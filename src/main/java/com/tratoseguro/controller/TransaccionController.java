package com.tratoseguro.controller;

import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.DisputaService;
import com.tratoseguro.service.LiquidacionService;
import com.tratoseguro.service.PagoService;
import com.tratoseguro.service.ReglaNegocioException;
import com.tratoseguro.service.TransaccionService;
import com.tratoseguro.service.UsuarioService;
import com.tratoseguro.util.Reloj;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * CONTROLADOR del core: recibe la petición, llama al servicio y elige la vista.
 * Las reglas (quién puede hacer qué y en qué estado) están en los servicios.
 * Si un servicio lanza ReglaNegocioException, ManejoErrores muestra el mensaje al usuario.
 */
@Controller
@RequestMapping("/transacciones")
public class TransaccionController {

    // Transportistas que operan en Ecuador (dropdown del formulario de envío)
    static final List<String> TRANSPORTISTAS = List.of(
            "Servientrega", "Tramaco Express", "Laar Courier", "Correos del Ecuador", "Urbano Express");

    private final TransaccionService transaccionService;
    private final DisputaService disputaService;
    private final LiquidacionService liquidacionService;
    private final PagoService pagoService;
    private final UsuarioService usuarioService;

    public TransaccionController(TransaccionService transaccionService, DisputaService disputaService,
                                 LiquidacionService liquidacionService, PagoService pagoService,
                                 UsuarioService usuarioService) {
        this.transaccionService = transaccionService;
        this.disputaService = disputaService;
        this.liquidacionService = liquidacionService;
        this.pagoService = pagoService;
        this.usuarioService = usuarioService;
    }

    // ---------- Lista y detalle ----------

    @GetMapping
    public String listar(@RequestParam(required = false) String buscar,
                         @RequestParam(required = false) EstadoTransaccion estado,
                         Authentication authentication, Model model) {
        Usuario usuario = usuarioActual(authentication);
        model.addAttribute("transacciones", transaccionService.listarPara(usuario, buscar, estado));
        model.addAttribute("estados", EstadoTransaccion.values());
        model.addAttribute("buscar", buscar);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("usuario", usuario);
        return "transacciones/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Authentication authentication, Model model) {
        Usuario usuario = usuarioActual(authentication);
        Transaccion transaccion = transaccionService.obtenerParaVer(id, usuario);
        Disputa disputa = disputaService.buscarPorTransaccion(id);

        model.addAttribute("t", transaccion);
        model.addAttribute("usuario", usuario);
        model.addAttribute("esComprador", transaccion.esComprador(usuario));
        model.addAttribute("esVendedor", transaccion.esVendedor(usuario));
        model.addAttribute("envio", transaccionService.buscarEnvio(id));
        model.addAttribute("historial", transaccionService.historial(id));
        model.addAttribute("movimientos", liquidacionService.movimientos(id));
        model.addAttribute("disputa", disputa);
        model.addAttribute("evidencias", disputa == null ? List.of() : disputaService.evidencias(disputa.getId()));
        model.addAttribute("transportistas", TRANSPORTISTAS);
        model.addAttribute("diasParaEnviar", diasHasta(transaccion.getFechaLimiteEnvio()));
        model.addAttribute("diasParaRevisar", diasHasta(transaccion.getFechaLimiteRevision()));
        return "transacciones/detalle";
    }

    // ---------- Crear, editar y eliminar (vendedor, solo en CREADA) ----------

    @GetMapping("/nueva")
    public String nueva(Authentication authentication, Model model) {
        exigirVendedor(usuarioActual(authentication));
        model.addAttribute("transaccion", new Transaccion());
        cargarFormulario(model, null);
        return "transacciones/formulario";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Authentication authentication, Model model, RedirectAttributes flash) {
        Usuario usuario = usuarioActual(authentication);
        Transaccion transaccion = transaccionService.obtenerParaVer(id, usuario);
        if (!transaccion.esVendedor(usuario) || !transaccion.isEditable()) {
            flash.addFlashAttribute("error", "Solo el vendedor puede editar el trato mientras está en estado Creada.");
            return "redirect:/transacciones/" + id;
        }
        model.addAttribute("transaccion", transaccion);
        cargarFormulario(model, transaccion.getComprador().getId());
        return "transacciones/formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("transaccion") Transaccion transaccion, BindingResult result,
                          @RequestParam(required = false) Long compradorId,
                          Authentication authentication, Model model, RedirectAttributes flash) {
        boolean esNuevo = transaccion.getId() == null;
        Transaccion guardada = result.hasErrors() ? null
                : transaccionService.guardar(transaccion, compradorId, usuarioActual(authentication), result);
        if (guardada == null) {
            cargarFormulario(model, compradorId);
            return "transacciones/formulario";
        }
        flash.addFlashAttribute("mensaje", esNuevo ? "Trato creado. Avisamos al comprador para que lo pague." : "Trato actualizado.");
        return "redirect:/transacciones/" + guardada.getId();
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        transaccionService.eliminar(id, usuarioActual(authentication));
        flash.addFlashAttribute("mensaje", "Trato eliminado.");
        return "redirect:/transacciones";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        transaccionService.cancelar(id, usuarioActual(authentication));
        flash.addFlashAttribute("mensaje", "Trato cancelado.");
        return "redirect:/transacciones/" + id;
    }

    // ---------- Pago (comprador) ----------

    @GetMapping("/{id}/pago")
    public String pago(@PathVariable Long id, @RequestParam(required = false) String cancelado,
                       Authentication authentication, Model model, RedirectAttributes flash) {
        Usuario usuario = usuarioActual(authentication);
        Transaccion transaccion = transaccionService.obtenerParaVer(id, usuario);
        if (!transaccion.esComprador(usuario) || transaccion.getEstado() != EstadoTransaccion.CREADA) {
            flash.addFlashAttribute("error", "Este trato no está pendiente de tu pago.");
            return "redirect:/transacciones/" + id;
        }
        model.addAttribute("t", transaccion);
        model.addAttribute("stripeActivo", pagoService.isStripeActivo());
        model.addAttribute("cancelado", cancelado != null);
        return "transacciones/pago";
    }

    @PostMapping("/{id}/pagar")
    public String pagar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean acepto,
                        Authentication authentication, RedirectAttributes flash) {
        if (!acepto) {
            flash.addFlashAttribute("error", "Para continuar acepta las condiciones del pago protegido.");
            return "redirect:/transacciones/" + id + "/pago";
        }
        String urlBase = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        String urlStripe = transaccionService.iniciarPago(id, usuarioActual(authentication), urlBase);
        if (urlStripe != null) {
            // Stripe Checkout: el comprador paga con una tarjeta de prueba y Stripe lo devuelve a /pago/exito
            return "redirect:" + urlStripe;
        }
        flash.addFlashAttribute("mensaje", "Pago simulado registrado: el dinero quedó retenido hasta que confirmes la recepción.");
        return "redirect:/transacciones/" + id;
    }

    // Stripe redirige aquí después de pagar. El servidor vuelve a consultar a Stripe antes de marcarlo como pagado.
    @GetMapping("/{id}/pago/exito")
    public String pagoExitoso(@PathVariable Long id, @RequestParam("session_id") String sessionId,
                              Authentication authentication, RedirectAttributes flash) {
        transaccionService.obtenerParaVer(id, usuarioActual(authentication));
        Transaccion transaccion = transaccionService.confirmarPagoStripe(sessionId);
        if (transaccion == null || !transaccion.getId().equals(id)) {
            throw new ReglaNegocioException("El pago de Stripe no corresponde a este trato.");
        }
        flash.addFlashAttribute("mensaje", "Pago con Stripe confirmado: el dinero quedó retenido hasta que confirmes la recepción.");
        return "redirect:/transacciones/" + id;
    }

    // ---------- Envío (vendedor) y confirmación (comprador) ----------

    @PostMapping("/{id}/envio")
    public String registrarEnvio(@PathVariable Long id, @RequestParam String transportista, @RequestParam String guia,
                                 Authentication authentication, RedirectAttributes flash) {
        transaccionService.registrarEnvio(id, usuarioActual(authentication), transportista, guia);
        flash.addFlashAttribute("mensaje", "Envío registrado. Avisamos al comprador.");
        return "redirect:/transacciones/" + id;
    }

    @PostMapping("/{id}/confirmar")
    public String confirmar(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        transaccionService.confirmarRecepcion(id, usuarioActual(authentication));
        flash.addFlashAttribute("mensaje", "Recepción confirmada: se liberó el pago al vendedor.");
        return "redirect:/transacciones/" + id;
    }

    // ---------- Disputa (comprador) y evidencia (ambas partes) ----------

    @GetMapping("/{id}/disputa")
    public String formularioDisputa(@PathVariable Long id, Authentication authentication, Model model, RedirectAttributes flash) {
        Usuario usuario = usuarioActual(authentication);
        Transaccion transaccion = transaccionService.obtenerParaVer(id, usuario);
        boolean puedeAbrir = transaccion.esComprador(usuario)
                && transaccion.getEstado().puedeCambiarA(EstadoTransaccion.EN_DISPUTA);
        if (!puedeAbrir) {
            flash.addFlashAttribute("error", "Solo el comprador puede abrir una disputa mientras el trato está pagado o enviado.");
            return "redirect:/transacciones/" + id;
        }
        model.addAttribute("t", transaccion);
        return "transacciones/disputa";
    }

    @PostMapping("/{id}/disputa")
    public String abrirDisputa(@PathVariable Long id, @RequestParam String motivo,
                               @RequestParam(required = false) String evidencia,
                               @RequestParam(required = false) String enlace,
                               Authentication authentication, RedirectAttributes flash) {
        disputaService.abrir(id, usuarioActual(authentication), motivo, evidencia, enlace);
        flash.addFlashAttribute("mensaje", "Disputa abierta. Un mediador revisará la evidencia; los plazos quedan pausados.");
        return "redirect:/transacciones/" + id;
    }

    @PostMapping("/{id}/evidencia")
    public String agregarEvidencia(@PathVariable Long id, @RequestParam String descripcion,
                                   @RequestParam(required = false) String enlace,
                                   Authentication authentication, RedirectAttributes flash) {
        disputaService.agregarEvidencia(id, usuarioActual(authentication), descripcion, enlace);
        flash.addFlashAttribute("mensaje", "Evidencia agregada a la disputa.");
        return "redirect:/transacciones/" + id;
    }

    // ---------- Ayudantes ----------

    // Días que faltan para un plazo (negativo si ya venció); null si el plazo todavía no existe
    private Long diasHasta(LocalDate limite) {
        return limite == null ? null : ChronoUnit.DAYS.between(Reloj.hoy(), limite);
    }

    private void cargarFormulario(Model model, Long compradorId) {
        model.addAttribute("compradores", transaccionService.compradoresDisponibles());
        model.addAttribute("compradorId", compradorId);
    }

    private void exigirVendedor(Usuario usuario) {
        if (!usuario.tieneRol(Rol.VENDEDOR)) {
            throw new AccessDeniedException("Solo un vendedor puede crear tratos.");
        }
    }

    private Usuario usuarioActual(Authentication authentication) {
        return usuarioService.buscarPorUsername(authentication.getName());
    }
}
