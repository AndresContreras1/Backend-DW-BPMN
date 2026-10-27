package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.NodoFlujo;

/** Polimorfico: devuelve Actividad y Gateway mezclados (misma tabla, SINGLE_TABLE). */
public interface NodoFlujoRepository extends RepositorioTenant<NodoFlujo> {

    List<NodoFlujo> findAllByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    List<NodoFlujo> findAllByLane_Pool_ProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

    boolean existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndActivoTrue(String nombre, Long procesoId, Long empresaId);

    boolean existsByLane_PoolIdAndEmpresaIdAndActivoTrue(Long poolId, Long empresaId);
}
