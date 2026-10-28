package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.LaneRequest;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;

public interface LaneOrquestadorService {

    LaneResponse crear(Long empresaId, Long poolId, LaneRequest request);

    LaneResponse editar(Long empresaId, Long laneId, LaneRequest request);
}
