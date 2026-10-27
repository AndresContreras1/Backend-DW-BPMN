package com.facimus.procesos.gestion.service.dto;

import java.util.List;

import com.facimus.procesos.gestion.model.RolProceso;

public record RolProcesoConsulta(RolProceso rol, List<String> procesos) {
}
