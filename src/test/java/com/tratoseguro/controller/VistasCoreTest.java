package com.tratoseguro.controller;

import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.DisputaRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/**
 * Renderiza cada pantalla del core con el rol que corresponde (así un error de Thymeleaf
 * aparece en las pruebas) y comprueba los permisos por rol y por participación.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VistasCoreTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TransaccionRepository transaccionRepository;
    @Autowired private DisputaRepository disputaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Transaccion trato(String producto) {
        return transaccionRepository.findAll().stream()
                .filter(t -> t.getProducto().equals(producto)).findFirst().orElseThrow();
    }

    @Test
    void elCompradorVeSusTratosYElDetalleConSuHistorial() throws Exception {
        mockMvc.perform(get("/transacciones").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bicicleta aro 29")));

        Transaccion laptop = trato("Laptop Lenovo IdeaPad");
        mockMvc.perform(get("/transacciones/" + laptop.getId()).with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar recepción")))
                .andExpect(content().string(containsString("SVT-0049381726")))
                .andExpect(content().string(containsString("Historial")));
    }

    @Test
    void elDetalleDeUnTratoLiberadoMuestraLaLiquidacionQueCuadra() throws Exception {
        Transaccion audifonos = trato("Audífonos Sony WH-1000XM5");
        mockMvc.perform(get("/transacciones/" + audifonos.getId()).with(user("carlos").roles("VENDEDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$ 194,00")))
                .andExpect(content().string(containsString("Cuadra con el monto retenido")));
    }

    @Test
    void pantallasDelVendedorYDelComprador() throws Exception {
        Transaccion bici = trato("Bicicleta aro 29");
        mockMvc.perform(get("/transacciones/nueva").with(user("carlos").roles("VENDEDOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/transacciones/editar/" + bici.getId()).with(user("carlos").roles("VENDEDOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/transacciones/" + bici.getId() + "/pago").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pago de prueba: no se realiza ningún cobro real")));

        Transaccion iphone = trato("iPhone 13");
        mockMvc.perform(get("/transacciones/" + iphone.getId() + "/disputa").with(user("anatorres").roles("COMPRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/notificaciones").with(user("anatorres").roles("COMPRADOR"))).andExpect(status().isOk());
    }

    @Test
    void pantallasDelMediadorFinanzasYAdmin() throws Exception {
        Disputa pendiente = disputaRepository.findByDecisionIsNullOrderByFechaAperturaAsc().get(0);
        mockMvc.perform(get("/disputas").with(user("paula").roles("MEDIADOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/disputas/" + pendiente.getId() + "/resolver").with(user("paula").roles("MEDIADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vía rápida")));
        mockMvc.perform(get("/finanzas").with(user("finanzas").roles("FINANZAS")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cuadra")));
        mockMvc.perform(get("/admin/feriados").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Independencia de Guayaquil")));
    }

    @Test
    void cadaRolSoloEntraASuSeccion() throws Exception {
        mockMvc.perform(get("/disputas").with(user("anatorres").roles("COMPRADOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/finanzas").with(user("carlos").roles("VENDEDOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/feriados").with(user("paula").roles("MEDIADOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/transacciones/nueva").with(user("anatorres").roles("COMPRADOR"))).andExpect(status().isForbidden());
    }

    @Test
    void unaPersonaAjenaNoPuedeVerUnTrato() throws Exception {
        if (usuarioRepository.findByUsername("ajeno").isEmpty()) {
            usuarioRepository.save(new Usuario("ajeno", "ajeno@correo.ec", passwordEncoder.encode("clave123"),
                    "Persona Ajena", "1102345673", Rol.COMPRADOR, null));
        }
        Transaccion bici = trato("Bicicleta aro 29");
        mockMvc.perform(get("/transacciones/" + bici.getId()).with(user("ajeno").roles("COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elVendedorNoPuedeConfirmarLaRecepcion() throws Exception {
        Transaccion laptop = trato("Laptop Lenovo IdeaPad");
        mockMvc.perform(post("/transacciones/" + laptop.getId() + "/confirmar").with(user("carlos").roles("VENDEDOR")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elWebhookSinConfiguracionSeRechaza() throws Exception {
        mockMvc.perform(post("/stripe/webhook").content("{}").header("Stripe-Signature", "t=1,v1=falsa"))
                .andExpect(status().isBadRequest());
    }
}
