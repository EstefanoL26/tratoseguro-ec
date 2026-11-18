package com.tratoseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * MODELO: archivo (foto o PDF) adjunto a una evidencia (EVIDENCIA 1 : 0..1 ARCHIVO_EVIDENCIA).
 * Se guarda en la base porque el disco de Render se borra en cada despliegue.
 * Está en su propia tabla para que listar evidencias no cargue los bytes.
 */
@Entity
@Table(name = "archivo_evidencia")
public class ArchivoEvidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_evidencia", nullable = false, unique = true)
    private Evidencia evidencia;

    // Nombre original saneado (solo para mostrarlo y como nombre de descarga)
    @Column(nullable = false, length = 150)
    private String nombre;

    // Tipo comprobado por el contenido del archivo (firma de bytes), no por lo que diga el navegador
    @Column(name = "tipo_contenido", nullable = false, length = 50)
    private String tipoContenido;

    @Column(nullable = false)
    private Integer tamano;

    // Huella SHA-256: permite comprobar que el archivo no se alteró después de subirlo
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private byte[] datos;

    public ArchivoEvidencia() {
    }

    public ArchivoEvidencia(Evidencia evidencia, String nombre, String tipoContenido, byte[] datos, String sha256) {
        this.evidencia = evidencia;
        this.nombre = nombre;
        this.tipoContenido = tipoContenido;
        this.datos = datos;
        this.tamano = datos.length;
        this.sha256 = sha256;
    }

    public Long getId() {
        return id;
    }

    public Evidencia getEvidencia() {
        return evidencia;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipoContenido() {
        return tipoContenido;
    }

    public Integer getTamano() {
        return tamano;
    }

    public String getSha256() {
        return sha256;
    }

    public byte[] getDatos() {
        return datos;
    }
}
