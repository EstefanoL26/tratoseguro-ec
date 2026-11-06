package com.tratoseguro.service;

import com.tratoseguro.model.Liquidacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los tres casos de la tabla del documento de análisis (monto retenido M = 200,00).
 */
class LiquidacionServiceTest {

    private final LiquidacionService servicio = new LiquidacionService(null);
    private final BigDecimal monto = new BigDecimal("200.00");

    @Test
    void liberacion() {
        Liquidacion l = servicio.calcular(monto, BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.00"), l.getReembolso());
        assertEquals(new BigDecimal("6.00"), l.getComision());
        assertEquals(new BigDecimal("194.00"), l.getNetoVendedor());
        assertTrue(l.isCuadra());
    }

    @Test
    void reembolsoParcial() {
        Liquidacion l = servicio.calcular(monto, new BigDecimal("50.00"));
        assertEquals(new BigDecimal("50.00"), l.getReembolso());
        assertEquals(new BigDecimal("4.50"), l.getComision());
        assertEquals(new BigDecimal("145.50"), l.getNetoVendedor());
        assertTrue(l.isCuadra());
    }

    @Test
    void reembolsoTotal() {
        Liquidacion l = servicio.calcular(monto, monto);
        assertEquals(new BigDecimal("200.00"), l.getReembolso());
        assertEquals(new BigDecimal("0.00"), l.getComision());
        assertEquals(new BigDecimal("0.00"), l.getNetoVendedor());
        assertTrue(l.isCuadra());
    }

    @Test
    void redondeoHalfUpSigueCuadrando() {
        // 3 % de 33,35 = 1,0005 -> 1,00; neto 32,35; 0 + 1,00 + 32,35 = 33,35
        Liquidacion l = servicio.calcular(new BigDecimal("33.35"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("1.00"), l.getComision());
        assertTrue(l.isCuadra());
    }

    @Test
    void rechazaReembolsoMayorAlMontoONegativo() {
        assertThrows(ReglaNegocioException.class, () -> servicio.calcular(monto, new BigDecimal("200.01")));
        assertThrows(ReglaNegocioException.class, () -> servicio.calcular(monto, new BigDecimal("-1.00")));
    }
}
