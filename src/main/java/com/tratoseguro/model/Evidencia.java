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
 * MODELO: evidencia que aporta el comprador o el vendedor en una disputa (DISPUTA 1 : N EVIDENCIA).
 * El archivo es un enlace (por ejemplo a una foto en Google Drive): el servidor de Render
 * no guarda archivos de forma permanente.
 */
@Entity
@Table(name = "evidencia")
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_disputa", nullable = false)
    private Disputa disputa;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String descripcion;

    private String archivo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Evidencia() {
    }

    public Evidencia(Disputa disputa, Usuario usuario, String descripcion, String archivo, LocalDateTime fecha) {
        this.disputa = disputa;
        this.usuario = usuario;
        this.descripcion = descripcion;
        this.archivo = archivo;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Disputa getDisputa() {
        return disputa;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getArchivo() {
        return archivo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
