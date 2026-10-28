package com.tratoseguro.service;

import com.tratoseguro.model.EstadoTransaccion;
import com.tratoseguro.model.Transaccion;
import com.tratoseguro.repository.TransaccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lógica de negocio de los tratos. Las reglas se validan aquí y no solo ocultando botones en la vista.
 */
@Service
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;

    public TransaccionService(TransaccionRepository transaccionRepository) {
        this.transaccionRepository = transaccionRepository;
    }

    public List<Transaccion> listar(String buscar) {
        if (buscar == null || buscar.isBlank()) {
            return transaccionRepository.findAll();
        }
        return transaccionRepository.findByProductoContainingIgnoreCase(buscar.trim());
    }

    public Transaccion buscarPorId(Long id) {
        return transaccionRepository.findById(id).orElse(null);
    }

    // Crea o edita. Devuelve false si el trato ya no se puede editar.
    @Transactional
    public boolean guardar(Transaccion transaccion) {
        if (transaccion.getId() == null) {
            // Trato nuevo: siempre empieza en CREADA
            transaccion.setEstado(EstadoTransaccion.CREADA);
            transaccion.setFechaCreacion(LocalDateTime.now());
            transaccionRepository.save(transaccion);
            return true;
        }

        // Edición: se vuelve a buscar en la base para no confiar en lo que llega del formulario
        Transaccion original = buscarPorId(transaccion.getId());
        if (original == null || !original.isEditable()) {
            return false;
        }
        transaccion.setEstado(original.getEstado());
        transaccion.setFechaCreacion(original.getFechaCreacion());
        transaccionRepository.save(transaccion);
        return true;
    }

    // Solo se elimina si está en CREADA
    @Transactional
    public boolean eliminar(Long id) {
        Transaccion transaccion = buscarPorId(id);
        if (transaccion == null || !transaccion.isEditable()) {
            return false;
        }
        transaccionRepository.delete(transaccion);
        return true;
    }
}
