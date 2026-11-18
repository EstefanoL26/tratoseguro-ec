package com.tratoseguro.service;

import com.stripe.exception.StripeException;
import com.tratoseguro.model.MetodoPago;
import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.MovimientoRepository;
import com.tratoseguro.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pago al vendedor con Stripe Connect (modo de prueba).
 *
 * Al liberar un trato pagado con Stripe, el neto se transfiere a la cuenta conectada del vendedor.
 * Si el vendedor todavía no conectó su cuenta (o Stripe rechaza la transferencia), la liberación
 * se registra igual, pero la transferencia queda PENDIENTE y se reintenta:
 *  - cuando el vendedor termina su registro en Stripe, o
 *  - cuando finanzas pulsa "Reintentar transferencias pendientes".
 * Los tratos con pago simulado se liquidan solo en la contabilidad interna (referencia SIMULADO).
 */
@Service
public class CobroVendedorService {

    private static final Logger log = LoggerFactory.getLogger(CobroVendedorService.class);

    private final PagoService pagoService;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public CobroVendedorService(PagoService pagoService, MovimientoRepository movimientoRepository,
                                UsuarioRepository usuarioRepository, NotificacionService notificacionService) {
        this.pagoService = pagoService;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * Paga el neto al vendedor y devuelve la referencia del movimiento de LIBERACION:
     * el id de la transferencia (tr_...), SIMULADO o PENDIENTE.
     */
    public String pagarAlVendedor(Transaccion transaccion, BigDecimal neto) {
        if (!usaStripe(transaccion)) {
            return PagoService.REFERENCIA_SIMULADA;
        }
        Usuario vendedor = transaccion.getVendedor();
        if (!tieneCuentaActiva(vendedor)) {
            notificacionService.notificar(vendedor, transaccion, "Se liberaron $ " + neto + " de \"" + transaccion.getProducto()
                    + "\". Conecta tu cuenta de cobro en Stripe (menú Cobros) para recibir la transferencia.");
            return Movimiento.REFERENCIA_PENDIENTE;
        }
        try {
            return pagoService.transferirAVendedor(transaccion, neto, vendedor.getStripeCuentaId());
        } catch (StripeException e) {
            // La liberación no se bloquea: el dinero queda asignado al vendedor y la transferencia se reintenta
            log.warn("Stripe rechazó la transferencia del trato {}: {}", transaccion.getId(), e.getMessage());
            return Movimiento.REFERENCIA_PENDIENTE;
        }
    }

    // =====================================================================
    // Registro del vendedor en Stripe (onboarding)
    // =====================================================================

    /**
     * Crea (si hace falta) la cuenta conectada del vendedor y devuelve el enlace al registro de Stripe.
     */
    @Transactional
    public String iniciarRegistro(Usuario vendedor, String urlBase) {
        exigirVendedor(vendedor);
        if (!pagoService.isStripeActivo()) {
            throw new ReglaNegocioException("Stripe no está configurado en este servidor: los pagos son simulados y no necesitas una cuenta de cobro.");
        }
        if (vendedor.getStripeCuentaId() == null) {
            vendedor.setStripeCuentaId(pagoService.crearCuentaConectada(vendedor));
            usuarioRepository.save(vendedor);
        }
        return pagoService.crearEnlaceRegistro(vendedor.getStripeCuentaId(), urlBase);
    }

    /**
     * Consulta a Stripe si la cuenta del vendedor ya puede recibir transferencias.
     * Si se activó, paga las transferencias que tenía pendientes. Devuelve si la cuenta está activa.
     */
    @Transactional
    public boolean actualizarEstadoCuenta(Usuario vendedor) {
        if (vendedor.getStripeCuentaId() == null || !pagoService.isStripeActivo()) {
            return false;
        }
        boolean activa = pagoService.cuentaPuedeRecibir(vendedor.getStripeCuentaId());
        boolean seActivo = activa && !vendedor.isStripeCobrosActivos();
        vendedor.setStripeCobrosActivos(activa);
        usuarioRepository.save(vendedor);
        if (seActivo) {
            notificacionService.notificar(vendedor, null, "Tu cuenta de cobro en Stripe quedó activa. Las liberaciones se te transfieren automáticamente.");
        }
        if (activa) {
            reintentarPendientes(vendedor.getId());
        }
        return activa;
    }

    // Aviso del webhook de Stripe (evento account.updated)
    @Transactional
    public void cuentaActualizada(String cuentaId) {
        usuarioRepository.findByStripeCuentaId(cuentaId).ifPresent(this::actualizarEstadoCuenta);
    }

    // =====================================================================
    // Transferencias pendientes
    // =====================================================================

    /**
     * Reintenta las transferencias pendientes (de un vendedor, o de todos si vendedorId es null).
     * Devuelve cuántas se completaron.
     */
    @Transactional
    public int reintentarPendientes(Long vendedorId) {
        if (!pagoService.isStripeActivo()) {
            return 0;
        }
        int completadas = 0;
        for (Movimiento movimiento : pendientes()) {
            Transaccion transaccion = movimiento.getTransaccion();
            Usuario vendedor = transaccion.getVendedor();
            if ((vendedorId != null && !vendedor.getId().equals(vendedorId)) || !tieneCuentaActiva(vendedor)) {
                continue;
            }
            try {
                String referencia = pagoService.transferirAVendedor(transaccion, movimiento.getMonto(), vendedor.getStripeCuentaId());
                movimiento.setReferencia(referencia);
                movimientoRepository.save(movimiento);
                notificacionService.notificar(vendedor, transaccion, "Te transferimos $ " + movimiento.getMonto()
                        + " de \"" + transaccion.getProducto() + "\" a tu cuenta de Stripe.");
                completadas++;
            } catch (StripeException e) {
                log.warn("Sigue pendiente la transferencia del trato {}: {}", transaccion.getId(), e.getMessage());
            }
        }
        return completadas;
    }

    @Transactional(readOnly = true)
    public List<Movimiento> pendientes() {
        return movimientoRepository.findByTipoAndReferenciaOrderByIdAsc(TipoMovimiento.LIBERACION, Movimiento.REFERENCIA_PENDIENTE);
    }

    @Transactional(readOnly = true)
    public List<Movimiento> liberacionesDe(Usuario vendedor) {
        return movimientoRepository.liberacionesDeVendedor(vendedor.getId());
    }

    public boolean isStripeActivo() {
        return pagoService.isStripeActivo();
    }

    private boolean usaStripe(Transaccion transaccion) {
        return pagoService.isStripeActivo() && transaccion.getMetodoPago() == MetodoPago.STRIPE
                && transaccion.getStripePaymentIntent() != null;
    }

    private boolean tieneCuentaActiva(Usuario vendedor) {
        return vendedor.getStripeCuentaId() != null && vendedor.isStripeCobrosActivos();
    }

    private void exigirVendedor(Usuario usuario) {
        if (!usuario.tieneRol(Rol.VENDEDOR)) {
            throw new AccessDeniedException("Solo un vendedor tiene cuenta de cobro.");
        }
    }
}
