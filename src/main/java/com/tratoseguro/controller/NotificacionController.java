package com.tratoseguro.controller;

import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.AvisoPushService;
import com.tratoseguro.service.CorreoService;
import com.tratoseguro.service.NotificacionService;
import com.tratoseguro.service.ReglaNegocioException;
import com.tratoseguro.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Notificaciones del usuario conectado: la lista interna y los avisos fuera de la app
 * (correo y push al celular).
 */
@Controller
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final UsuarioService usuarioService;
    private final CorreoService correoService;
    private final AvisoPushService avisoPushService;

    public NotificacionController(NotificacionService notificacionService, UsuarioService usuarioService,
                                  CorreoService correoService, AvisoPushService avisoPushService) {
        this.notificacionService = notificacionService;
        this.usuarioService = usuarioService;
        this.correoService = correoService;
        this.avisoPushService = avisoPushService;
    }

    // Lo que manda el navegador al suscribirse (formato estándar de PushSubscription.toJSON())
    public record SuscripcionPush(String endpoint, Claves keys) {
        public record Claves(String p256dh, String auth) {
        }
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        Usuario usuario = usuarioService.buscarPorUsername(authentication.getName());
        model.addAttribute("notificaciones", notificacionService.listar(usuario));
        model.addAttribute("correoActivo", correoService.isActivo());
        model.addAttribute("emailUsuario", usuario.getEmail());
        model.addAttribute("pushActivo", avisoPushService.isActivo());
        model.addAttribute("vapidPublica", avisoPushService.getClavePublica());
        model.addAttribute("dispositivos", avisoPushService.contarDispositivos(usuario));
        return "notificaciones";
    }

    @PostMapping("/leidas")
    public String marcarLeidas(Authentication authentication) {
        notificacionService.marcarTodasLeidas(usuarioService.buscarPorUsername(authentication.getName()));
        return "redirect:/notificaciones";
    }

    // Manda un aviso de prueba por todos los canales: campana, correo y push
    @PostMapping("/prueba")
    public String prueba(Authentication authentication, RedirectAttributes flash) {
        Usuario usuario = usuarioService.buscarPorUsername(authentication.getName());
        notificacionService.notificar(usuario, null, "Aviso de prueba: así te llegarán las novedades de tus tratos.");
        flash.addFlashAttribute("mensaje", "Enviamos un aviso de prueba"
                + (correoService.isActivo() ? " a tu correo" : "")
                + (avisoPushService.isActivo() && avisoPushService.contarDispositivos(usuario) > 0 ? " y a tus dispositivos" : "")
                + ". También aparece aquí abajo.");
        return "redirect:/notificaciones";
    }

    // El navegador guarda este dispositivo para recibir push (fetch con JSON y token CSRF)
    @PostMapping("/push/suscribir")
    @ResponseBody
    public ResponseEntity<String> suscribir(@RequestBody SuscripcionPush datos, Authentication authentication) {
        if (!avisoPushService.isActivo()) {
            return ResponseEntity.status(503).body("Las notificaciones push no están configuradas en este servidor.");
        }
        if (datos == null || datos.keys() == null) {
            return ResponseEntity.badRequest().body("El navegador envió una suscripción inválida.");
        }
        try {
            avisoPushService.suscribir(usuarioService.buscarPorUsername(authentication.getName()),
                    datos.endpoint(), datos.keys().p256dh(), datos.keys().auth());
            return ResponseEntity.ok("ok");
        } catch (ReglaNegocioException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/push/cancelar")
    @ResponseBody
    public ResponseEntity<String> cancelar(@RequestBody SuscripcionPush datos, Authentication authentication) {
        if (datos != null && datos.endpoint() != null) {
            avisoPushService.cancelar(usuarioService.buscarPorUsername(authentication.getName()), datos.endpoint());
        }
        return ResponseEntity.ok("ok");
    }
}
