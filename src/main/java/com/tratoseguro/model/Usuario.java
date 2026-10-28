package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * MODELO: usuario del sistema (comprador, vendedor, mediador, finanzas o administrador).
 * La contraseña se guarda cifrada con BCrypt y la cédula se valida en el servicio antes de guardar.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El usuario es obligatorio")
    @Size(min = 4, max = 50, message = "El usuario debe tener entre 4 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._]*$", message = "Solo letras, números, punto y guion bajo")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Escribe un correo válido")
    @Size(max = 100, message = "Máximo 100 caracteres")
    @Column(length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "Máximo 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "La cédula es obligatoria")
    @Column(unique = true, length = 10)
    private String cedula;

    @NotNull(message = "Escoge un rol")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Rol rol;

    // Ciudad donde vive el usuario (de aquí sale su provincia). Se usa para los envíos del core.
    @ManyToOne
    @JoinColumn(name = "id_ciudad")
    private Ciudad ciudad;

    public Usuario() {
    }

    public Usuario(String username, String email, String password, String nombre, String cedula, Rol rol, Ciudad ciudad) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.nombre = nombre;
        this.cedula = cedula;
        this.rol = rol;
        this.ciudad = ciudad;
    }

    public boolean tieneRol(Rol rol) {
        return this.rol == rol;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String cedula) {
        this.cedula = cedula;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Ciudad getCiudad() {
        return ciudad;
    }

    public void setCiudad(Ciudad ciudad) {
        this.ciudad = ciudad;
    }
}
