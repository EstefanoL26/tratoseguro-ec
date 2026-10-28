package com.tratoseguro.controller;

import com.tratoseguro.model.Ciudad;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.CiudadRepository;
import com.tratoseguro.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del módulo de administración. Las peticiones se envían directo al servidor,
 * sin navegador ni JavaScript, igual que lo haría alguien que intenta saltarse el formulario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CiudadRepository ciudadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void sinSesionRedirigeAlLogin() throws Exception {
        mockMvc.perform(get("/admin/usuarios"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void unCompradorNoPuedeEntrarALaAdministracion() throws Exception {
        mockMvc.perform(get("/admin/usuarios").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdminSiPuedeEntrar() throws Exception {
        mockMvc.perform(get("/admin/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void noSeGuardaUnUsuarioConCedulaInvalida() throws Exception {
        Long ciudadId = ciudadRepository.findAll().get(0).getId();

        mockMvc.perform(post("/admin/usuarios/guardar").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("nombre", "Usuario Falso")
                        .param("cedula", "1234567890")
                        .param("username", "falso")
                        .param("email", "falso@correo.ec")
                        .param("rol", "VENDEDOR")
                        .param("clave", "clave123")
                        .param("ciudadId", ciudadId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("usuario", "cedula"));

        assertTrue(usuarioRepository.findByUsername("falso").isEmpty());
    }

    @Test
    void noSeGuardaSinCiudadAunqueSeSalteElDropdown() throws Exception {
        mockMvc.perform(post("/admin/usuarios/guardar").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("nombre", "Sin Ciudad")
                        .param("cedula", "1309876546")
                        .param("username", "sinciudad")
                        .param("email", "sinciudad@correo.ec")
                        .param("rol", "COMPRADOR")
                        .param("clave", "clave123")
                        .param("ciudadId", "99999"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("usuario", "ciudad"));

        assertTrue(usuarioRepository.findByUsername("sinciudad").isEmpty());
    }

    @Test
    void seGuardaUnUsuarioValidoConLaClaveCifrada() throws Exception {
        Ciudad ciudad = ciudadRepository.findAll().get(0);

        mockMvc.perform(post("/admin/usuarios/guardar").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("nombre", "Luis Andrade")
                        .param("cedula", "1709876542")
                        .param("username", "landrade")
                        .param("email", "luis.andrade@correo.ec")
                        .param("rol", "MEDIADOR")
                        .param("clave", "clave123")
                        .param("ciudadId", ciudad.getId().toString()))
                .andExpect(redirectedUrl("/admin/usuarios"));

        Usuario guardado = usuarioRepository.findByUsername("landrade").orElseThrow();
        assertEquals("1709876542", guardado.getCedula());
        assertEquals(ciudad.getId(), guardado.getCiudad().getId());
        assertTrue(passwordEncoder.matches("clave123", guardado.getPassword()));
    }

    @Test
    void elDropdownDependienteDevuelveSoloLasCiudadesDeLaProvincia() throws Exception {
        Ciudad quito = ciudadRepository.findAll().stream()
                .filter(c -> c.getNombre().equals("Quito"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/admin/usuarios/ciudades").with(user("admin").roles("ADMIN"))
                        .param("provinciaId", quito.getProvincia().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.nombre == 'Quito')]").exists());
    }
}
