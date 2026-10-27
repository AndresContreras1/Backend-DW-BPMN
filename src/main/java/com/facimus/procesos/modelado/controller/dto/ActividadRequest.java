package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoActividad;

import jakarta.validation.constraints.NotBlank;

public record ActividadRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        String descripcion,
        int posicionX,
        int posicionY,
        Long laneId,
        TipoActividad tipoActividad) {

    public ActividadRequest(String nombre, String descripcion, int posicionX, int posicionY, Long laneId) {
        this(nombre, descripcion, posicionX, posicionY, laneId, null);
    }
}
