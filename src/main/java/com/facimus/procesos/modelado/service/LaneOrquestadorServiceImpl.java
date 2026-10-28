package com.facimus.procesos.modelado.service;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.controller.dto.LaneRequest;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.model.Lane;

import lombok.RequiredArgsConstructor;

/**
 * Orquestador transaccional de crear/editar una lane (HU-22 y HU-24): resuelve el RolProceso
 * con RolProcesoService y se lo pasa a LaneService. Vive aqui y no en LaneService porque
 * RolProcesoService ya depende de LaneService (para saber en que lanes se usa un rol), y
 * LaneService->RolProcesoService seria un ciclo.
 */
@Service
@RequiredArgsConstructor
public class LaneOrquestadorServiceImpl implements LaneOrquestadorService {

    private final LaneService laneService;
    private final RolProcesoService rolProcesoService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public LaneResponse crear(Long empresaId, Long poolId, LaneRequest request) {
        RolProceso rolProceso = rolProcesoService.obtener(empresaId, request.rolProcesoId());
        Lane lane = laneService.crear(empresaId, poolId, request.nombre(), rolProceso);
        return modelMapper.map(lane, LaneResponse.class);
    }

    @Transactional
    @Override
    public LaneResponse editar(Long empresaId, Long laneId, LaneRequest request) {
        RolProceso rolProceso = rolProcesoService.obtener(empresaId, request.rolProcesoId());
        Lane lane = laneService.editar(empresaId, laneId, request.nombre(), rolProceso);
        return modelMapper.map(lane, LaneResponse.class);
    }
}
