package com.tratoseguro.service;

import com.tratoseguro.model.Notificacion;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.NotificacionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.util.Reloj;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notificaciones internas: cada cambio importante de un trato avisa a las partes.
 */
@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    public NotificacionService(NotificacionRepository notificacionRepository, UsuarioRepository usuarioRepository) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void notificar(Usuario usuario, Transaccion transaccion, String mensaje) {
        String texto = mensaje.length() > 255 ? mensaje.substring(0, 252) + "..." : mensaje;
        notificacionRepository.save(new Notificacion(usuario, transaccion, texto, Reloj.ahora()));
    }

    // Avisa al comprador y al vendedor del trato
    @Transactional
    public void notificarPartes(Transaccion transaccion, String mensaje) {
        notificar(transaccion.getComprador(), transaccion, mensaje);
        notificar(transaccion.getVendedor(), transaccion, mensaje);
    }

    // Avisa a todos los mediadores (por ejemplo, cuando se abre una disputa)
    @Transactional
    public void notificarMediadores(Transaccion transaccion, String mensaje) {
        for (Usuario mediador : usuarioRepository.findByRolOrderByNombreAsc(Rol.MEDIADOR)) {
            notificar(mediador, transaccion, mensaje);
        }
    }

    public List<Notificacion> listar(Usuario usuario) {
        return notificacionRepository.findTop50ByUsuarioIdOrderByIdDesc(usuario.getId());
    }

    public long contarNoLeidas(Usuario usuario) {
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuario.getId());
    }

    // Al eliminar un trato (solo en CREADA) se borran también sus avisos
    @Transactional
    public void eliminarDeTransaccion(Long transaccionId) {
        notificacionRepository.deleteAll(notificacionRepository.findByTransaccionId(transaccionId));
    }

    @Transactional
    public void marcarTodasLeidas(Usuario usuario) {
        for (Notificacion notificacion : notificacionRepository.findByUsuarioIdAndLeidaFalse(usuario.getId())) {
            notificacion.marcarLeida();
        }
    }
}
