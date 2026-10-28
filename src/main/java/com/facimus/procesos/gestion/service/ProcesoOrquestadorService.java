package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.ProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;

import com.facimus.procesos.gestion.model.Proceso;

public interface ProcesoOrquestadorService {

    Proceso crearConPoolInicial(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria);

    ProcesoResponse crearConPoolInicial(Long empresaId, Long usuarioId, ProcesoRequest request);
}
