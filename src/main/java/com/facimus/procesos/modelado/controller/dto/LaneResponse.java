package com.facimus.procesos.modelado.controller.dto;

public record LaneResponse(Long id, String nombre, int orden, Long poolId, Long rolProcesoId,
        String rolProcesoNombre) {
}
