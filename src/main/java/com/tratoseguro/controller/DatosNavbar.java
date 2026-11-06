package com.tratoseguro.controller;

import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.service.NotificacionService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Agrega a todas las vistas los datos de la barra de navegación: el usuario conectado,
 * su rol (para mostrar u ocultar menús) y cuántas notificaciones tiene sin leer.
 * Ocultar un menú es solo comodidad: la seguridad real está en SecurityConfig y en los servicios.
 */
@ControllerAdvice(annotations = Controller.class)
public class DatosNavbar {

    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public DatosNavbar(UsuarioRepository usuarioRepository, NotificacionService notificacionService) {
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    @ModelAttribute
    public void datosNavbar(Authentication authentication, Model model) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        Usuario usuario = usuarioRepository.findByUsername(authentication.getName()).orElse(null);
        if (usuario == null) {
            return;
        }
        model.addAttribute("usuarioActual", usuario.getUsername());
        model.addAttribute("nombreActual", usuario.getNombre());
        model.addAttribute("rolActual", usuario.getRol());
        model.addAttribute("esAdmin", usuario.tieneRol(Rol.ADMIN));
        model.addAttribute("esVendedorActual", usuario.tieneRol(Rol.VENDEDOR));
        model.addAttribute("esMediador", usuario.tieneRol(Rol.MEDIADOR));
        model.addAttribute("esFinanzas", usuario.tieneRol(Rol.FINANZAS) || usuario.tieneRol(Rol.ADMIN));
        model.addAttribute("notificacionesNoLeidas", notificacionService.contarNoLeidas(usuario));
    }
}
