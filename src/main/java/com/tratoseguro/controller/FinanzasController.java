package com.tratoseguro.controller;

import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.service.FinanzasService;
import com.tratoseguro.service.PagoService;
import com.tratoseguro.service.PlazoService;
import com.tratoseguro.util.Reloj;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Panel de finanzas (roles FINANZAS y ADMIN): dinero en custodia, comisiones, liberaciones y reembolsos.
 */
@Controller
@RequestMapping("/finanzas")
public class FinanzasController {

    private final FinanzasService finanzasService;
    private final PlazoService plazoService;
    private final PagoService pagoService;

    public FinanzasController(FinanzasService finanzasService, PlazoService plazoService, PagoService pagoService) {
        this.finanzasService = finanzasService;
        this.plazoService = plazoService;
        this.pagoService = pagoService;
    }

    @GetMapping
    public String panel(Model model) {
        model.addAttribute("enCustodia", finanzasService.totalEnCustodia());
        model.addAttribute("retenido", finanzasService.totalPorTipo(TipoMovimiento.RETENCION));
        model.addAttribute("liberado", finanzasService.totalPorTipo(TipoMovimiento.LIBERACION));
        model.addAttribute("comisiones", finanzasService.totalPorTipo(TipoMovimiento.COMISION));
        model.addAttribute("reembolsado", finanzasService.totalPorTipo(TipoMovimiento.REEMBOLSO));
        model.addAttribute("porEstado", finanzasService.tratosPorEstado());
        model.addAttribute("movimientos", finanzasService.movimientosRecientes());
        model.addAttribute("stripeActivo", pagoService.isStripeActivo());
        model.addAttribute("hoy", Reloj.hoy());
        return "finanzas/panel";
    }

    // La revisión de plazos corre sola cada día a las 6:00; este botón la adelanta (útil para la demostración)
    @PostMapping("/plazos")
    public String revisarPlazos(RedirectAttributes flash) {
        int cambios = plazoService.revisar(Reloj.hoy());
        flash.addFlashAttribute("mensaje", cambios == 0
                ? "Revisión de plazos hecha: no había tratos vencidos."
                : "Revisión de plazos hecha: " + cambios + " trato(s) se reembolsaron o liberaron automáticamente.");
        return "redirect:/finanzas";
    }
}
