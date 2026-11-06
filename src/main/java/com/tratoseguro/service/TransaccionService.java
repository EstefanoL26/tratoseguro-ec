package com.tratoseguro.service;

import com.stripe.model.checkout.Session;
import com.tratoseguro.model.Envio;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Historial;
import com.tratoseguro.model.MetodoPago;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.DisputaRepository;
import com.tratoseguro.repository.EnvioRepository;
import com.tratoseguro.repository.HistorialRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CORE: motor de custodia del pago. Controla en qué estado está cada trato, quién puede
 * cambiarlo, cuándo vencen los plazos y cuándo se liquida el dinero retenido.
 * Todas las reglas se validan aquí, en el servidor, no solo ocultando botones en la vista.
 */
@Service
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final EnvioRepository envioRepository;
    private final HistorialRepository historialRepository;
    private final DisputaRepository disputaRepository;
    private final DiasHabilesService diasHabilesService;
    private final LiquidacionService liquidacionService;
    private final NotificacionService notificacionService;
    private final PagoService pagoService;

    public TransaccionService(TransaccionRepository transaccionRepository, UsuarioRepository usuarioRepository,
                              EnvioRepository envioRepository, HistorialRepository historialRepository,
                              DisputaRepository disputaRepository, DiasHabilesService diasHabilesService,
                              LiquidacionService liquidacionService, NotificacionService notificacionService,
                              PagoService pagoService) {
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.envioRepository = envioRepository;
        this.historialRepository = historialRepository;
        this.disputaRepository = disputaRepository;
        this.diasHabilesService = diasHabilesService;
        this.liquidacionService = liquidacionService;
        this.notificacionService = notificacionService;
        this.pagoService = pagoService;
    }

    // =====================================================================
    // Consultas y permisos de lectura
    // =====================================================================

    /**
     * Tratos que el usuario puede ver:
     * comprador y vendedor solo ven los suyos; el mediador, los que tienen disputa;
     * finanzas y el administrador ven todos (solo lectura).
     */
    @Transactional(readOnly = true)
    public List<Transaccion> listarPara(Usuario usuario, String buscar, EstadoTransaccion estado) {
        List<Transaccion> base;
        if (usuario.tieneRol(Rol.ADMIN) || usuario.tieneRol(Rol.FINANZAS)) {
            base = transaccionRepository.findAllByOrderByIdDesc();
        } else if (usuario.tieneRol(Rol.MEDIADOR)) {
            base = new ArrayList<>();
            for (Transaccion t : transaccionRepository.findAllByOrderByIdDesc()) {
                if (disputaRepository.findByTransaccionId(t.getId()).isPresent()) {
                    base.add(t);
                }
            }
        } else {
            base = transaccionRepository.buscarDeUsuario(usuario.getId());
        }

        String texto = buscar == null ? "" : buscar.trim().toLowerCase();
        List<Transaccion> resultado = new ArrayList<>();
        for (Transaccion t : base) {
            boolean coincideTexto = texto.isEmpty() || t.getProducto().toLowerCase().contains(texto);
            boolean coincideEstado = estado == null || t.getEstado() == estado;
            if (coincideTexto && coincideEstado) {
                resultado.add(t);
            }
        }
        return resultado;
    }

    // Busca un trato y comprueba que el usuario pueda verlo (si no, responde 403)
    @Transactional(readOnly = true)
    public Transaccion obtenerParaVer(Long id, Usuario usuario) {
        Transaccion transaccion = buscar(id);
        if (!puedeVer(transaccion, usuario)) {
            throw new AccessDeniedException("No participas en este trato.");
        }
        return transaccion;
    }

    public boolean puedeVer(Transaccion transaccion, Usuario usuario) {
        if (transaccion.participa(usuario) || usuario.tieneRol(Rol.ADMIN) || usuario.tieneRol(Rol.FINANZAS)) {
            return true;
        }
        return usuario.tieneRol(Rol.MEDIADOR) && disputaRepository.findByTransaccionId(transaccion.getId()).isPresent();
    }

    @Transactional(readOnly = true)
    public Envio buscarEnvio(Long transaccionId) {
        return envioRepository.findByTransaccionId(transaccionId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Historial> historial(Long transaccionId) {
        return historialRepository.findByTransaccionIdOrderByIdAsc(transaccionId);
    }

    public List<Usuario> compradoresDisponibles() {
        return usuarioRepository.findByRolOrderByNombreAsc(Rol.COMPRADOR);
    }

    // =====================================================================
    // CRUD del vendedor (solo mientras el trato está en CREADA)
    // =====================================================================

    /**
     * Crea o edita un trato. El vendedor es siempre el usuario conectado.
     * Los errores se marcan en "errores" para mostrarlos junto a cada campo. Devuelve el trato guardado o null.
     */
    @Transactional
    public Transaccion guardar(Transaccion datos, Long compradorId, Usuario vendedor, Errors errores) {
        if (!vendedor.tieneRol(Rol.VENDEDOR)) {
            throw new AccessDeniedException("Solo un vendedor puede crear tratos.");
        }

        Usuario comprador = compradorId == null ? null : usuarioRepository.findById(compradorId).orElse(null);
        if (comprador == null || !comprador.tieneRol(Rol.COMPRADOR)) {
            errores.rejectValue("comprador", "requerido", "Escoge un comprador de la lista.");
        } else if (comprador.getId().equals(vendedor.getId())) {
            errores.rejectValue("comprador", "mismo", "No puedes venderte a ti mismo.");
        }
        if (errores.hasErrors()) {
            return null;
        }

        Transaccion transaccion;
        if (datos.getId() == null) {
            transaccion = new Transaccion();
            transaccion.setVendedor(vendedor);
            transaccion.setEstado(EstadoTransaccion.CREADA);
            transaccion.setFechaCreacion(Reloj.ahora());
        } else {
            // Edición: se vuelve a buscar en la base para no confiar en lo que llega del formulario
            transaccion = buscar(datos.getId());
            if (!transaccion.esVendedor(vendedor)) {
                throw new AccessDeniedException("Solo el vendedor del trato puede editarlo.");
            }
            if (!transaccion.isEditable()) {
                throw new ReglaNegocioException("El trato ya no se puede editar porque no está en estado Creada.");
            }
        }

        // Se copian solo los campos que el vendedor puede cambiar
        transaccion.setProducto(datos.getProducto().trim());
        transaccion.setDescripcion(datos.getDescripcion() == null ? null : datos.getDescripcion().trim());
        transaccion.setMonto(datos.getMonto());
        transaccion.setDiasEntrega(datos.getDiasEntrega());
        transaccion.setComprador(comprador);

        boolean esNuevo = transaccion.getId() == null;
        transaccionRepository.save(transaccion);
        if (esNuevo) {
            historialRepository.save(new Historial(transaccion, vendedor, null, EstadoTransaccion.CREADA,
                    Reloj.ahora(), "Trato creado por el vendedor"));
            notificacionService.notificar(comprador, transaccion,
                    vendedor.getNombre() + " creó el trato \"" + transaccion.getProducto() + "\". Ya puedes pagarlo.");
        }
        return transaccion;
    }

    @Transactional
    public void eliminar(Long id, Usuario usuario) {
        Transaccion transaccion = buscar(id);
        if (!transaccion.esVendedor(usuario)) {
            throw new AccessDeniedException("Solo el vendedor del trato puede eliminarlo.");
        }
        if (!transaccion.isEditable()) {
            throw new ReglaNegocioException("Solo se pueden eliminar tratos en estado Creada.");
        }
        // Un trato en CREADA solo tiene su historial de creación y avisos: se borran primero
        historialRepository.deleteAll(historialRepository.findByTransaccionIdOrderByIdAsc(id));
        notificacionService.eliminarDeTransaccion(id);
        transaccionRepository.delete(transaccion);
    }

    // Comprador o vendedor, antes de pagar
    @Transactional
    public void cancelar(Long id, Usuario usuario) {
        Transaccion transaccion = buscar(id);
        if (!transaccion.participa(usuario)) {
            throw new AccessDeniedException("Solo las partes del trato pueden cancelarlo.");
        }
        cambiarEstado(transaccion, EstadoTransaccion.CANCELADA, usuario, "Cancelado por " + usuario.getNombre());
    }

    // =====================================================================
    // Pago y retención de fondos
    // =====================================================================

    /**
     * Inicia el pago del comprador.
     * Con Stripe devuelve la URL de Stripe Checkout; sin Stripe registra un pago simulado y devuelve null.
     */
    @Transactional
    public String iniciarPago(Long id, Usuario comprador, String urlBase) {
        Transaccion transaccion = buscar(id);
        validarPuedePagar(transaccion, comprador);

        if (pagoService.isStripeActivo()) {
            Session sesion = pagoService.crearSesionCheckout(transaccion, urlBase);
            transaccion.setStripeSessionId(sesion.getId());
            return sesion.getUrl();
        }
        registrarPago(transaccion, MetodoPago.SIMULADO, PagoService.REFERENCIA_SIMULADA, comprador);
        return null;
    }

    // Solo para los datos de ejemplo (DatosIniciales): registra un pago simulado sin pasar por Stripe
    @Transactional
    public void registrarPagoSimulado(Long id, Usuario comprador) {
        Transaccion transaccion = buscar(id);
        validarPuedePagar(transaccion, comprador);
        registrarPago(transaccion, MetodoPago.SIMULADO, PagoService.REFERENCIA_SIMULADA, comprador);
    }

    /**
     * Confirma un pago de Stripe Checkout. La llaman la página de éxito y el webhook;
     * es idempotente: si el trato ya está pagado no hace nada.
     * Devuelve el trato (o null si la sesión no corresponde a ningún trato).
     */
    @Transactional
    public Transaccion confirmarPagoStripe(String sessionId) {
        Transaccion transaccion = transaccionRepository.findByStripeSessionId(sessionId).orElse(null);
        if (transaccion == null) {
            return null;
        }
        if (transaccion.getEstado() != EstadoTransaccion.CREADA) {
            return transaccion;
        }
        Session sesion = pagoService.obtenerSesion(sessionId);
        String idTrato = sesion.getMetadata() == null ? null : sesion.getMetadata().get("transaccionId");
        if (!"paid".equals(sesion.getPaymentStatus()) || !transaccion.getId().toString().equals(idTrato)) {
            throw new ReglaNegocioException("Stripe todavía no confirma el pago de este trato.");
        }
        transaccion.setStripePaymentIntent(sesion.getPaymentIntent());
        registrarPago(transaccion, MetodoPago.STRIPE, sesion.getPaymentIntent(), transaccion.getComprador());
        return transaccion;
    }

    // CREADA -> PAGADA: se retiene el dinero y empieza a correr el límite de envío
    private void registrarPago(Transaccion transaccion, MetodoPago metodo, String referencia, Usuario comprador) {
        LocalDateTime ahora = Reloj.ahora();
        transaccion.setMetodoPago(metodo);
        transaccion.setFechaPago(ahora);
        transaccion.setFechaLimiteEnvio(diasHabilesService.sumarDiasHabiles(ahora.toLocalDate(), transaccion.getDiasEntrega()));
        cambiarEstado(transaccion, EstadoTransaccion.PAGADA, comprador, "Pago recibido (" + metodo.getDescripcion() + ")");
        liquidacionService.registrarMovimiento(transaccion, TipoMovimiento.RETENCION, transaccion.getMonto(), ahora, referencia);
    }

    private void validarPuedePagar(Transaccion transaccion, Usuario usuario) {
        if (!transaccion.esComprador(usuario)) {
            throw new AccessDeniedException("Solo el comprador del trato puede pagarlo.");
        }
        if (transaccion.getEstado() != EstadoTransaccion.CREADA) {
            throw new ReglaNegocioException("Este trato ya no se puede pagar (estado: " + transaccion.getEstado().getDescripcion() + ").");
        }
    }

    // =====================================================================
    // Envío y confirmación de recepción
    // =====================================================================

    // PAGADA -> ENVIADA: el vendedor registra la guía y empieza a correr el límite de revisión
    @Transactional
    public void registrarEnvio(Long id, Usuario vendedor, String transportista, String guia) {
        Transaccion transaccion = buscar(id);
        if (!transaccion.esVendedor(vendedor)) {
            throw new AccessDeniedException("Solo el vendedor del trato puede registrar el envío.");
        }
        String transportistaLimpio = transportista == null ? "" : transportista.trim();
        String guiaLimpia = guia == null ? "" : guia.trim().toUpperCase();
        if (transportistaLimpio.isEmpty() || transportistaLimpio.length() > 100) {
            throw new ReglaNegocioException("Escoge el transportista.");
        }
        if (!guiaLimpia.matches("[A-Z0-9-]{5,40}")) {
            throw new ReglaNegocioException("La guía debe tener entre 5 y 40 letras, números o guiones.");
        }

        LocalDateTime ahora = Reloj.ahora();
        cambiarEstado(transaccion, EstadoTransaccion.ENVIADA, vendedor, "Enviado por " + transportistaLimpio + ", guía " + guiaLimpia);
        envioRepository.save(new Envio(transaccion, transportistaLimpio, guiaLimpia, ahora));
        transaccion.setFechaLimiteRevision(
                diasHabilesService.sumarDiasHabiles(ahora.toLocalDate(), transaccion.getDiasEntrega() + 3));
    }

    // ENVIADA -> LIBERADA: el comprador confirma y se paga al vendedor (menos la comisión)
    @Transactional
    public void confirmarRecepcion(Long id, Usuario comprador) {
        Transaccion transaccion = buscar(id);
        if (!transaccion.esComprador(comprador)) {
            throw new AccessDeniedException("Solo el comprador del trato puede confirmar la recepción.");
        }
        if (transaccion.getEstado() != EstadoTransaccion.ENVIADA) {
            throw new ReglaNegocioException("Solo puedes confirmar la recepción de un trato enviado.");
        }
        liquidacionService.liquidar(transaccion, BigDecimal.ZERO, null);
        cambiarEstado(transaccion, EstadoTransaccion.LIBERADA, comprador, "El comprador confirmó la recepción");
    }

    // =====================================================================
    // Plazos automáticos (los llama PlazoService una vez al día)
    // =====================================================================

    @Transactional(readOnly = true)
    public List<Long> idsConPlazoVencido(LocalDate hoy) {
        List<Long> ids = new ArrayList<>();
        transaccionRepository.findByEstadoAndFechaLimiteEnvioBefore(EstadoTransaccion.PAGADA, hoy)
                .forEach(t -> ids.add(t.getId()));
        transaccionRepository.findByEstadoAndFechaLimiteRevisionBefore(EstadoTransaccion.ENVIADA, hoy)
                .forEach(t -> ids.add(t.getId()));
        return ids;
    }

    /**
     * Aplica el plazo vencido de un trato (cada uno en su propia transacción):
     *  - PAGADA y venció el límite de envío -> reembolso total al comprador (REEMBOLSADA)
     *  - ENVIADA y venció el límite de revisión sin reclamo -> liberación al vendedor (LIBERADA)
     * Los tratos EN_DISPUTA no se tocan: sus plazos quedan pausados hasta que decida el mediador.
     * Devuelve true si cambió algo.
     */
    @Transactional
    public boolean aplicarPlazo(Long id, LocalDate hoy) {
        Transaccion transaccion = buscar(id);
        if (transaccion.getEstado() == EstadoTransaccion.PAGADA
                && transaccion.getFechaLimiteEnvio() != null && transaccion.getFechaLimiteEnvio().isBefore(hoy)) {
            String referencia = pagoService.reembolsar(transaccion, transaccion.getMonto());
            liquidacionService.liquidar(transaccion, transaccion.getMonto(), referencia);
            cambiarEstado(transaccion, EstadoTransaccion.REEMBOLSADA, null,
                    "Venció el límite de envío (" + transaccion.getFechaLimiteEnvio() + "): reembolso total al comprador");
            return true;
        }
        if (transaccion.getEstado() == EstadoTransaccion.ENVIADA
                && transaccion.getFechaLimiteRevision() != null && transaccion.getFechaLimiteRevision().isBefore(hoy)) {
            liquidacionService.liquidar(transaccion, BigDecimal.ZERO, null);
            cambiarEstado(transaccion, EstadoTransaccion.LIBERADA, null,
                    "Venció el límite de revisión (" + transaccion.getFechaLimiteRevision() + ") sin reclamo: pago al vendedor");
            return true;
        }
        return false;
    }

    // =====================================================================
    // Máquina de estados: único lugar donde cambia el estado de un trato
    // =====================================================================

    /**
     * Valida la transición, cambia el estado, guarda el historial y notifica a las partes.
     * usuario == null significa que el cambio lo hizo el sistema.
     */
    @Transactional
    public void cambiarEstado(Transaccion transaccion, EstadoTransaccion nuevo, Usuario usuario, String motivo) {
        EstadoTransaccion anterior = transaccion.getEstado();
        if (!anterior.puedeCambiarA(nuevo)) {
            throw new ReglaNegocioException("No se puede pasar un trato de \"" + anterior.getDescripcion()
                    + "\" a \"" + nuevo.getDescripcion() + "\".");
        }
        transaccion.setEstado(nuevo);
        transaccionRepository.save(transaccion);
        historialRepository.save(new Historial(transaccion, usuario, anterior, nuevo, Reloj.ahora(), motivo));
        notificacionService.notificarPartes(transaccion,
                "\"" + transaccion.getProducto() + "\" pasó a " + nuevo.getDescripcion() + ". " + motivo + ".");
    }

    public Transaccion buscar(Long id) {
        return transaccionRepository.findById(id)
                .orElseThrow(() -> new ReglaNegocioException("El trato #" + id + " no existe."));
    }
}
