package com.facimus.procesos.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Lane;

public interface LaneRepository extends RepositorioTenant<Lane> {

    /** rolProceso es LAZY y se mapea a DTO en el controller: se carga explicitamente aqui. */
    @Override
    @EntityGraph(attributePaths = "rolProceso")
    Optional<Lane> findByIdAndEmpresaId(Long id, Long empresaId);

    @EntityGraph(attributePaths = "rolProceso")
    List<Lane> findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(Long poolId, Long empresaId);

    @EntityGraph(attributePaths = "rolProceso")
    List<Lane> findAllByPool_ProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

    long countByRolProcesoIdAndEmpresaId(Long rolProcesoId, Long empresaId);

    boolean existsByRolProcesoIdAndEmpresaId(Long rolProcesoId, Long empresaId);

    List<Lane> findAllByRolProcesoIdAndEmpresaId(Long rolProcesoId, Long empresaId);
}
