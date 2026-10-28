package com.tratoseguro.service;

import com.tratoseguro.model.Usuario;
import com.tratoseguro.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Spring Security llama a loadUserByUsername() al enviar el login y compara
 * la contraseña escrita con el hash BCrypt guardado en la base.
 */
@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(usuario.getRol())
                .build();
    }

    // Devuelve false si el usuario ya existe
    public boolean registrar(String nombre, String username, String password) {
        if (usuarioRepository.existsByUsername(username)) {
            return false;
        }
        usuarioRepository.save(new Usuario(username, passwordEncoder.encode(password), nombre, "USER"));
        return true;
    }
}
