package com.facimus.procesos.gestion.controller.dto;

public record RolProcesoVistaResponse(Long id, String nombre, String descripcion, long procesosQueLoUsan,
        boolean enUso) {
}
