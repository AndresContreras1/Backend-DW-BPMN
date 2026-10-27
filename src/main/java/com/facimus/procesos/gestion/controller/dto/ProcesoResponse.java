package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoProceso;

public record ProcesoResponse(Long id, String nombre, String descripcion, String categoria,
        EstadoProceso estado, boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion) {
}
