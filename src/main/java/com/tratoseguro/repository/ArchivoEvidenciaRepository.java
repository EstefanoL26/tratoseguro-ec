package com.tratoseguro.repository;

import com.tratoseguro.model.ArchivoEvidencia;
import com.tratoseguro.model.ArchivoInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArchivoEvidenciaRepository extends JpaRepository<ArchivoEvidencia, Long> {

    // Datos de los archivos de una disputa, sin cargar los bytes
    @Query("select new com.tratoseguro.model.ArchivoInfo(a.id, a.evidencia.id, a.nombre, a.tipoContenido, a.tamano, a.sha256) "
            + "from ArchivoEvidencia a where a.evidencia.disputa.id = :disputaId")
    List<ArchivoInfo> buscarInfoDeDisputa(@Param("disputaId") Long disputaId);

    Optional<ArchivoEvidencia> findByEvidenciaId(Long evidenciaId);
}
