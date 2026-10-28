package com.tratoseguro.config;

import com.tratoseguro.model.Ciudad;
import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Provincia;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.CiudadRepository;
import com.tratoseguro.repository.ProvinciaRepository;
import com.tratoseguro.repository.TransaccionRepository;
import com.tratoseguro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Carga datos de ejemplo la primera vez que arranca la app (solo si las tablas están vacías).
 * Las cédulas de ejemplo son ficticias, pero cumplen el dígito verificador.
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProvinciaRepository provinciaRepository;
    private final CiudadRepository ciudadRepository;
    private final PasswordEncoder passwordEncoder;

    // En el servidor se cambia con la variable de entorno ADMIN_PASSWORD
    @Value("${tratoseguro.admin.password}")
    private String claveAdmin;

    public DatosIniciales(TransaccionRepository transaccionRepository, UsuarioRepository usuarioRepository,
                          ProvinciaRepository provinciaRepository, CiudadRepository ciudadRepository,
                          PasswordEncoder passwordEncoder) {
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.provinciaRepository = provinciaRepository;
        this.ciudadRepository = ciudadRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (provinciaRepository.count() == 0) {
            cargarProvinciasYCiudades();
        }

        if (usuarioRepository.count() == 0) {
            crearUsuario("admin", claveAdmin, "Administrador", "admin@tratoseguro.ec", "1712345675", Rol.ADMIN, "Quito");
            crearUsuario("carlos", "clave123", "Carlos Mena", "carlos.mena@correo.ec", "1723456784", Rol.VENDEDOR, "Quito");
            crearUsuario("anatorres", "clave123", "Ana Torres", "ana.torres@correo.ec", "0923456784", Rol.COMPRADOR, "Guayaquil");
            crearUsuario("paula", "clave123", "Paula Ríos", "paula.rios@tratoseguro.ec", "0104567896", Rol.MEDIADOR, "Cuenca");
            crearUsuario("finanzas", "clave123", "Diego Salazar", "finanzas@tratoseguro.ec", "1807654320", Rol.FINANZAS, "Ambato");
        }

        if (transaccionRepository.count() == 0) {
            transaccionRepository.save(crearTransaccion("Bicicleta aro 29", "Bicicleta de montaña, poco uso",
                    "200.00", "Carlos Mena", "ana.torres@correo.ec", 5, EstadoTransaccion.CREADA));
            transaccionRepository.save(crearTransaccion("iPhone 13", "128 GB, color negro",
                    "520.00", "Carlos Mena", "ana.torres@correo.ec", 3, EstadoTransaccion.PAGADA));
        }
    }

    // Las 24 provincias del Ecuador (código = dos primeros dígitos de la cédula) y sus ciudades principales
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

    private Transaccion crearTransaccion(String producto, String descripcion, String monto, String vendedor,
                                         String emailComprador, int diasEntrega, EstadoTransaccion estado) {
        Transaccion t = new Transaccion();
        t.setProducto(producto);
        t.setDescripcion(descripcion);
        t.setMonto(new BigDecimal(monto));
        t.setVendedor(vendedor);
        t.setEmailComprador(emailComprador);
        t.setDiasEntrega(diasEntrega);
        t.setEstado(estado);
        t.setFechaCreacion(LocalDateTime.now());
        return t;
    }
}
