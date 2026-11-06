package com.tratoseguro.config;

import com.tratoseguro.model.Ciudad;
import com.tratoseguro.model.Decision;
import com.tratoseguro.model.Disputa;
import com.tratoseguro.model.Feriado;
import com.tratoseguro.model.Provincia;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.CiudadRepository;
import com.tratoseguro.repository.FeriadoRepository;
import com.tratoseguro.repository.ProvinciaRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.service.DisputaService;
import com.tratoseguro.service.TransaccionService;
import com.tratoseguro.util.Reloj;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carga datos de ejemplo la primera vez que arranca la app (solo si las tablas están vacías).
 * Los tratos se crean con los mismos servicios que usa la aplicación, así cada uno tiene
 * su historial, sus movimientos de dinero y sus notificaciones.
 * Las cédulas de ejemplo son ficticias, pero cumplen el dígito verificador.
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProvinciaRepository provinciaRepository;
    private final CiudadRepository ciudadRepository;
    private final FeriadoRepository feriadoRepository;
    private final TransaccionService transaccionService;
    private final DisputaService disputaService;
    private final PasswordEncoder passwordEncoder;

    // En el servidor se cambia con la variable de entorno ADMIN_PASSWORD
    @Value("${tratoseguro.admin.password}")
    private String claveAdmin;

    public DatosIniciales(TransaccionRepository transaccionRepository, UsuarioRepository usuarioRepository,
                          ProvinciaRepository provinciaRepository, CiudadRepository ciudadRepository,
                          FeriadoRepository feriadoRepository, TransaccionService transaccionService,
                          DisputaService disputaService, PasswordEncoder passwordEncoder) {
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.provinciaRepository = provinciaRepository;
        this.ciudadRepository = ciudadRepository;
        this.feriadoRepository = feriadoRepository;
        this.transaccionService = transaccionService;
        this.disputaService = disputaService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (provinciaRepository.count() == 0) {
            cargarProvinciasYCiudades();
        }
        if (feriadoRepository.count() == 0) {
            cargarFeriados();
        }
        if (usuarioRepository.count() == 0) {
            crearUsuario("admin", claveAdmin, "Administrador", "admin@tratoseguro.ec", "1712345675", Rol.ADMIN, "Quito");
            crearUsuario("carlos", "clave123", "Carlos Mena", "carlos.mena@correo.ec", "1723456784", Rol.VENDEDOR, "Quito");
            crearUsuario("anatorres", "clave123", "Ana Torres", "ana.torres@correo.ec", "0923456784", Rol.COMPRADOR, "Guayaquil");
            crearUsuario("paula", "clave123", "Paula Ríos", "paula.rios@tratoseguro.ec", "0104567896", Rol.MEDIADOR, "Cuenca");
            crearUsuario("finanzas", "clave123", "Diego Salazar", "finanzas@tratoseguro.ec", "1807654320", Rol.FINANZAS, "Ambato");
        }
        if (transaccionRepository.count() == 0) {
            cargarTratos();
        }
    }

    // ---------- Tratos de ejemplo: uno por cada situación del core ----------

    private void cargarTratos() {
        Usuario carlos = usuarioRepository.findByUsername("carlos").orElseThrow();
        Usuario ana = usuarioRepository.findByUsername("anatorres").orElseThrow();
        Usuario paula = usuarioRepository.findByUsername("paula").orElseThrow();

        // 1. Recién creado: Ana todavía no paga
        crearTrato(carlos, ana, "Bicicleta aro 29", "Bicicleta de montaña, poco uso", "200.00", 5);

        // 2. Pagado: el dinero está retenido y Carlos tiene que enviar
        Transaccion iphone = crearTrato(carlos, ana, "iPhone 13", "128 GB, color negro", "520.00", 3);
        transaccionService.registrarPagoSimulado(iphone.getId(), ana);

        // 3. Enviado con su guía: Ana tiene que confirmar o reclamar
        Transaccion laptop = crearTrato(carlos, ana, "Laptop Lenovo IdeaPad", "15.6 pulgadas, 16 GB de RAM", "750.00", 5);
        transaccionService.registrarPagoSimulado(laptop.getId(), ana);
        transaccionService.registrarEnvio(laptop.getId(), carlos, "Servientrega", "SVT-0049381726");

        // 4. Liberado: caso "Liberación" de la tabla (comisión 6,00 y neto 194,00)
        Transaccion audifonos = crearTrato(carlos, ana, "Audífonos Sony WH-1000XM5", "Con estuche original", "200.00", 4);
        transaccionService.registrarPagoSimulado(audifonos.getId(), ana);
        transaccionService.registrarEnvio(audifonos.getId(), carlos, "Tramaco Express", "TRA-7781023");
        transaccionService.confirmarRecepcion(audifonos.getId(), ana);

        // 5. Disputa abierta con evidencia de ambas partes: la resuelve el mediador
        Transaccion consola = crearTrato(carlos, ana, "Consola PlayStation 5", "Edición con lector de discos", "450.00", 4);
        transaccionService.registrarPagoSimulado(consola.getId(), ana);
        transaccionService.registrarEnvio(consola.getId(), carlos, "Servientrega", "SVT-0051234870");
        disputaService.abrir(consola.getId(), ana, "La consola llegó sin el control y con la caja abierta",
                "Foto del paquete recibido: falta el control DualSense", "https://drive.google.com/ejemplo-foto-paquete");
        disputaService.agregarEvidencia(consola.getId(), carlos,
                "Se envió completa; adjunto la foto del empaque antes de entregarlo a Servientrega", null);

        // 6. Disputa resuelta con reembolso parcial: caso de la tabla (50,00 + 4,50 + 145,50 = 200,00)
        Transaccion chaqueta = crearTrato(carlos, ana, "Chaqueta de cuero", "Talla M, color café", "200.00", 3);
        transaccionService.registrarPagoSimulado(chaqueta.getId(), ana);
        transaccionService.registrarEnvio(chaqueta.getId(), carlos, "Laar Courier", "LAAR-209931");
        Disputa disputaChaqueta = disputaService.abrir(chaqueta.getId(), ana, "La chaqueta tiene una costura rota en la manga",
                "Foto de la costura rota", null);
        disputaService.resolver(disputaChaqueta.getId(), paula, Decision.REEMBOLSO_PARCIAL, new BigDecimal("50.00"), false,
                "La prenda tiene un defecto menor reparable; se reembolsa el costo estimado de la reparación.");

        // 7. Pagado pero el vendedor no envió a tiempo: al revisar plazos se reembolsa todo al comprador
        Transaccion camara = crearTrato(carlos, ana, "Cámara Canon EOS M50", "Con lente 15-45 mm", "300.00", 2);
        transaccionService.registrarPagoSimulado(camara.getId(), ana);
        moverLimiteEnvioAlPasado(camara.getId());

        // 8. Enviado y la compradora no reclamó a tiempo: al revisar plazos se libera el pago al vendedor
        Transaccion reloj = crearTrato(carlos, ana, "Reloj Casio G-Shock", "Modelo GA-2100", "80.00", 2);
        transaccionService.registrarPagoSimulado(reloj.getId(), ana);
        transaccionService.registrarEnvio(reloj.getId(), carlos, "Correos del Ecuador", "CEC-55410078");
        moverLimiteRevisionAlPasado(reloj.getId());
    }

    private Transaccion crearTrato(Usuario vendedor, Usuario comprador, String producto, String descripcion,
                                   String monto, int diasEntrega) {
        Transaccion datos = new Transaccion();
        datos.setProducto(producto);
        datos.setDescripcion(descripcion);
        datos.setMonto(new BigDecimal(monto));
        datos.setDiasEntrega(diasEntrega);
        return transaccionService.guardar(datos, comprador.getId(), vendedor, new BeanPropertyBindingResult(datos, "transaccion"));
    }

    private void moverLimiteEnvioAlPasado(Long id) {
        Transaccion t = transaccionRepository.findById(id).orElseThrow();
        t.setFechaLimiteEnvio(Reloj.hoy().minusDays(2));
        transaccionRepository.save(t);
    }

    private void moverLimiteRevisionAlPasado(Long id) {
        Transaccion t = transaccionRepository.findById(id).orElseThrow();
        t.setFechaLimiteRevision(Reloj.hoy().minusDays(2));
        transaccionRepository.save(t);
    }

    // ---------- Feriados nacionales (fecha del calendario; el administrador registra los traslados) ----------

    private void cargarFeriados() {
        feriado("2026-01-01", "Año Nuevo");
        feriado("2026-02-16", "Carnaval");
        feriado("2026-02-17", "Carnaval");
        feriado("2026-04-03", "Viernes Santo");
        feriado("2026-05-01", "Día del Trabajo");
        feriado("2026-05-24", "Batalla de Pichincha");
        feriado("2026-08-10", "Primer Grito de Independencia");
        feriado("2026-10-09", "Independencia de Guayaquil");
        feriado("2026-11-02", "Día de los Difuntos");
        feriado("2026-11-03", "Independencia de Cuenca");
        feriado("2026-12-25", "Navidad");
        feriado("2027-01-01", "Año Nuevo");
        feriado("2027-02-08", "Carnaval");
        feriado("2027-02-09", "Carnaval");
        feriado("2027-03-26", "Viernes Santo");
        feriado("2027-05-01", "Día del Trabajo");
        feriado("2027-05-24", "Batalla de Pichincha");
        feriado("2027-08-10", "Primer Grito de Independencia");
        feriado("2027-10-09", "Independencia de Guayaquil");
        feriado("2027-11-02", "Día de los Difuntos");
        feriado("2027-11-03", "Independencia de Cuenca");
        feriado("2027-12-25", "Navidad");
    }

    private void feriado(String fecha, String nombre) {
        feriadoRepository.save(new Feriado(LocalDate.parse(fecha), nombre));
    }

    // ---------- Las 24 provincias del Ecuador (código = dos primeros dígitos de la cédula) ----------

    private void cargarProvinciasYCiudades() {
        provincia("01", "Azuay", "Cuenca", "Gualaceo", "Paute");
        provincia("02", "Bolívar", "Guaranda", "San Miguel");
        provincia("03", "Cañar", "Azogues", "La Troncal", "Cañar");
        provincia("04", "Carchi", "Tulcán", "San Gabriel");
        provincia("05", "Cotopaxi", "Latacunga", "La Maná", "Salcedo");
        provincia("06", "Chimborazo", "Riobamba", "Guano", "Alausí");
        provincia("07", "El Oro", "Machala", "Pasaje", "Santa Rosa", "Huaquillas");
        provincia("08", "Esmeraldas", "Esmeraldas", "Atacames", "Quinindé");
        provincia("09", "Guayas", "Guayaquil", "Durán", "Samborondón", "Daule", "Milagro");
        provincia("10", "Imbabura", "Ibarra", "Otavalo", "Cotacachi");
        provincia("11", "Loja", "Loja", "Catamayo", "Macará");
        provincia("12", "Los Ríos", "Babahoyo", "Quevedo", "Ventanas");
        provincia("13", "Manabí", "Portoviejo", "Manta", "Chone", "Jipijapa");
        provincia("14", "Morona Santiago", "Macas", "Sucúa", "Gualaquiza");
        provincia("15", "Napo", "Tena", "Archidona");
        provincia("16", "Pastaza", "Puyo", "Mera");
        provincia("17", "Pichincha", "Quito", "Cayambe", "Sangolquí", "Machachi");
        provincia("18", "Tungurahua", "Ambato", "Baños de Agua Santa", "Pelileo");
        provincia("19", "Zamora Chinchipe", "Zamora", "Yantzaza");
        provincia("20", "Galápagos", "Puerto Baquerizo Moreno", "Puerto Ayora", "Puerto Villamil");
        provincia("21", "Sucumbíos", "Nueva Loja", "Shushufindi");
        provincia("22", "Orellana", "Francisco de Orellana", "La Joya de los Sachas");
        provincia("23", "Santo Domingo de los Tsáchilas", "Santo Domingo", "La Concordia");
        provincia("24", "Santa Elena", "Santa Elena", "La Libertad", "Salinas");
    }

    private void provincia(String codigo, String nombre, String... ciudades) {
        Provincia provincia = provinciaRepository.save(new Provincia(codigo, nombre));
        for (String ciudad : ciudades) {
            ciudadRepository.save(new Ciudad(ciudad, provincia));
        }
    }

    private void crearUsuario(String username, String clave, String nombre, String email, String cedula, Rol rol,
                              String nombreCiudad) {
        Ciudad ciudad = ciudadRepository.findAll().stream()
                .filter(c -> c.getNombre().equals(nombreCiudad))
                .findFirst()
                .orElse(null);
        usuarioRepository.save(new Usuario(username, email, passwordEncoder.encode(clave), nombre, cedula, rol, ciudad));
    }
}
