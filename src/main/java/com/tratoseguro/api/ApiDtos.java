package com.tratoseguro.api;

import com.tratoseguro.model.Historial;
import com.tratoseguro.model.Liquidacion;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.Reputacion;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Objetos que devuelve la API REST. Nunca se exponen las entidades directamente
 * (así no salen datos como la contraseña cifrada o la cédula completa).
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    @Schema(description = "Participante de un trato")
    public record PersonaDto(Long id, String nombre) {
        static PersonaDto de(Usuario u) {
            return u == null ? null : new PersonaDto(u.getId(), u.getNombre());
        }
    }

    @Schema(description = "Trato en un listado")
    public record TratoResumenDto(Long id, String producto, BigDecimal monto, String estado, PersonaDto vendedor,
                                  PersonaDto comprador, LocalDateTime fechaCreacion) {
        static TratoResumenDto de(Transaccion t) {
            return new TratoResumenDto(t.getId(), t.getProducto(), t.getMonto(), t.getEstado().name(),
                    PersonaDto.de(t.getVendedor()), PersonaDto.de(t.getComprador()), t.getFechaCreacion());
        }
    }

    @Schema(description = "Reparto del dinero retenido. Regla de cuadre: reembolso + comisión + neto = monto")
    public record LiquidacionDto(BigDecimal monto, BigDecimal reembolso, BigDecimal comision, BigDecimal netoVendedor,
                                 boolean cuadra) {
        static LiquidacionDto de(Liquidacion l) {
            return new LiquidacionDto(l.getMonto(), l.getReembolso(), l.getComision(), l.getNetoVendedor(), l.isCuadra());
        }

        static LiquidacionDto de(Transaccion t) {
            if (t.getComision() == null) {
                return null;
            }
            return de(new Liquidacion(t.getMonto(), t.getReembolso(), t.getComision(), t.getMontoVendedor()));
        }
    }

    @Schema(description = "Cambio de estado del trato")
    public record HistorialDto(LocalDateTime fecha, String estadoAnterior, String estadoNuevo, String autor, String motivo) {
        static HistorialDto de(Historial h) {
            return new HistorialDto(h.getFecha(), h.getEstadoAnterior() == null ? null : h.getEstadoAnterior().name(),
                    h.getEstadoNuevo().name(), h.describirAutor(), h.getMotivo());
        }
    }

    @Schema(description = "Movimiento de dinero (RETENCION, LIBERACION, COMISION o REEMBOLSO)")
    public record MovimientoDto(String tipo, BigDecimal monto, LocalDateTime fecha, String referencia) {
        static MovimientoDto de(Movimiento m) {
            return new MovimientoDto(m.getTipo().name(), m.getMonto(), m.getFecha(), m.getReferencia());
        }
    }

    @Schema(description = "Detalle completo de un trato")
    public record TratoDetalleDto(Long id, String producto, String descripcion, BigDecimal monto, String estado,
                                  PersonaDto vendedor, PersonaDto comprador, Integer diasEntrega, String metodoPago,
                                  LocalDateTime fechaCreacion, LocalDateTime fechaPago, LocalDate fechaLimiteEnvio,
                                  LocalDate fechaLimiteRevision, LiquidacionDto liquidacion, String codigoVerificacion,
                                  List<MovimientoDto> movimientos, List<HistorialDto> historial) {
    }

    @Schema(description = "Reputación de un vendedor")
    public record ReputacionDto(PersonaDto vendedor, BigDecimal promedio, long calificaciones, long tratosCerrados,
                                long tratosConDisputa, Integer porcentajeSinDisputa) {
        static ReputacionDto de(Usuario vendedor, Reputacion r) {
            return new ReputacionDto(PersonaDto.de(vendedor), r.promedio(), r.calificaciones(), r.tratosCerrados(),
                    r.tratosConDisputa(), r.getPorcentajeSinDisputa());
        }
    }

    @Schema(description = "Error de la API")
    public record ErrorDto(int estado, String error, String mensaje, LocalDateTime fecha) {
    }
}
