package com.tratoseguro.controller;

import com.tratoseguro.model.Movimiento;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.CalificacionService;
import com.tratoseguro.service.CobroVendedorService;
import com.tratoseguro.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * "Mis cobros" del vendedor (solo rol VENDEDOR, ver SecurityConfig):
 * conectar la cuenta de Stripe (modo de prueba) y ver lo que se le ha liberado y transferido.
 */
@Controller
@RequestMapping("/cuenta/cobros")
public class CuentaCobroController {

    private final CobroVendedorService cobroVendedorService;
    private final CalificacionService calificacionService;
    private final UsuarioService usuarioService;

    public CuentaCobroController(CobroVendedorService cobroVendedorService, CalificacionService calificacionService,
                                 UsuarioService usuarioService) {
        this.cobroVendedorService = cobroVendedorService;
        this.calificacionService = calificacionService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String panel(Authentication authentication, Model model) {
        Usuario vendedor = usuarioActual(authentication);
        List<Movimiento> liberaciones = cobroVendedorService.liberacionesDe(vendedor);

        BigDecimal transferido = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        for (Movimiento m : liberaciones) {
            if (m.isTransferenciaPendiente()) {
                pendiente = pendiente.add(m.getMonto());
            } else {
                transferido = transferido.add(m.getMonto());
            }
        }

        model.addAttribute("vendedor", vendedor);
        model.addAttribute("stripeActivo", cobroVendedorService.isStripeActivo());
        model.addAttribute("liberaciones", liberaciones);
        model.addAttribute("totalTransferido", transferido);
        model.addAttribute("totalPendiente", pendiente);
        model.addAttribute("reputacion", calificacionService.reputacion(vendedor));
        return "cuenta/cobros";
    }

    // Crea la cuenta conectada (si no existe) y lleva al vendedor al registro de Stripe
    @PostMapping("/conectar")
    public String conectar(Authentication authentication) {
        String enlace = cobroVendedorService.iniciarRegistro(usuarioActual(authentication), urlBase());
        return "redirect:" + enlace;
    }

    // Stripe manda aquí si el enlace de registro caducó: se genera uno nuevo
    @GetMapping("/reintentar")
    public String reintentar(Authentication authentication) {
        return conectar(authentication);
    }

    // Stripe devuelve aquí al vendedor al terminar (o abandonar) el registro
    @GetMapping("/retorno")
    public String retorno(Authentication authentication, RedirectAttributes flash) {
        boolean activa = cobroVendedorService.actualizarEstadoCuenta(usuarioActual(authentication));
        if (activa) {
            flash.addFlashAttribute("mensaje", "Cuenta de cobro activa. Las liberaciones se te transferirán automáticamente.");
        } else {
            flash.addFlashAttribute("error", "Stripe todavía no activa tu cuenta. Completa el registro o vuelve a intentarlo en unos minutos.");
        }
        return "redirect:/cuenta/cobros";
    }

    @PostMapping("/actualizar")
    public String actualizar(Authentication authentication, RedirectAttributes flash) {
        return retorno(authentication, flash);
    }

    private String urlBase() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }

    private Usuario usuarioActual(Authentication authentication) {
        return usuarioService.buscarPorUsername(authentication.getName());
    }
}
