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
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MODELO: un trato entre un vendedor y un comprador. Es el centro del core:
 * su estado, sus plazos y el resultado de la liquidación del dinero retenido.
 * El dinero siempre va en BigDecimal.
 */
@Entity
@Table(name = "transaccion")
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El vendedor lo pone el servidor (es el usuario que crea el trato)
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_vendedor", nullable = false)
    private Usuario vendedor;

    // El comprador se escoge de un dropdown con los usuarios de rol COMPRADOR (el servicio lo valida)
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_comprador", nullable = false)
    private Usuario comprador;

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

    @NotNull(message = "Los días de entrega son obligatorios")
    @Min(value = 1, message = "Mínimo 1 día")
    @Max(value = 30, message = "Máximo 30 días")
    @Column(name = "dias_entrega", nullable = false)
    private Integer diasEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTransaccion estado;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "fecha_limite_envio")
    private LocalDate fechaLimiteEnvio;

    @Column(name = "fecha_limite_revision")
    private LocalDate fechaLimiteRevision;

    // Resultado de la liquidación: reembolso + comisión + neto al vendedor = monto
    @Column(precision = 10, scale = 2)
    private BigDecimal reembolso;

    @Column(precision = 10, scale = 2)
    private BigDecimal comision;

    @Column(name = "monto_vendedor", precision = 10, scale = 2)
    private BigDecimal montoVendedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20)
    private MetodoPago metodoPago;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent")
    private String stripePaymentIntent;

    // Bloqueo optimista: si dos procesos intentan cambiar el mismo trato a la vez,
    // el segundo falla en lugar de liquidar el dinero dos veces
    @Version
    @Column(nullable = false)
    private Long version;

    public Transaccion() {
    }

    // Solo se puede editar o eliminar mientras el trato no se ha pagado
    public boolean isEditable() {
        return estado == EstadoTransaccion.CREADA;
    }

    public boolean participa(Usuario usuario) {
        return usuario != null && (usuario.getId().equals(vendedor.getId()) || usuario.getId().equals(comprador.getId()));
    }

    public boolean esVendedor(Usuario usuario) {
        return usuario != null && usuario.getId().equals(vendedor.getId());
    }

    public boolean esComprador(Usuario usuario) {
        return usuario != null && usuario.getId().equals(comprador.getId());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getVendedor() {
        return vendedor;
    }

    public void setVendedor(Usuario vendedor) {
        this.vendedor = vendedor;
    }

    public Usuario getComprador() {
        return comprador;
    }

    public void setComprador(Usuario comprador) {
        this.comprador = comprador;
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

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDateTime fechaPago) {
        this.fechaPago = fechaPago;
    }

    public LocalDate getFechaLimiteEnvio() {
        return fechaLimiteEnvio;
    }

    public void setFechaLimiteEnvio(LocalDate fechaLimiteEnvio) {
        this.fechaLimiteEnvio = fechaLimiteEnvio;
    }

    public LocalDate getFechaLimiteRevision() {
        return fechaLimiteRevision;
    }

    public void setFechaLimiteRevision(LocalDate fechaLimiteRevision) {
        this.fechaLimiteRevision = fechaLimiteRevision;
    }

    public BigDecimal getReembolso() {
        return reembolso;
    }

    public void setReembolso(BigDecimal reembolso) {
        this.reembolso = reembolso;
    }

    public BigDecimal getComision() {
        return comision;
    }

    public void setComision(BigDecimal comision) {
        this.comision = comision;
    }

    public BigDecimal getMontoVendedor() {
        return montoVendedor;
    }

    public void setMontoVendedor(BigDecimal montoVendedor) {
        this.montoVendedor = montoVendedor;
    }

    public MetodoPago getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPago metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getStripePaymentIntent() {
        return stripePaymentIntent;
    }

    public void setStripePaymentIntent(String stripePaymentIntent) {
        this.stripePaymentIntent = stripePaymentIntent;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
