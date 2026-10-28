package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Catálogo de ciudades. Cada ciudad pertenece a una provincia (PROVINCIA 1:N CIUDAD).
 */
@Entity
@Table(name = "ciudad")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_provincia", nullable = false)
    private Provincia provincia;

    public Ciudad() {
    }

    public Ciudad(String nombre, Provincia provincia) {
        this.nombre = nombre;
        this.provincia = provincia;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Provincia getProvincia() {
        return provincia;
    }
}
