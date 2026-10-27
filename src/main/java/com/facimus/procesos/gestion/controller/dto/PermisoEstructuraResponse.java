package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;

public record PermisoEstructuraResponse(
        RolAcceso rolAcceso,
        boolean crearPool,
        boolean editarPool,
        boolean eliminarPool,
        boolean crearLane,
        boolean editarLane,
        boolean eliminarLane) {
}
