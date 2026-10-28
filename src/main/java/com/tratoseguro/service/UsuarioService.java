package com.tratoseguro.service;

import com.tratoseguro.model.Ciudad;
import com.tratoseguro.model.Rol;
import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.CiudadRepository;
import com.tratoseguro.repository.UsuarioRepository;
import com.tratoseguro.util.ValidadorCedula;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Lógica de negocio de los usuarios: login, registro y administración.
 * Aquí se valida la cédula (dato sensible) ANTES de guardar en la base de datos.
 */
@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final CiudadRepository ciudadRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, CiudadRepository ciudadRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.ciudadRepository = ciudadRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(usuario.getRol().name())
                .build();
    }

    // ---------- Registro público ----------

    // Devuelve null si se registró, o el mensaje de error
    @Transactional
    public String registrar(String nombre, String username, String email, String cedula, String password,
                            String rolElegido) {
        String errorCedula = validarCedula(cedula, null);
        if (errorCedula != null) {
            return errorCedula;
        }
        if (usuarioRepository.existsByUsername(username)) {
            return "Ese usuario ya existe.";
        }
        // Por seguridad, al registrarse solo se puede ser comprador o vendedor.
        // Los roles MEDIADOR, FINANZAS y ADMIN solo los asigna el administrador.
        Rol rol = "VENDEDOR".equals(rolElegido) ? Rol.VENDEDOR : Rol.COMPRADOR;
        usuarioRepository.save(new Usuario(username, email, passwordEncoder.encode(password), nombre, cedula, rol, null));
        return null;
    }

    // ---------- Administración ----------

    public List<Usuario> listar(String buscar) {
        if (buscar == null || buscar.isBlank()) {
            return usuarioRepository.findAllByOrderByIdAsc();
        }
        String texto = buscar.trim();
        return usuarioRepository.findByNombreContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrCedulaContainingOrderByIdAsc(
                texto, texto, texto);
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id).orElse(null);
    }

    /**
     * Crea o edita un usuario desde el panel de administración.
     * Los errores se marcan en "errores" para que la vista los muestre junto a cada campo.
     * Devuelve true si se guardó.
     */
    @Transactional
    public boolean guardar(Usuario datos, String clave, Long ciudadId, String usernameActual, Errors errores) {
        boolean esNuevo = datos.getId() == null;

        // En una edición se vuelve a buscar en la base para no confiar en lo que llega del formulario
        Usuario usuario = esNuevo ? new Usuario() : buscarPorId(datos.getId());
        if (usuario == null) {
            errores.reject("noExiste", "El usuario ya no existe.");
            return false;
        }

        String cedula = datos.getCedula() == null ? "" : datos.getCedula().trim();
        String username = datos.getUsername() == null ? "" : datos.getUsername().trim();

        // 1. Cédula: dato sensible. Se valida aquí, en el servidor, aunque el navegador no tenga JavaScript.
        if (!errores.hasFieldErrors("cedula")) {
            String errorCedula = validarCedula(cedula, usuario.getId());
            if (errorCedula != null) {
                errores.rejectValue("cedula", "cedulaInvalida", errorCedula);
            }
        }

        // 2. El username no se puede repetir
        Usuario otro = usuarioRepository.findByUsername(username).orElse(null);
        if (otro != null && !otro.getId().equals(usuario.getId())) {
            errores.rejectValue("username", "duplicado", "Ese usuario ya está registrado.");
        }

        // 3. La ciudad debe existir en el catálogo (llega del dropdown, pero se comprueba igual)
        Ciudad ciudad = ciudadId == null ? null : ciudadRepository.findById(ciudadId).orElse(null);
        if (ciudad == null) {
            errores.rejectValue("ciudad", "requerida", "Escoge una provincia y una ciudad.");
        }

        // 4. Contraseña: obligatoria al crear; al editar, vacía significa "no cambiar"
        boolean cambiaClave = clave != null && !clave.isBlank();
        if (esNuevo && !cambiaClave) {
            errores.reject("claveRequerida", "La contraseña es obligatoria para un usuario nuevo.");
        } else if (cambiaClave && clave.length() < 6) {
            errores.reject("claveCorta", "La contraseña debe tener al menos 6 caracteres.");
        }

        // 5. El administrador no puede quitarse a sí mismo el rol ADMIN (se quedaría sin acceso)
        if (!esNuevo && usuario.getUsername().equals(usernameActual) && datos.getRol() != Rol.ADMIN) {
            errores.rejectValue("rol", "propioRol", "No puedes quitarte tu propio rol de administrador.");
        }

        if (errores.hasErrors()) {
            return false;
        }

        // Todo correcto: se copian solo los campos permitidos
        usuario.setNombre(datos.getNombre().trim());
        usuario.setUsername(username);
        usuario.setEmail(datos.getEmail().trim());
        usuario.setCedula(cedula);
        usuario.setRol(datos.getRol());
        usuario.setCiudad(ciudad);
        if (cambiaClave) {
            usuario.setPassword(passwordEncoder.encode(clave));
        }
        usuarioRepository.save(usuario);
        return true;
    }

    // No se permite eliminar la propia cuenta
    @Transactional
    public boolean eliminar(Long id, String usernameActual) {
        Usuario usuario = buscarPorId(id);
        if (usuario == null || usuario.getUsername().equals(usernameActual)) {
            return false;
        }
        usuarioRepository.delete(usuario);
        return true;
    }

    // Devuelve null si la cédula es válida y no está repetida, o el mensaje de error
    private String validarCedula(String cedula, Long idUsuario) {
        if (!ValidadorCedula.esValida(cedula)) {
            return "La cédula no es válida: deben ser 10 dígitos y cumplir el dígito verificador.";
        }
        Usuario duenio = usuarioRepository.findByCedula(cedula).orElse(null);
        if (duenio != null && !duenio.getId().equals(idUsuario)) {
            return "Esa cédula ya está registrada con otro usuario.";
        }
        return null;
    }
}
