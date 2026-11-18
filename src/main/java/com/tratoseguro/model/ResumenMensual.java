package com.tratoseguro.model;

import java.math.BigDecimal;

/**
 * Totales de un mes para los gráficos del panel de finanzas (no es una tabla).
 *
 * @param etiqueta  mes legible, por ejemplo "nov 2026"
 */
public record ResumenMensual(String etiqueta, BigDecimal retenido, BigDecimal liberado, BigDecimal reembolsado,
                             BigDecimal comisiones) {
}
