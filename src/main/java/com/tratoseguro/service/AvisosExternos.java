package com.tratoseguro.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda por correo y push cada notificación interna, en segundo plano y solo después de que
 * la base confirmó el cambio. Así el usuario no espera al servicio externo y, si el trato
 * no se guarda, no sale ningún aviso.
 */
@Component
public class AvisosExternos {

    private final CorreoService correoService;
    private final AvisoPushService avisoPushService;

    // Los datos de ejemplo que se cargan al arrancar no deben mandar correos ni push
    private volatile boolean listo = false;

    public AvisosExternos(CorreoService correoService, AvisoPushService avisoPushService) {
        this.correoService = correoService;
        this.avisoPushService = avisoPushService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        listo = true;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void enviar(NotificacionCreada aviso) {
        if (!listo) {
            return;
        }
        correoService.enviarAviso(aviso);
        avisoPushService.enviarA(aviso);
    }
}
