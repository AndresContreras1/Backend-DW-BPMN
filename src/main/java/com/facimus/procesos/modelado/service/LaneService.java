package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.controller.dto.ReordenarLanesRequest;
import java.util.List;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.modelado.model.Lane;

public interface LaneService {

    Lane crear(Long empresaId, Long poolId, String nombre, RolProceso rolProceso);

    Lane editar(Long empresaId, Long laneId, String nombre, RolProceso rolProceso);

    void eliminar(Long empresaId, Long laneId);

    List<Lane> reordenar(Long empresaId, Long poolId, List<Long> laneIds);

    List<Lane> listarPorPool(Long empresaId, Long poolId);

    Lane obtener(Long empresaId, Long laneId);

    List<Lane> listarPorProceso(Long empresaId, Long procesoId);

    List<Lane> listarPorRolProceso(Long empresaId, Long rolProcesoId);

    long contarPorRolProceso(Long empresaId, Long rolProcesoId);

    void eliminarPorPool(Long empresaId, Long poolId);

    boolean existe(Long empresaId, Long laneId);

    List<LaneResponse> reordenar(Long empresaId, Long poolId, ReordenarLanesRequest request);

    List<LaneResponse> consultarPorPool(Long empresaId, Long poolId);

    LaneResponse consultarDetalle(Long empresaId, Long laneId);
}
