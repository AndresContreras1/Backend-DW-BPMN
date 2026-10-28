package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.ProcesoDiagramaResponse;

import com.facimus.procesos.gestion.service.dto.ProcesoDiagrama;

public interface ProcesoDiagramaService {

    ProcesoDiagrama obtener(Long empresaId, Long procesoId);

    ProcesoDiagramaResponse consultarDiagrama(Long empresaId, Long procesoId);
}
