package com.tratoseguro.model;

import java.math.BigDecimal;

/**
 * Resultado de liquidar el dinero retenido de un trato (no es una tabla).
 * Regla de cuadre: reembolso + comisión + neto al vendedor = monto retenido.
 */
public class Liquidacion {

    private final BigDecimal monto;
    private final BigDecimal reembolso;
    private final BigDecimal comision;
    private final BigDecimal netoVendedor;

    public Liquidacion(BigDecimal monto, BigDecimal reembolso, BigDecimal comision, BigDecimal netoVendedor) {
        this.monto = monto;
        this.reembolso = reembolso;
        this.comision = comision;
        this.netoVendedor = netoVendedor;
    }

    public BigDecimal getSuma() {
        return reembolso.add(comision).add(netoVendedor);
    }

    public boolean isCuadra() {
        return getSuma().compareTo(monto) == 0;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public BigDecimal getReembolso() {
        return reembolso;
    }

    public BigDecimal getComision() {
        return comision;
    }

    public BigDecimal getNetoVendedor() {
        return netoVendedor;
    }
}
