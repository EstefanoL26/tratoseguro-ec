package com.tratoseguro.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reputación de un vendedor (no es una tabla): se calcula con sus calificaciones y sus tratos cerrados.
 *
 * @param promedio          promedio de estrellas (1 decimal) o null si nadie lo ha calificado
 * @param calificaciones    cuántas calificaciones tiene
 * @param tratosCerrados    tratos liberados o reembolsados
 * @param tratosConDisputa  tratos cerrados que pasaron por una disputa
 */
public record Reputacion(BigDecimal promedio, long calificaciones, long tratosCerrados, long tratosConDisputa) {

    public static Reputacion de(Double promedio, long calificaciones, long tratosCerrados, long tratosConDisputa) {
        BigDecimal redondeado = promedio == null ? null : BigDecimal.valueOf(promedio).setScale(1, RoundingMode.HALF_UP);
        return new Reputacion(redondeado, calificaciones, tratosCerrados, tratosConDisputa);
    }

    public boolean isSinCalificaciones() {
        return calificaciones == 0;
    }

    // Porcentaje de tratos cerrados sin disputa (null si todavía no cerró ninguno)
    public Integer getPorcentajeSinDisputa() {
        if (tratosCerrados == 0) {
            return null;
        }
        return (int) Math.round(100.0 * (tratosCerrados - tratosConDisputa) / tratosCerrados);
    }

    // Estrellas llenas para mostrar (redondeo al entero más cercano)
    public int getEstrellasLlenas() {
        return promedio == null ? 0 : promedio.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
