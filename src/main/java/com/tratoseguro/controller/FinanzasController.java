package com.tratoseguro.controller;

import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.ResumenMensual;
import com.tratoseguro.model.TipoMovimiento;
import com.tratoseguro.service.CobroVendedorService;
import com.tratoseguro.service.FinanzasService;
import com.tratoseguro.service.PagoService;
import com.tratoseguro.service.PlazoService;
import com.tratoseguro.util.Reloj;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel de finanzas (roles FINANZAS y ADMIN): dinero en custodia, comisiones, liberaciones y reembolsos,
 * gráficos mensuales, exportación CSV y transferencias pendientes a vendedores (Stripe Connect).
 */
@Controller
@RequestMapping("/finanzas")
public class FinanzasController {

    private final FinanzasService finanzasService;
    private final PlazoService plazoService;
    private final PagoService pagoService;
    private final CobroVendedorService cobroVendedorService;

    public FinanzasController(FinanzasService finanzasService, PlazoService plazoService, PagoService pagoService,
                              CobroVendedorService cobroVendedorService) {
        this.finanzasService = finanzasService;
        this.plazoService = plazoService;
        this.pagoService = pagoService;
        this.cobroVendedorService = cobroVendedorService;
    }

    @GetMapping
    public String panel(Model model) {
        Map<EstadoTransaccion, Long> porEstado = finanzasService.tratosPorEstado();
        model.addAttribute("enCustodia", finanzasService.totalEnCustodia());
        model.addAttribute("retenido", finanzasService.totalPorTipo(TipoMovimiento.RETENCION));
        model.addAttribute("liberado", finanzasService.totalPorTipo(TipoMovimiento.LIBERACION));
        model.addAttribute("comisiones", finanzasService.totalPorTipo(TipoMovimiento.COMISION));
        model.addAttribute("reembolsado", finanzasService.totalPorTipo(TipoMovimiento.REEMBOLSO));
        model.addAttribute("porEstado", porEstado);
        model.addAttribute("movimientos", finanzasService.movimientosRecientes());
        model.addAttribute("pendientes", cobroVendedorService.pendientes());
        model.addAttribute("stripeActivo", pagoService.isStripeActivo());
        model.addAttribute("hoy", Reloj.hoy());
        model.addAttribute("graficos", datosGraficos(finanzasService.resumenMensual(6), porEstado));
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

    // Stripe Connect: vuelve a intentar las transferencias a vendedores que quedaron pendientes
    @PostMapping("/transferencias")
    public String reintentarTransferencias(RedirectAttributes flash) {
        int completadas = cobroVendedorService.reintentarPendientes(null);
        int siguen = cobroVendedorService.pendientes().size();
        flash.addFlashAttribute("mensaje", completadas + " transferencia(s) completada(s); " + siguen
                + " siguen pendientes (el vendedor aún no activa su cuenta de cobro).");
        return "redirect:/finanzas";
    }

    // Todos los movimientos en CSV para abrirlos en Excel. El BOM hace que Excel lea bien las tildes.
    @GetMapping("/movimientos.csv")
    public ResponseEntity<byte[]> exportarCsv() {
        byte[] contenido = ("﻿" + finanzasService.movimientosCsv()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"movimientos-" + Reloj.hoy() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(contenido);
    }

    // Datos para Chart.js (Thymeleaf los convierte a JSON dentro del script)
    private Map<String, Object> datosGraficos(List<ResumenMensual> meses, Map<EstadoTransaccion, Long> porEstado) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("meses", meses.stream().map(ResumenMensual::etiqueta).toList());
        datos.put("retenido", meses.stream().map(ResumenMensual::retenido).toList());
        datos.put("liberado", meses.stream().map(ResumenMensual::liberado).toList());
        datos.put("reembolsado", meses.stream().map(ResumenMensual::reembolsado).toList());
        datos.put("comisiones", meses.stream().map(ResumenMensual::comisiones).toList());
        List<String> estados = new ArrayList<>();
        List<Long> cantidades = new ArrayList<>();
        porEstado.forEach((estado, cantidad) -> {
            estados.add(estado.getDescripcion());
            cantidades.add(cantidad);
        });
        datos.put("estados", estados);
        datos.put("cantidades", cantidades);
        return datos;
    }
}
