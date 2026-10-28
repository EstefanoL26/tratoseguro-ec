package com.tratoseguro.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Agrega a todas las vistas el usuario conectado y si es administrador,
 * para mostrar u ocultar el menú "Administración" en la barra de navegación.
 * Ocultar el menú es solo comodidad: la seguridad real está en SecurityConfig.
 */
@ControllerAdvice
public class DatosNavbar {

    @ModelAttribute("usuarioActual")
    public String usuarioActual(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }

    @ModelAttribute("esAdmin")
    public boolean esAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
