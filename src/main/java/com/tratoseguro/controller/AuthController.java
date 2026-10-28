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
    public String registrar(@RequestParam String nombre, @RequestParam String username,
                            @RequestParam String password, Model model, RedirectAttributes flash) {
        if (nombre.isBlank() || username.isBlank() || password.isBlank()) {
            model.addAttribute("error", "Todos los campos son obligatorios.");
            return "registro";
        }
        if (password.length() < 6) {
            model.addAttribute("error", "La contraseña debe tener al menos 6 caracteres.");
            return "registro";
        }
        if (!usuarioService.registrar(nombre.trim(), username.trim(), password)) {
            model.addAttribute("error", "Ese usuario ya existe.");
            return "registro";
        }
        flash.addFlashAttribute("mensaje", "Cuenta creada. Ahora inicia sesión.");
        return "redirect:/login";
    }
}
