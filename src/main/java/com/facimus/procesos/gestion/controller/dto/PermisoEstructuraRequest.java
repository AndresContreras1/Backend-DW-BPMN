package com.facimus.procesos.gestion.controller.dto;

public record PermisoEstructuraRequest(
        boolean crearPool,
        boolean editarPool,
        boolean eliminarPool,
        boolean crearLane,
        boolean editarLane,
        boolean eliminarLane) {
}
