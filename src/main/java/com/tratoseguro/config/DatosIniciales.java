package com.tratoseguro.config;

import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.repository.TransaccionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Carga datos de ejemplo la primera vez que arranca la app (solo si las tablas están vacías).
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private final TransaccionRepository transaccionRepository;

    public DatosIniciales(TransaccionRepository transaccionRepository) {
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    public void run(String... args) {
        if (transaccionRepository.count() == 0) {
            transaccionRepository.save(crearTransaccion("Bicicleta aro 29", "Bicicleta de montaña, poco uso",
                    "200.00", "Carlos Mena", "ana.torres@correo.ec", 5, EstadoTransaccion.CREADA));
            transaccionRepository.save(crearTransaccion("iPhone 13", "128 GB, color negro",
                    "520.00", "Carlos Mena", "ana.torres@correo.ec", 3, EstadoTransaccion.PAGADA));
        }
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
