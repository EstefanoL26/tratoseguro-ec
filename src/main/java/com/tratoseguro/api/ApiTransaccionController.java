package com.tratoseguro.api;

import com.tratoseguro.api.ApiDtos.HistorialDto;
import com.tratoseguro.api.ApiDtos.LiquidacionDto;
import com.tratoseguro.api.ApiDtos.MovimientoDto;
import com.tratoseguro.api.ApiDtos.PersonaDto;
import com.tratoseguro.api.ApiDtos.ReputacionDto;
import com.tratoseguro.api.ApiDtos.TratoDetalleDto;
import com.tratoseguro.api.ApiDtos.TratoResumenDto;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.CalificacionService;
import com.tratoseguro.service.ComprobanteService;
import com.tratoseguro.service.LiquidacionService;
import com.tratoseguro.service.TransaccionService;
import com.tratoseguro.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * API REST v1 (JSON). Usa los mismos servicios que las pantallas, así que aplica las mismas reglas:
 * cada usuario solo ve sus tratos y solo puede hacer lo que su rol y el estado del trato permiten.
 * Autenticación: HTTP Basic (usuario y contraseña de la aplicación). Documentación en /swagger-ui.html
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tratos", description = "Consulta de tratos, liquidaciones y reputación")
@SecurityRequirement(name = "basic")
public class ApiTransaccionController {

    private final TransaccionService transaccionService;
    private final LiquidacionService liquidacionService;
    private final CalificacionService calificacionService;
    private final UsuarioService usuarioService;

    public ApiTransaccionController(TransaccionService transaccionService, LiquidacionService liquidacionService,
                                    CalificacionService calificacionService, UsuarioService usuarioService) {
        this.transaccionService = transaccionService;
        this.liquidacionService = liquidacionService;
        this.calificacionService = calificacionService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/transacciones")
    @Operation(summary = "Tratos del usuario", description = "Comprador y vendedor ven los suyos; el mediador, los que tienen disputa; finanzas y admin, todos.")
    public List<TratoResumenDto> listar(@Parameter(description = "Texto a buscar en el producto") @RequestParam(required = false) String buscar,
                                        @Parameter(description = "Filtrar por estado") @RequestParam(required = false) EstadoTransaccion estado,
                                        Authentication authentication) {
        return transaccionService.listarPara(usuarioActual(authentication), buscar, estado).stream()
                .map(TratoResumenDto::de).toList();
    }

    @GetMapping("/transacciones/{id}")
    @Operation(summary = "Detalle de un trato", description = "Incluye plazos, liquidación, código de verificación, movimientos e historial. 403 si no participas.")
    public TratoDetalleDto detalle(@PathVariable Long id, Authentication authentication) {
        Transaccion t = transaccionService.obtenerParaVer(id, usuarioActual(authentication));
        return new TratoDetalleDto(t.getId(), t.getProducto(), t.getDescripcion(), t.getMonto(), t.getEstado().name(),
                PersonaDto.de(t.getVendedor()), PersonaDto.de(t.getComprador()), t.getDiasEntrega(),
                t.getMetodoPago() == null ? null : t.getMetodoPago().name(), t.getFechaCreacion(), t.getFechaPago(),
                t.getFechaLimiteEnvio(), t.getFechaLimiteRevision(), LiquidacionDto.de(t),
                ComprobanteService.codigoVerificacion(t),
                liquidacionService.movimientos(id).stream().map(MovimientoDto::de).toList(),
                transaccionService.historial(id).stream().map(HistorialDto::de).toList());
    }

    @PostMapping("/transacciones/{id}/confirmar-recepcion")
    @Operation(summary = "Confirmar la recepción (comprador)", description = "ENVIADA → LIBERADA: liquida el pago al vendedor menos el 3 % de comisión.")
    public TratoDetalleDto confirmarRecepcion(@PathVariable Long id, Authentication authentication) {
        transaccionService.confirmarRecepcion(id, usuarioActual(authentication));
        return detalle(id, authentication);
    }

    @GetMapping("/vendedores/{id}/reputacion")
    @Operation(summary = "Reputación de un vendedor", description = "Promedio de estrellas, tratos cerrados y porcentaje sin disputa.")
    public ReputacionDto reputacion(@PathVariable Long id) {
        Usuario vendedor = calificacionService.buscarVendedor(id);
        return ReputacionDto.de(vendedor, calificacionService.reputacion(vendedor));
    }

    @GetMapping("/liquidacion/simular")
    @Operation(summary = "Simular una liquidación", description = "Calcula base = M − R, comisión = base × 3 % y neto = base − comisión, y verifica el cuadre. No guarda nada.")
    public LiquidacionDto simular(@Parameter(description = "Monto retenido (M)", example = "200.00") @RequestParam BigDecimal monto,
                                  @Parameter(description = "Reembolso (R)", example = "50.00") @RequestParam(defaultValue = "0") BigDecimal reembolso) {
        return LiquidacionDto.de(liquidacionService.calcular(monto, reembolso));
    }

    private Usuario usuarioActual(Authentication authentication) {
        return usuarioService.buscarPorUsername(authentication.getName());
    }
}
