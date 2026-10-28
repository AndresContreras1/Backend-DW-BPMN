package com.facimus.procesos.modelado.service;

import com.facimus.procesos.gestion.model.Proceso;

public interface AuditoriaModeladoService {

    void registrar(Proceso proceso, String descripcion);
}
