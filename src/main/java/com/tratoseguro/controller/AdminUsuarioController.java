package com.tratoseguro.controller;

import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.service.UbicacionService;
import com.tratoseguro.service.UsuarioService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * ADMINISTRACIÓN de usuarios (solo rol ADMIN, ver SecurityConfig).
 * Aquí se crean los compradores, vendedores, mediadores y personal de finanzas que usa el core.
 */
@Controller
@RequestMapping("/admin/usuarios")
public class AdminUsuarioController {

    private final UsuarioService usuarioService;
    private final UbicacionService ubicacionService;

    public AdminUsuarioController(UsuarioService usuarioService, UbicacionService ubicacionService) {
        this.usuarioService = usuarioService;
        this.ubicacionService = ubicacionService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String buscar, Model model) {
        model.addAttribute("usuarios", usuarioService.listar(buscar));
        model.addAttribute("buscar", buscar);
        return "admin/usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        Usuario usuario = new Usuario();
        usuario.setRol(Rol.COMPRADOR);
        model.addAttribute("usuario", usuario);
        cargarListas(model, null, null);
        return "admin/usuario-form";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes flash) {
        Usuario usuario = usuarioService.buscarPorId(id);
        if (usuario == null) {
            flash.addFlashAttribute("error", "Ese usuario no existe.");
            return "redirect:/admin/usuarios";
        }
        model.addAttribute("usuario", usuario);
        Long provinciaId = usuario.getCiudad() == null ? null : usuario.getCiudad().getProvincia().getId();
        Long ciudadId = usuario.getCiudad() == null ? null : usuario.getCiudad().getId();
        cargarListas(model, provinciaId, ciudadId);
        return "admin/usuario-form";
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("usuario") Usuario usuario, BindingResult result,
                          @RequestParam(required = false) Long provinciaId,
                          @RequestParam(required = false) Long ciudadId,
                          @RequestParam(required = false) String clave,
                          Authentication authentication, Model model, RedirectAttributes flash) {
        boolean esNuevo = usuario.getId() == null;
        // El servicio hace las validaciones de negocio (cédula, duplicados, ciudad, contraseña)
        if (!usuarioService.guardar(usuario, clave, ciudadId, authentication.getName(), result)) {
            cargarListas(model, provinciaId, ciudadId);
            return "admin/usuario-form";
        }
        flash.addFlashAttribute("mensaje", esNuevo ? "Usuario creado correctamente." : "Usuario actualizado correctamente.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        if (usuarioService.eliminar(id, authentication.getName())) {
            flash.addFlashAttribute("mensaje", "Usuario eliminado.");
        } else {
            flash.addFlashAttribute("error", "No se pudo eliminar: el usuario no existe o es tu propia cuenta.");
        }
        return "redirect:/admin/usuarios";
    }

    // Dropdown dependiente: devuelve en JSON las ciudades de la provincia escogida
    @GetMapping("/ciudades")
    @ResponseBody
    public List<Map<String, Object>> ciudades(@RequestParam Long provinciaId) {
        return ubicacionService.listarCiudades(provinciaId).stream()
                .map(c -> Map.<String, Object>of("id", c.getId(), "nombre", c.getNombre()))
                .toList();
    }

    // Datos que necesita el formulario: roles, provincias y las ciudades de la provincia escogida
    private void cargarListas(Model model, Long provinciaId, Long ciudadId) {
        model.addAttribute("roles", Rol.values());
        model.addAttribute("provincias", ubicacionService.listarProvincias());
        model.addAttribute("ciudades", provinciaId == null ? List.of() : ubicacionService.listarCiudades(provinciaId));
        model.addAttribute("provinciaId", provinciaId);
        model.addAttribute("ciudadId", ciudadId);
    }
}
