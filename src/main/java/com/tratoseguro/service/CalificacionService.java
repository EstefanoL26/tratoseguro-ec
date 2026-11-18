package com.tratoseguro.service;

import com.tratoseguro.model.Calificacion;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Reputacion;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.CalificacionRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reputación del vendedor: el comprador lo califica (1 a 5 estrellas) cuando el trato se cierra,
 * y antes de pagar cualquier trato ve el promedio, cuántos tratos cerró y cuántos terminaron en disputa.
 */
@Service
public class CalificacionService {

    private final CalificacionRepository calificacionRepository;
    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransaccionService transaccionService;
    private final NotificacionService notificacionService;

    public CalificacionService(CalificacionRepository calificacionRepository, TransaccionRepository transaccionRepository,
                               UsuarioRepository usuarioRepository, TransaccionService transaccionService,
                               NotificacionService notificacionService) {
        this.calificacionRepository = calificacionRepository;
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.transaccionService = transaccionService;
        this.notificacionService = notificacionService;
    }

    /**
     * El comprador califica al vendedor. Solo una vez por trato y solo cuando el trato
     * terminó con movimiento de dinero (LIBERADA o REEMBOLSADA).
     */
    @Transactional
    public Calificacion calificar(Long transaccionId, Usuario comprador, Integer puntaje, String comentario) {
        Transaccion transaccion = transaccionService.buscar(transaccionId);
        if (!transaccion.esComprador(comprador)) {
            throw new AccessDeniedException("Solo el comprador del trato puede calificar al vendedor.");
        }
        if (!puedeCalificarse(transaccion)) {
            throw new ReglaNegocioException("Solo puedes calificar un trato liberado o reembolsado.");
        }
        if (calificacionRepository.findByTransaccionId(transaccionId).isPresent()) {
            throw new ReglaNegocioException("Ya calificaste este trato.");
        }
        if (puntaje == null || puntaje < 1 || puntaje > 5) {
            throw new ReglaNegocioException("Escoge de 1 a 5 estrellas.");
        }
        String texto = comentario == null ? "" : comentario.trim();
        if (texto.length() > 300) {
            throw new ReglaNegocioException("El comentario admite máximo 300 caracteres.");
        }

        Calificacion calificacion = calificacionRepository.save(
                new Calificacion(transaccion, puntaje, texto.isEmpty() ? null : texto, Reloj.ahora()));
        notificacionService.notificar(transaccion.getVendedor(), transaccion,
                comprador.getNombre() + " te calificó con " + puntaje + (puntaje == 1 ? " estrella" : " estrellas")
                        + " por \"" + transaccion.getProducto() + "\".");
        return calificacion;
    }

    public boolean puedeCalificarse(Transaccion transaccion) {
        return transaccion.getEstado() == EstadoTransaccion.LIBERADA || transaccion.getEstado() == EstadoTransaccion.REEMBOLSADA;
    }

    @Transactional(readOnly = true)
    public Calificacion deTransaccion(Long transaccionId) {
        return calificacionRepository.findByTransaccionId(transaccionId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Reputacion reputacion(Usuario vendedor) {
        Long id = vendedor.getId();
        return Reputacion.de(calificacionRepository.promedioDeVendedor(id), calificacionRepository.countByVendedorId(id),
                transaccionRepository.contarCerradosDeVendedor(id), transaccionRepository.contarDisputasResueltasDeVendedor(id));
    }

    @Transactional(readOnly = true)
    public List<Calificacion> calificacionesDe(Usuario vendedor) {
        return calificacionRepository.findByVendedorIdOrderByFechaDesc(vendedor.getId());
    }

    // Perfil público de un vendedor (cualquier usuario con sesión puede verlo antes de tratar con él)
    @Transactional(readOnly = true)
    public Usuario buscarVendedor(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ReglaNegocioException("El vendedor #" + id + " no existe."));
        if (!usuario.tieneRol(Rol.VENDEDOR)) {
            throw new ReglaNegocioException("El usuario #" + id + " no es vendedor.");
        }
        return usuario;
    }
}
