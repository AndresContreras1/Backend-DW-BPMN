package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.model.Proceso;

public interface ProcesoOrquestadorService {

    Proceso crearConPoolInicial(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria);
}
