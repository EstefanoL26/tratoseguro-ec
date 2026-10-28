package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODELO: un trato entre un vendedor y un comprador. El dinero siempre va en BigDecimal.
 */
@Entity
@Table(name = "transaccion")
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El producto es obligatorio")
    @Size(max = 150, message = "Máximo 150 caracteres")
    @Column(nullable = false, length = 150)
    private String producto;

    @Size(max = 255, message = "Máximo 255 caracteres")
    private String descripcion;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto mínimo es $ 1,00")
    @DecimalMax(value = "10000.00", message = "El monto máximo es $ 10.000,00")
    @Digits(integer = 5, fraction = 2, message = "Máximo 2 decimales")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @NotBlank(message = "El vendedor es obligatorio")
    @Size(max = 100, message = "Máximo 100 caracteres")
    @Column(nullable = false, length = 100)
    private String vendedor;

    @NotBlank(message = "El correo del comprador es obligatorio")
    @Email(message = "Escribe un correo válido")
    @Column(name = "email_comprador", nullable = false, length = 100)
    private String emailComprador;

    @NotNull(message = "Los días de entrega son obligatorios")
    @Min(value = 1, message = "Mínimo 1 día")
    @Max(value = 30, message = "Máximo 30 días")
    @Column(name = "dias_entrega", nullable = false)
    private Integer diasEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTransaccion estado;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    public Transaccion() {
    }

    // Solo se puede editar o eliminar mientras el trato no se ha pagado
    public boolean isEditable() {
        return estado == EstadoTransaccion.CREADA;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getVendedor() {
        return vendedor;
    }

    public void setVendedor(String vendedor) {
        this.vendedor = vendedor;
    }

    public String getEmailComprador() {
        return emailComprador;
    }

    public void setEmailComprador(String emailComprador) {
        this.emailComprador = emailComprador;
    }

    public Integer getDiasEntrega() {
        return diasEntrega;
    }

    public void setDiasEntrega(Integer diasEntrega) {
        this.diasEntrega = diasEntrega;
    }

    public EstadoTransaccion getEstado() {
        return estado;
    }

    public void setEstado(EstadoTransaccion estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
