package com.tratoseguro.controller;

import com.tratoseguro.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Login y registro. El POST /login y el POST /logout los procesa Spring Security.
 */
@Controller
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/")
    public String inicio() {
        return "redirect:/transacciones";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/registro")
    public String registro() {
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam String nombre, @RequestParam String username, @RequestParam String email,
                            @RequestParam String cedula, @RequestParam String password,
                            @RequestParam(defaultValue = "COMPRADOR") String rol,
                            Model model, RedirectAttributes flash) {
        if (nombre.isBlank() || username.isBlank() || email.isBlank() || cedula.isBlank() || password.isBlank()) {
            model.addAttribute("error", "Todos los campos son obligatorios.");
            return "registro";
        }
        if (!email.contains("@")) {
            model.addAttribute("error", "Escribe un correo válido.");
            return "registro";
        }
        if (password.length() < 6) {
            model.addAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "registro";
        }
        // La cédula y el rol se validan en el servicio
        String error = usuarioService.registrar(nombre.trim(), username.trim(), email.trim(), cedula.trim(), password, rol);
        if (error != null) {
            model.addAttribute("error", error);
            return "registro";
        }
        flash.addFlashAttribute("mensaje", "Cuenta creada. Ahora inicia sesión.");
        return "redirect:/login";
    }
}
