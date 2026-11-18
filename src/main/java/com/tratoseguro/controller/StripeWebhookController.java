package com.tratoseguro.controller;

import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.tratoseguro.service.CobroVendedorService;
import com.tratoseguro.service.PagoService;
import com.tratoseguro.service.ReglaNegocioException;
import com.tratoseguro.service.TransaccionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de Stripe: Stripe avisa aquí cuando un pago de Checkout se completó,
 * aunque el comprador cierre el navegador antes de volver a la app.
 * Es público (lo llama Stripe, no un usuario), pero solo se acepta si la firma es válida.
 */
@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PagoService pagoService;
    private final TransaccionService transaccionService;
    private final CobroVendedorService cobroVendedorService;

    public StripeWebhookController(PagoService pagoService, TransaccionService transaccionService,
                                   CobroVendedorService cobroVendedorService) {
        this.pagoService = pagoService;
        this.transaccionService = transaccionService;
        this.cobroVendedorService = cobroVendedorService;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> recibir(@RequestBody String cuerpo,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String firma) {
        if (!pagoService.isWebhookConfigurado() || firma == null) {
            return ResponseEntity.badRequest().body("Webhook no configurado");
        }
        try {
            Event evento = pagoService.leerEventoWebhook(cuerpo, firma);
            StripeObject objeto = evento.getDataObjectDeserializer().getObject().orElse(null);
            if ("checkout.session.completed".equals(evento.getType()) && objeto instanceof Session sesion) {
                transaccionService.confirmarPagoStripe(sesion.getId());
            }
            // Stripe Connect: el vendedor terminó su registro o cambió el estado de su cuenta
            if ("account.updated".equals(evento.getType()) && objeto instanceof Account cuenta) {
                cobroVendedorService.cuentaActualizada(cuenta.getId());
            }
            return ResponseEntity.ok("ok");
        } catch (ReglaNegocioException e) {
            log.warn("Webhook de Stripe rechazado: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
