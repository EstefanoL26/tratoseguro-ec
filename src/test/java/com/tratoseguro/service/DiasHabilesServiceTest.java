package com.tratoseguro.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ejemplo del documento de análisis (los feriados vienen de DatosIniciales).
 */
@SpringBootTest
class DiasHabilesServiceTest {

    @Autowired
    private DiasHabilesService diasHabilesService;

    @Test
    void limiteDeEnvio() {
        // Pago el jueves 01/10/2026 con 5 días de entrega -> 08/10/2026
        assertEquals(LocalDate.of(2026, 10, 8), diasHabilesService.sumarDiasHabiles(LocalDate.of(2026, 10, 1), 5));
    }

    @Test
    void limiteDeRevisionSaltaElFeriadoDel9DeOctubre() {
        // Envío el 05/10/2026 con 5 + 3 días -> 16/10/2026
        assertEquals(LocalDate.of(2026, 10, 16), diasHabilesService.sumarDiasHabiles(LocalDate.of(2026, 10, 5), 8));
    }

    @Test
    void finesDeSemanaYFeriadosNoSonHabiles() {
        assertFalse(diasHabilesService.esHabil(LocalDate.of(2026, 10, 9)));   // feriado
        assertFalse(diasHabilesService.esHabil(LocalDate.of(2026, 10, 10)));  // sábado
        assertFalse(diasHabilesService.esHabil(LocalDate.of(2026, 10, 11)));  // domingo
    }
}
