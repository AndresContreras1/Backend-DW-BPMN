package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoActividad;

public record ActividadResponse(Long id, String nombre, String descripcion, int posicionX, int posicionY,
        Long laneId, TipoActividad tipoActividad) {
}
