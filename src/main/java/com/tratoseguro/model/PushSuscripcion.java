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
 * MODELO: un navegador o celular que aceptó recibir notificaciones push de un usuario
 * (USUARIO 1 : N PUSH_SUSCRIPCION). Las claves sirven para cifrar el aviso para ese dispositivo.
 */
@Entity
@Table(name = "push_suscripcion")
public class PushSuscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 500)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public PushSuscripcion() {
    }

    public PushSuscripcion(Usuario usuario, String endpoint, String p256dh, String auth, LocalDateTime fecha) {
        this.usuario = usuario;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
