package com.facimus.procesos.gestion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.HistorialCambio;

public interface HistorialCambioRepository extends RepositorioTenant<HistorialCambio> {

    /** {@code autor} es LAZY y se mapea a DTO en el controller: se carga explicitamente aqui. */
    @EntityGraph(attributePaths = "autor")
    List<HistorialCambio> findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDesc(Long procesoId, Long empresaId);
}
