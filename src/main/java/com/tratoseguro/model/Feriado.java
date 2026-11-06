package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * MODELO: feriado nacional. Los plazos del core se cuentan en días hábiles y lo saltan.
 * El administrador los mantiene al día (los traslados los decreta el Gobierno cada año).
 */
@Entity
@Table(name = "feriado")
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "La fecha es obligatoria")
    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "Máximo 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nombre;

    public Feriado() {
    }

    public Feriado(LocalDate fecha, String nombre) {
        this.fecha = fecha;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
