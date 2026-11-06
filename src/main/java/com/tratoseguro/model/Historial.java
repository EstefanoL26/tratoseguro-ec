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

import java.time.LocalDateTime;

/**
 * MODELO: un cambio de estado del trato (el expediente).
 * Si usuario es null, el cambio lo hizo el sistema (por ejemplo, un plazo vencido).
 */
@Entity
@Table(name = "historial")
public class Historial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_transaccion", nullable = false)
    private Transaccion transaccion;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_ant", length = 30)
    private EstadoTransaccion estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private EstadoTransaccion estadoNuevo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    private String motivo;

    public Historial() {
    }

    public Historial(Transaccion transaccion, Usuario usuario, EstadoTransaccion estadoAnterior,
                     EstadoTransaccion estadoNuevo, LocalDateTime fecha, String motivo) {
        this.transaccion = transaccion;
        this.usuario = usuario;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.fecha = fecha;
        this.motivo = motivo;
    }

    // Quién hizo el cambio, para mostrarlo en el expediente
    public String describirAutor() {
        return usuario == null ? "Sistema" : usuario.getNombre();
    }

    public Long getId() {
        return id;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public EstadoTransaccion getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoTransaccion getEstadoNuevo() {
        return estadoNuevo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getMotivo() {
        return motivo;
    }
}
