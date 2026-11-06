package com.tratoseguro.controller;

import com.tratoseguro.model.Feriado;
import com.tratoseguro.service.FeriadoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ADMINISTRACIÓN de feriados (solo ADMIN). Alimentan el cálculo de días hábiles de los plazos.
 */
@Controller
@RequestMapping("/admin/feriados")
public class AdminFeriadoController {

    private final FeriadoService feriadoService;

    public AdminFeriadoController(FeriadoService feriadoService) {
        this.feriadoService = feriadoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("feriados", feriadoService.listar());
        model.addAttribute("feriado", new Feriado());
        return "admin/feriados";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("feriado") Feriado feriado, BindingResult result,
                          Model model, RedirectAttributes flash) {
        if (!feriadoService.guardar(feriado, result)) {
            model.addAttribute("feriados", feriadoService.listar());
            return "admin/feriados";
        }
        flash.addFlashAttribute("mensaje", "Feriado agregado. Los nuevos plazos ya lo saltan.");
        return "redirect:/admin/feriados";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes flash) {
        if (feriadoService.eliminar(id)) {
            flash.addFlashAttribute("mensaje", "Feriado eliminado.");
        } else {
            flash.addFlashAttribute("error", "Ese feriado no existe.");
        }
        return "redirect:/admin/feriados";
    }
}
