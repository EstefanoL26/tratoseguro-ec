package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * MODELO: aviso interno para un usuario (no se envían correos ni SMS reales).
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "id_transaccion")
    private Transaccion transaccion;

    @Column(nullable = false)
    private String mensaje;

    @Column(nullable = false)
    private boolean leida;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Notificacion() {
    }

    public Notificacion(Usuario usuario, Transaccion transaccion, String mensaje, LocalDateTime fecha) {
        this.usuario = usuario;
        this.transaccion = transaccion;
        this.mensaje = mensaje;
        this.fecha = fecha;
    }

    public void marcarLeida() {
        this.leida = true;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public boolean isLeida() {
        return leida;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
