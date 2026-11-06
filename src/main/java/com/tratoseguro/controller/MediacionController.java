package com.tratoseguro.controller;

import com.tratoseguro.model.Decision;
import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.Evidencia;
import com.tratoseguro.model.Liquidacion;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.service.DisputaService;
import com.tratoseguro.service.LiquidacionService;
import com.tratoseguro.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mediación de disputas (solo rol MEDIADOR, ver SecurityConfig).
 */
@Controller
@RequestMapping("/disputas")
public class MediacionController {

    private final DisputaService disputaService;
    private final LiquidacionService liquidacionService;
    private final UsuarioService usuarioService;

    public MediacionController(DisputaService disputaService, LiquidacionService liquidacionService,
                               UsuarioService usuarioService) {
        this.disputaService = disputaService;
        this.liquidacionService = liquidacionService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pendientes", disputaService.pendientes());
        model.addAttribute("resueltas", disputaService.resueltas());
        return "disputas/lista";
    }

    @GetMapping("/{id}/resolver")
    public String formulario(@PathVariable Long id, Model model) {
        Disputa disputa = disputaService.buscar(id);
        Transaccion t = disputa.getTransaccion();

        // Evidencia separada por parte, para compararla lado a lado
        List<Evidencia> delComprador = new ArrayList<>();
        List<Evidencia> delVendedor = new ArrayList<>();
        for (Evidencia evidencia : disputaService.evidencias(id)) {
            if (t.esComprador(evidencia.getUsuario())) {
                delComprador.add(evidencia);
            } else {
                delVendedor.add(evidencia);
            }
        }

        model.addAttribute("disputa", disputa);
        model.addAttribute("t", t);
        model.addAttribute("evidenciasComprador", delComprador);
        model.addAttribute("evidenciasVendedor", delVendedor);
        model.addAttribute("decisiones", Decision.values());
        // Liquidación de referencia de cada caso (la definitiva la vuelve a calcular el servidor al guardar)
        model.addAttribute("liqVendedor", liquidacionService.calcular(t.getMonto(), BigDecimal.ZERO));
        model.addAttribute("liqTotal", liquidacionService.calcular(t.getMonto(), t.getMonto()));
        model.addAttribute("porcentajeComision", LiquidacionService.PORCENTAJE_COMISION);
        return "disputas/resolver";
    }

    @PostMapping("/{id}/resolver")
    public String resolver(@PathVariable Long id, @RequestParam(required = false) Decision decision,
                           @RequestParam(required = false) BigDecimal reembolso,
                           @RequestParam(defaultValue = "false") boolean viaRapida,
                           @RequestParam(required = false) String justificacion,
                           Authentication authentication, RedirectAttributes flash) {
        Liquidacion liquidacion = disputaService.resolver(id, usuarioService.buscarPorUsername(authentication.getName()),
                decision, reembolso, viaRapida, justificacion);
        flash.addFlashAttribute("mensaje", "Disputa resuelta: reembolso $ " + liquidacion.getReembolso()
                + ", comisión $ " + liquidacion.getComision() + ", neto al vendedor $ " + liquidacion.getNetoVendedor() + ".");
        return "redirect:/disputas";
    }
}
