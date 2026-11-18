package com.tratoseguro.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.TransferCollection;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferListParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Pasarela de pagos. Retener dinero de terceros es una actividad regulada en Ecuador,
 * así que TODO es de prueba:
 *  - Con STRIPE_SECRET_KEY (clave sk_test_...) el comprador paga en Stripe Checkout con tarjetas de prueba.
 *  - Sin clave, la plataforma registra un pago simulado.
 *  - Stripe Connect: al liberar, el neto se transfiere a la cuenta conectada del vendedor
 *    (la decisión de cuándo transferir está en CobroVendedorService).
 * Las claves reales (sk_live_...) se rechazan a propósito.
 */
@Service
public class PagoService {

    public static final String REFERENCIA_SIMULADA = "SIMULADO";

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final String claveSecreta;
    private final String secretoWebhook;

    public PagoService(@Value("${stripe.secret-key:}") String claveSecreta,
                       @Value("${stripe.webhook-secret:}") String secretoWebhook) {
        this.claveSecreta = claveSecreta == null ? "" : claveSecreta.trim();
        this.secretoWebhook = secretoWebhook == null ? "" : secretoWebhook.trim();
        if (this.claveSecreta.startsWith("sk_live_")) {
            log.warn("Se configuró una clave REAL de Stripe. Se ignora: TratoSeguro solo trabaja en modo de prueba.");
        }
    }

    // Stripe solo se activa con una clave de PRUEBA
    public boolean isStripeActivo() {
        return claveSecreta.startsWith("sk_test_");
    }

    public boolean isWebhookConfigurado() {
        return isStripeActivo() && !secretoWebhook.isEmpty();
    }

    /**
     * Crea la sesión de Stripe Checkout y devuelve la URL a la que se redirige al comprador.
     * urlBase es la dirección de la app (por ejemplo https://tratoseguro-ec.onrender.com).
     */
    public Session crearSesionCheckout(Transaccion transaccion, String urlBase) {
        String urlDetalle = urlBase + "/transacciones/" + transaccion.getId();
        SessionCreateParams parametros = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                // Solo tarjeta: se cobra al instante. Otros métodos (banco) tardan días y el trato no quedaría pagado
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                .setClientReferenceId(transaccion.getId().toString())
                .putMetadata("transaccionId", transaccion.getId().toString())
                .setCustomerEmail(transaccion.getComprador().getEmail())
                .setSuccessUrl(urlDetalle + "/pago/exito?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(urlDetalle + "/pago?cancelado")
                // El grupo une el cobro al comprador con la transferencia al vendedor (Stripe Connect)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setTransferGroup(grupoTransferencia(transaccion))
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(enCentavos(transaccion.getMonto()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Trato #" + transaccion.getId() + ": " + transaccion.getProducto())
                                        .setDescription("Pago protegido: TratoSeguro EC retiene el dinero hasta que confirmes la recepción.")
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            return Session.create(parametros, opciones(null));
        } catch (StripeException e) {
            log.error("Stripe no pudo crear la sesión de pago del trato {}: {}", transaccion.getId(), e.getMessage());
            throw new ReglaNegocioException("No se pudo iniciar el pago con Stripe. Inténtalo de nuevo en unos minutos.");
        }
    }

    // Consulta a Stripe una sesión de Checkout (el servidor no confía en lo que diga el navegador)
    public Session obtenerSesion(String sessionId) {
        try {
            return Session.retrieve(sessionId, opciones(null));
        } catch (StripeException e) {
            log.error("Stripe no encontró la sesión {}: {}", sessionId, e.getMessage());
            throw new ReglaNegocioException("No se pudo verificar el pago con Stripe.");
        }
    }

    /**
     * Devuelve el dinero al comprador. Con Stripe crea un reembolso real en modo de prueba;
     * sin Stripe devuelve la referencia "SIMULADO".
     * La clave de idempotencia evita reembolsar dos veces el mismo trato si se reintenta.
     */
    public String reembolsar(Transaccion transaccion, BigDecimal monto) {
        if (transaccion.getStripePaymentIntent() == null || !isStripeActivo()) {
            return REFERENCIA_SIMULADA;
        }
        RefundCreateParams parametros = RefundCreateParams.builder()
                .setPaymentIntent(transaccion.getStripePaymentIntent())
                .setAmount(enCentavos(monto))
                .build();
        try {
            Refund reembolso = Refund.create(parametros, opciones("reembolso-trato-" + transaccion.getId()));
            return reembolso.getId();
        } catch (StripeException e) {
            log.error("Stripe no pudo reembolsar el trato {}: {}", transaccion.getId(), e.getMessage());
            throw new ReglaNegocioException("Stripe no pudo procesar el reembolso. No se cambió el estado del trato.");
        }
    }

    // =====================================================================
    // Stripe Connect: pago al vendedor ("separate charges and transfers")
    // El comprador le paga a la plataforma; al liberar, la plataforma transfiere
    // el neto (monto − reembolso − comisión) a la cuenta conectada del vendedor.
    // =====================================================================

    /**
     * Crea la cuenta conectada (tipo Express, modo de prueba) del vendedor y devuelve su id (acct_...).
     * Stripe Connect no opera en Ecuador, así que la cuenta de prueba se crea en EE. UU.:
     * en el registro de Stripe se usan los datos de prueba que el propio formulario ofrece.
     */
    public String crearCuentaConectada(Usuario vendedor) {
        AccountCreateParams parametros = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry("US")
                .setEmail(vendedor.getEmail())
                .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                .setCapabilities(AccountCreateParams.Capabilities.builder()
                        .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                        .build())
                .putMetadata("usuarioId", vendedor.getId().toString())
                .build();
        try {
            return Account.create(parametros, opciones("cuenta-vendedor-" + vendedor.getId())).getId();
        } catch (StripeException e) {
            log.error("Stripe no pudo crear la cuenta conectada del usuario {}: {}", vendedor.getId(), e.getMessage());
            throw new ReglaNegocioException("No se pudo crear tu cuenta de cobro en Stripe. Inténtalo de nuevo en unos minutos.");
        }
    }

    // Enlace de un solo uso al registro de Stripe (onboarding). Caduca a los pocos minutos.
    public String crearEnlaceRegistro(String cuentaId, String urlBase) {
        AccountLinkCreateParams parametros = AccountLinkCreateParams.builder()
                .setAccount(cuentaId)
                .setRefreshUrl(urlBase + "/cuenta/cobros/reintentar")
                .setReturnUrl(urlBase + "/cuenta/cobros/retorno")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();
        try {
            return AccountLink.create(parametros, opciones(null)).getUrl();
        } catch (StripeException e) {
            log.error("Stripe no pudo crear el enlace de registro de {}: {}", cuentaId, e.getMessage());
            throw new ReglaNegocioException("No se pudo abrir el registro de Stripe. Inténtalo de nuevo.");
        }
    }

    // La cuenta puede recibir transferencias cuando Stripe activa la capacidad "transfers"
    public boolean cuentaPuedeRecibir(String cuentaId) {
        try {
            Account cuenta = Account.retrieve(cuentaId, opciones(null));
            return cuenta.getCapabilities() != null && "active".equals(cuenta.getCapabilities().getTransfers());
        } catch (StripeException e) {
            log.error("Stripe no encontró la cuenta {}: {}", cuentaId, e.getMessage());
            throw new ReglaNegocioException("No se pudo consultar tu cuenta de cobro en Stripe.");
        }
    }

    /**
     * Transfiere el neto del trato a la cuenta conectada del vendedor y devuelve el id de la transferencia (tr_...).
     * Se liga al cobro original (source_transaction): así no hace falta saldo disponible en la plataforma.
     * Antes de crearla busca si ya existe una del mismo trato, para no pagar dos veces si se reintenta.
     * Lanza StripeException si Stripe la rechaza (quien llama decide si queda pendiente).
     */
    public String transferirAVendedor(Transaccion transaccion, BigDecimal neto, String cuentaId) throws StripeException {
        String grupo = grupoTransferencia(transaccion);
        TransferCollection existentes = Transfer.list(TransferListParams.builder()
                .setTransferGroup(grupo).setDestination(cuentaId).setLimit(1L).build(), opciones(null));
        if (existentes.getData() != null && !existentes.getData().isEmpty()) {
            return existentes.getData().get(0).getId();
        }

        String cobro = PaymentIntent.retrieve(transaccion.getStripePaymentIntent(), opciones(null)).getLatestCharge();
        TransferCreateParams parametros = TransferCreateParams.builder()
                .setAmount(enCentavos(neto))
                .setCurrency("usd")
                .setDestination(cuentaId)
                .setSourceTransaction(cobro)
                .setTransferGroup(grupo)
                .setDescription("Trato #" + transaccion.getId() + ": " + transaccion.getProducto())
                .build();
        // La clave cambia cada minuto: un reintento después de un error no recibe el mismo error guardado
        String clave = "liberacion-trato-" + transaccion.getId() + "-" + (System.currentTimeMillis() / 60000);
        return Transfer.create(parametros, opciones(clave)).getId();
    }

    private String grupoTransferencia(Transaccion transaccion) {
        return "TRATO-" + transaccion.getId();
    }

    // Verifica la firma del webhook para asegurar que el aviso viene de Stripe
    public Event leerEventoWebhook(String cuerpo, String firma) {
        try {
            return Webhook.constructEvent(cuerpo, firma, secretoWebhook);
        } catch (SignatureVerificationException e) {
            throw new ReglaNegocioException("Firma del webhook inválida.");
        }
    }

    private RequestOptions opciones(String claveIdempotencia) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder().setApiKey(claveSecreta);
        if (claveIdempotencia != null) {
            builder.setIdempotencyKey(claveIdempotencia);
        }
        return builder.build();
    }

    // Stripe trabaja en centavos: $ 200,00 -> 20000
    private long enCentavos(BigDecimal monto) {
        return monto.movePointRight(2).longValueExact();
    }
}
