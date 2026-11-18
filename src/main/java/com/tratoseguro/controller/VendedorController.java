package com.tratoseguro.controller;

import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.CalificacionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Perfil público del vendedor: reputación y comentarios de sus compradores.
 * Lo puede ver cualquier usuario con sesión, para decidir si confía en él antes de pagar.
 */
@Controller
public class VendedorController {

    private final CalificacionService calificacionService;

    public VendedorController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @GetMapping("/vendedores/{id}")
    public String perfil(@PathVariable Long id, Model model) {
        Usuario vendedor = calificacionService.buscarVendedor(id);
        model.addAttribute("vendedor", vendedor);
        model.addAttribute("reputacion", calificacionService.reputacion(vendedor));
        model.addAttribute("calificaciones", calificacionService.calificacionesDe(vendedor));
        return "vendedores/perfil";
    }
}
