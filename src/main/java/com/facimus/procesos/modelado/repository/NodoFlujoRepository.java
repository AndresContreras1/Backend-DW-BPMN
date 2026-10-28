package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.NodoFlujo;

/** Polimorfico: devuelve Actividad y Gateway mezclados (misma tabla, SINGLE_TABLE). */
public interface NodoFlujoRepository extends RepositorioTenant<NodoFlujo> {

    List<NodoFlujo> findAllByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    List<NodoFlujo> findAllByLane_Pool_ProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

    boolean existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndActivoTrue(String nombre, Long procesoId, Long empresaId);

    boolean existsByLane_PoolIdAndEmpresaIdAndActivoTrue(Long poolId, Long empresaId);

    /** Nodos activos del proceso con ese nombre (sin distinguir mayusculas), de cualquier subtipo. */
    @Query("select n from NodoFlujo n where lower(n.nombre) = lower(:nombre) "
            + "and n.lane.pool.proceso.id = :procesoId and n.empresa.id = :empresaId and n.activo = true")
    List<NodoFlujo> buscarActivosPorNombre(@Param("nombre") String nombre, @Param("procesoId") Long procesoId,
            @Param("empresaId") Long empresaId);
}
