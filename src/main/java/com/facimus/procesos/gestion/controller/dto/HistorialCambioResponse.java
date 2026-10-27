package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDateTime;

public record HistorialCambioResponse(Long id, LocalDateTime fechaCambio, String descripcionCambio,
        String autorNombre) {
}
