package com.tratoseguro.controller;

import com.tratoseguro.model.Transaccion;
import com.tratoseguro.service.TransaccionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * CONTROLADOR del CRUD de tratos: recibe la petición, llama al servicio y elige la vista.
 */
@Controller
@RequestMapping("/transacciones")
public class TransaccionController {

    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String buscar, Model model) {
        model.addAttribute("transacciones", transaccionService.listar(buscar));
        model.addAttribute("buscar", buscar);
        return "lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("transaccion", new Transaccion());
        return "formulario";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes flash) {
        Transaccion transaccion = transaccionService.buscarPorId(id);
        if (transaccion == null || !transaccion.isEditable()) {
            flash.addFlashAttribute("error", "Ese trato no existe o ya no se puede editar.");
            return "redirect:/transacciones";
        }
        model.addAttribute("transaccion", transaccion);
        return "formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("transaccion") Transaccion transaccion, BindingResult result,
                          RedirectAttributes flash) {
        if (result.hasErrors()) {
            return "formulario";
        }
        if (transaccionService.guardar(transaccion)) {
            flash.addFlashAttribute("mensaje", "Trato guardado correctamente.");
        } else {
            flash.addFlashAttribute("error", "El trato ya no se puede editar porque no está en estado CREADA.");
        }
        return "redirect:/transacciones";
    }

    // POST y no GET para que un simple enlace no pueda borrar datos
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes flash) {
        if (transaccionService.eliminar(id)) {
            flash.addFlashAttribute("mensaje", "Trato eliminado.");
        } else {
            flash.addFlashAttribute("error", "Solo se pueden eliminar tratos en estado CREADA.");
        }
        return "redirect:/transacciones";
    }
}
