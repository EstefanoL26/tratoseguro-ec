package com.tratoseguro.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.tratoseguro.model.Transaccion;
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
                .setClientReferenceId(transaccion.getId().toString())
                .putMetadata("transaccionId", transaccion.getId().toString())
                .setCustomerEmail(transaccion.getComprador().getEmail())
                .setSuccessUrl(urlDetalle + "/pago/exito?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(urlDetalle + "/pago?cancelado")
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
