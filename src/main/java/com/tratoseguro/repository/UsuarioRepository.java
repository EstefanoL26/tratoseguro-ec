package com.tratoseguro.repository;

import com.tratoseguro.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByCedula(String cedula);

    boolean existsByUsername(String username);

    List<Usuario> findAllByOrderByIdAsc();

    // Búsqueda del administrador por nombre, usuario o cédula
    List<Usuario> findByNombreContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrCedulaContainingOrderByIdAsc(
            String nombre, String username, String cedula);
}
