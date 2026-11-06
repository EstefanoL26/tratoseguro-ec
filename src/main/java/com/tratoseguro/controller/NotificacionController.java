package com.tratoseguro.controller;

import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.NotificacionService;
import com.tratoseguro.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Notificaciones internas del usuario conectado.
 */
@Controller
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final UsuarioService usuarioService;

    public NotificacionController(NotificacionService notificacionService, UsuarioService usuarioService) {
        this.notificacionService = notificacionService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        Usuario usuario = usuarioService.buscarPorUsername(authentication.getName());
        model.addAttribute("notificaciones", notificacionService.listar(usuario));
        return "notificaciones";
    }

    @PostMapping("/leidas")
    public String marcarLeidas(Authentication authentication) {
        notificacionService.marcarTodasLeidas(usuarioService.buscarPorUsername(authentication.getName()));
        return "redirect:/notificaciones";
    }
}
