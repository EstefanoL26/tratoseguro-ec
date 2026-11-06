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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODELO: disputa abierta por el comprador (TRANSACCION 1 : 0..1 DISPUTA).
 * El mediador la resuelve con una decisión y, si corresponde, un monto a reembolsar.
 */
@Entity
@Table(name = "disputa")
public class Disputa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_transaccion", nullable = false, unique = true)
    private Transaccion transaccion;

    @ManyToOne
    @JoinColumn(name = "id_mediador")
    private Usuario mediador;

    @Column(nullable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Decision decision;

    @Column(precision = 10, scale = 2)
    private BigDecimal reembolso;

    // Vía rápida: el mediador confirma un fraude y se reembolsa todo de inmediato
    @Column(name = "via_rapida", nullable = false)
    private boolean viaRapida;

    @Column(length = 500)
    private String justificacion;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    public Disputa() {
    }

    public Disputa(Transaccion transaccion, String motivo, LocalDateTime fechaApertura) {
        this.transaccion = transaccion;
        this.motivo = motivo;
        this.fechaApertura = fechaApertura;
    }

    public boolean isResuelta() {
        return decision != null;
    }

    public Long getId() {
        return id;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public Usuario getMediador() {
        return mediador;
    }

    public void setMediador(Usuario mediador) {
        this.mediador = mediador;
    }

    public String getMotivo() {
        return motivo;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public BigDecimal getReembolso() {
        return reembolso;
    }

    public void setReembolso(BigDecimal reembolso) {
        this.reembolso = reembolso;
    }

    public boolean isViaRapida() {
        return viaRapida;
    }

    public void setViaRapida(boolean viaRapida) {
        this.viaRapida = viaRapida;
    }

    public String getJustificacion() {
        return justificacion;
    }

    public void setJustificacion(String justificacion) {
        this.justificacion = justificacion;
    }

    public LocalDateTime getFechaApertura() {
        return fechaApertura;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }
}
