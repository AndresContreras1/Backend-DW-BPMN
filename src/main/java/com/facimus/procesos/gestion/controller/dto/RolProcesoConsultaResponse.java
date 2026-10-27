package com.facimus.procesos.gestion.controller.dto;

import java.util.List;

public record RolProcesoConsultaResponse(Long id, String nombre, String descripcion,
        List<String> procesos, boolean enUso) {
}
