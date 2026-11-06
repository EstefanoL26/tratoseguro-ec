package com.tratoseguro.controller;

import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
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

    public StripeWebhookController(PagoService pagoService, TransaccionService transaccionService) {
        this.pagoService = pagoService;
        this.transaccionService = transaccionService;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> recibir(@RequestBody String cuerpo,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String firma) {
        if (!pagoService.isWebhookConfigurado() || firma == null) {
            return ResponseEntity.badRequest().body("Webhook no configurado");
        }
        try {
            Event evento = pagoService.leerEventoWebhook(cuerpo, firma);
            if ("checkout.session.completed".equals(evento.getType())) {
                StripeObject objeto = evento.getDataObjectDeserializer().getObject().orElse(null);
                if (objeto instanceof Session sesion) {
                    transaccionService.confirmarPagoStripe(sesion.getId());
                }
            }
            return ResponseEntity.ok("ok");
        } catch (ReglaNegocioException e) {
            log.warn("Webhook de Stripe rechazado: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
