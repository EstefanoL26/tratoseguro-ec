package com.tratoseguro.service;

import com.tratoseguro.model.Decision;
import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Evidencia;
import com.tratoseguro.model.Liquidacion;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.DisputaRepository;
import com.tratoseguro.repository.EvidenciaRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Disputas: el comprador abre la disputa con evidencia, ambas partes aportan pruebas
 * y el mediador decide cómo se reparte el dinero retenido.
 */
@Service
public class DisputaService {

    private final DisputaRepository disputaRepository;
    private final EvidenciaRepository evidenciaRepository;
    private final TransaccionService transaccionService;
    private final LiquidacionService liquidacionService;
    private final NotificacionService notificacionService;
    private final PagoService pagoService;

    public DisputaService(DisputaRepository disputaRepository, EvidenciaRepository evidenciaRepository,
                          TransaccionService transaccionService, LiquidacionService liquidacionService,
                          NotificacionService notificacionService, PagoService pagoService) {
        this.disputaRepository = disputaRepository;
        this.evidenciaRepository = evidenciaRepository;
        this.transaccionService = transaccionService;
        this.liquidacionService = liquidacionService;
        this.notificacionService = notificacionService;
        this.pagoService = pagoService;
    }

    // =====================================================================
    // Abrir disputa y aportar evidencia
    // =====================================================================

    // PAGADA o ENVIADA -> EN_DISPUTA (solo el comprador). Los plazos quedan pausados.
    @Transactional
    public Disputa abrir(Long transaccionId, Usuario comprador, String motivo, String evidencia, String enlace) {
        Transaccion transaccion = transaccionService.buscar(transaccionId);
        if (!transaccion.esComprador(comprador)) {
            throw new AccessDeniedException("Solo el comprador del trato puede abrir una disputa.");
        }
        String motivoLimpio = limpiar(motivo);
        if (motivoLimpio.length() < 10 || motivoLimpio.length() > 255) {
            throw new ReglaNegocioException("Explica el motivo de la disputa (entre 10 y 255 caracteres).");
        }

        transaccionService.cambiarEstado(transaccion, EstadoTransaccion.EN_DISPUTA, comprador, "Disputa abierta: " + motivoLimpio);
        Disputa disputa = disputaRepository.save(new Disputa(transaccion, motivoLimpio, Reloj.ahora()));
        if (!limpiar(evidencia).isEmpty()) {
            guardarEvidencia(disputa, comprador, evidencia, enlace);
        }
        notificacionService.notificarMediadores(transaccion,
                "Nueva disputa en \"" + transaccion.getProducto() + "\" ($ " + transaccion.getMonto() + "). Revisa la evidencia.");
        return disputa;
    }

    // Comprador o vendedor, mientras la disputa no esté resuelta
    @Transactional
    public void agregarEvidencia(Long transaccionId, Usuario usuario, String descripcion, String enlace) {
        Disputa disputa = disputaRepository.findByTransaccionId(transaccionId)
                .orElseThrow(() -> new ReglaNegocioException("Este trato no tiene una disputa abierta."));
        if (!disputa.getTransaccion().participa(usuario)) {
            throw new AccessDeniedException("Solo las partes del trato pueden aportar evidencia.");
        }
        if (disputa.isResuelta()) {
            throw new ReglaNegocioException("La disputa ya fue resuelta; no se puede agregar más evidencia.");
        }
        guardarEvidencia(disputa, usuario, descripcion, enlace);
        notificacionService.notificarMediadores(disputa.getTransaccion(),
                usuario.getNombre() + " aportó evidencia en la disputa de \"" + disputa.getTransaccion().getProducto() + "\".");
    }

    private void guardarEvidencia(Disputa disputa, Usuario usuario, String descripcion, String enlace) {
        String texto = limpiar(descripcion);
        if (texto.length() < 5 || texto.length() > 255) {
            throw new ReglaNegocioException("Describe la evidencia (entre 5 y 255 caracteres).");
        }
        String enlaceLimpio = limpiar(enlace);
        if (!enlaceLimpio.isEmpty() && (!enlaceLimpio.matches("(?i)^https?://\\S+$") || enlaceLimpio.length() > 255)) {
            throw new ReglaNegocioException("El enlace debe empezar con http:// o https:// (por ejemplo, una foto en Google Drive).");
        }
        evidenciaRepository.save(new Evidencia(disputa, usuario, texto, enlaceLimpio.isEmpty() ? null : enlaceLimpio, Reloj.ahora()));
    }

    // =====================================================================
    // Resolución del mediador
    // =====================================================================

    /**
     * EN_DISPUTA -> LIBERADA (pago al vendedor) o REEMBOLSADA (reembolso total, parcial o vía rápida).
     * Vía rápida: el mediador confirma un fraude, se omite la negociación y se reembolsa todo de inmediato.
     */
    @Transactional
    public Liquidacion resolver(Long disputaId, Usuario mediador, Decision decision, BigDecimal reembolsoPedido,
                                boolean viaRapida, String justificacion) {
        if (!mediador.tieneRol(Rol.MEDIADOR)) {
            throw new AccessDeniedException("Solo un mediador puede resolver disputas.");
        }
        Disputa disputa = buscar(disputaId);
        Transaccion transaccion = disputa.getTransaccion();
        if (disputa.isResuelta() || transaccion.getEstado() != EstadoTransaccion.EN_DISPUTA) {
            throw new ReglaNegocioException("Esta disputa ya fue resuelta.");
        }
        String justificacionLimpia = limpiar(justificacion);
        if (justificacionLimpia.length() < 10 || justificacionLimpia.length() > 500) {
            throw new ReglaNegocioException("Escribe la justificación de la decisión (entre 10 y 500 caracteres).");
        }

        Decision decisionFinal = viaRapida ? Decision.REEMBOLSO_TOTAL : decision;
        BigDecimal reembolso = calcularReembolso(decisionFinal, reembolsoPedido, transaccion.getMonto());

        // Primero se valida que cuadre (si no cuadra, no se toca Stripe ni el estado)
        liquidacionService.calcular(transaccion.getMonto(), reembolso);
        String referencia = reembolso.signum() > 0 ? pagoService.reembolsar(transaccion, reembolso) : null;
        Liquidacion liquidacion = liquidacionService.liquidar(transaccion, reembolso, referencia);

        disputa.setMediador(mediador);
        disputa.setDecision(decisionFinal);
        disputa.setReembolso(liquidacion.getReembolso());
        disputa.setViaRapida(viaRapida);
        disputa.setJustificacion(justificacionLimpia);
        disputa.setFechaResolucion(Reloj.ahora());

        EstadoTransaccion nuevo = reembolso.signum() == 0 ? EstadoTransaccion.LIBERADA : EstadoTransaccion.REEMBOLSADA;
        String motivo = (viaRapida ? "Vía rápida por fraude: " : "Decisión del mediador: ") + decisionFinal.getDescripcion()
                + " (reembolso $ " + liquidacion.getReembolso() + ")";
        transaccionService.cambiarEstado(transaccion, nuevo, mediador, motivo);
        return liquidacion;
    }

    private BigDecimal calcularReembolso(Decision decision, BigDecimal pedido, BigDecimal monto) {
        if (decision == null) {
            throw new ReglaNegocioException("Escoge una decisión.");
        }
        switch (decision) {
            case REEMBOLSO_TOTAL:
                return monto;
            case PAGO_AL_VENDEDOR:
                return BigDecimal.ZERO;
            default:
                // Reembolso parcial: tiene que ser mayor que 0 y menor que el monto retenido
                if (pedido == null || pedido.signum() <= 0 || pedido.compareTo(monto) >= 0) {
                    throw new ReglaNegocioException("En un reembolso parcial el monto debe ser mayor a $ 0,00 y menor a $ " + monto + ".");
                }
                if (pedido.scale() > 2) {
                    throw new ReglaNegocioException("El reembolso admite máximo 2 decimales.");
                }
                return pedido.setScale(2, RoundingMode.HALF_UP);
        }
    }

    // =====================================================================
    // Consultas
    // =====================================================================

    public Disputa buscar(Long id) {
        return disputaRepository.findById(id)
                .orElseThrow(() -> new ReglaNegocioException("La disputa #" + id + " no existe."));
    }

    public Disputa buscarPorTransaccion(Long transaccionId) {
        return disputaRepository.findByTransaccionId(transaccionId).orElse(null);
    }

    public List<Evidencia> evidencias(Long disputaId) {
        return evidenciaRepository.findByDisputaIdOrderByIdAsc(disputaId);
    }

    public List<Disputa> pendientes() {
        return disputaRepository.findByDecisionIsNullOrderByFechaAperturaAsc();
    }

    public List<Disputa> resueltas() {
        return disputaRepository.findByDecisionIsNotNullOrderByFechaResolucionDesc();
    }

    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
