package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo de provincias del Ecuador. El código es el mismo que usan los dos
 * primeros dígitos de la cédula (01 Azuay ... 17 Pichincha ... 24 Santa Elena).
 */
@Entity
@Table(name = "provincia")
public class Provincia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    public Provincia() {
    }

    public Provincia(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }
}
