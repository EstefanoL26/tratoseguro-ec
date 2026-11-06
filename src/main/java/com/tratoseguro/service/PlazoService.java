package com.tratoseguro.service;

import com.tratoseguro.util.Reloj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Aplica los plazos vencidos una vez al día (6:00 a.m. hora de Ecuador).
 * Cada trato se procesa en su propia transacción: si uno falla (por ejemplo, Stripe no responde
 * o el comprador confirmó justo en ese momento), los demás siguen.
 * Finanzas también puede ejecutarlo a mano desde su panel.
 */
@Service
public class PlazoService {

    private static final Logger log = LoggerFactory.getLogger(PlazoService.class);

    private final TransaccionService transaccionService;

    public PlazoService(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Guayaquil")
    public void revisarAutomaticamente() {
        int cambios = revisar(Reloj.hoy());
        log.info("Revisión diaria de plazos: {} trato(s) actualizados", cambios);
    }

    // Devuelve cuántos tratos cambiaron de estado
    public int revisar(LocalDate hoy) {
        int cambios = 0;
        for (Long id : transaccionService.idsConPlazoVencido(hoy)) {
            try {
                if (transaccionService.aplicarPlazo(id, hoy)) {
                    cambios++;
                }
            } catch (RuntimeException e) {
                // Incluye el bloqueo optimista: si otro proceso cambió el trato, se reintenta mañana
                log.warn("No se pudo aplicar el plazo del trato {}: {}", id, e.getMessage());
            }
        }
        return cambios;
    }
}
