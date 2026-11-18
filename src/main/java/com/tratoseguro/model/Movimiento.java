package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODELO: un movimiento de dinero del trato (retención, liberación, comisión o reembolso).
 */
@Entity
@Table(name = "movimiento")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_transaccion", nullable = false)
    private Transaccion transaccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDateTime fecha;

    // Identificador del pago, reembolso o transferencia en Stripe, "SIMULADO",
    // o "PENDIENTE" si la transferencia al vendedor todavía no se pudo hacer
    private String referencia;

    public static final String REFERENCIA_PENDIENTE = "PENDIENTE";

    public Movimiento() {
    }

    public Movimiento(Transaccion transaccion, TipoMovimiento tipo, BigDecimal monto, LocalDateTime fecha, String referencia) {
        this.transaccion = transaccion;
        this.tipo = tipo;
        this.monto = monto;
        this.fecha = fecha;
        this.referencia = referencia;
    }

    public boolean esReembolso() {
        return tipo == TipoMovimiento.REEMBOLSO;
    }

    public Long getId() {
        return id;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getReferencia() {
        return referencia;
    }

    // Solo se usa para completar una transferencia que quedó pendiente
    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public boolean isTransferenciaPendiente() {
        return tipo == TipoMovimiento.LIBERACION && REFERENCIA_PENDIENTE.equals(referencia);
    }
}
