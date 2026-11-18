package com.tratoseguro.repository;

import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByCedula(String cedula);

    boolean existsByUsername(String username);

    // Webhook de Stripe Connect: Stripe avisa por el id de la cuenta conectada (acct_...)
    Optional<Usuario> findByStripeCuentaId(String stripeCuentaId);

    List<Usuario> findAllByOrderByIdAsc();

    // Para los dropdowns (por ejemplo, escoger el comprador de un trato)
    List<Usuario> findByRolOrderByNombreAsc(Rol rol);

    // Búsqueda del administrador por nombre, usuario o cédula
    List<Usuario> findByNombreContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrCedulaContainingOrderByIdAsc(
            String nombre, String username, String cedula);
}
