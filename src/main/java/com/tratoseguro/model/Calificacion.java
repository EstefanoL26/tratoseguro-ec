package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * MODELO: calificación que el comprador le da al vendedor al cerrar un trato (TRANSACCION 1 : 0..1 CALIFICACION).
 * Con ellas se calcula la reputación del vendedor, que el comprador ve antes de pagar.
 */
@Entity
@Table(name = "calificacion")
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_transaccion", nullable = false, unique = true)
    private Transaccion transaccion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_comprador", nullable = false)
    private Usuario comprador;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_vendedor", nullable = false)
    private Usuario vendedor;

    // De 1 a 5 estrellas
    @Column(nullable = false)
    private Integer puntaje;

    @Column(length = 300)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Calificacion() {
    }

    public Calificacion(Transaccion transaccion, Integer puntaje, String comentario, LocalDateTime fecha) {
        this.transaccion = transaccion;
        this.comprador = transaccion.getComprador();
        this.vendedor = transaccion.getVendedor();
        this.puntaje = puntaje;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public Usuario getComprador() {
        return comprador;
    }

    public Usuario getVendedor() {
        return vendedor;
    }

    public Integer getPuntaje() {
        return puntaje;
    }

    public String getComentario() {
        return comentario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
