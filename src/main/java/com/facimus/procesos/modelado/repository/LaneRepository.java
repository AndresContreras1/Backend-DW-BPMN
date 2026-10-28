package com.facimus.procesos.modelado.repository;

import java.util.List;
import java.util.Optional;

import java.util.Collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Una sola consulta agrupada: por cada rol, los procesos ACTIVOS donde alguna lane lo usa.
     * Cada fila es [rolId, procesoId, procesoNombre].
     */
    @Query("select l.rolProceso.id, l.pool.proceso.id, l.pool.proceso.nombre from Lane l "
            + "where l.empresa.id = :empresaId and l.rolProceso.id in :rolIds and l.pool.proceso.activo = true "
            + "group by l.rolProceso.id, l.pool.proceso.id, l.pool.proceso.nombre")
    List<Object[]> procesosActivosPorRoles(@Param("rolIds") Collection<Long> rolIds,
            @Param("empresaId") Long empresaId);
}
