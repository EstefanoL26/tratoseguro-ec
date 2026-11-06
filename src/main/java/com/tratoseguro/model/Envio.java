package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * MODELO: el envío que registra el vendedor (TRANSACCION 1 : 0..1 ENVIO).
 */
@Entity
@Table(name = "envio")
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_transaccion", nullable = false, unique = true)
    private Transaccion transaccion;

    @Column(nullable = false, length = 100)
    private String transportista;

    @Column(nullable = false, length = 100)
    private String guia;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Envio() {
    }

    public Envio(Transaccion transaccion, String transportista, String guia, LocalDateTime fecha) {
        this.transaccion = transaccion;
        this.transportista = transportista;
        this.guia = guia;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public String getTransportista() {
        return transportista;
    }

    public String getGuia() {
        return guia;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
