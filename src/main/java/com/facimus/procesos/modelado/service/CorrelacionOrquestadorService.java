package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.model.Correlacion;

public interface CorrelacionOrquestadorService {

    Correlacion definir(Long empresaId, Long mensajeId, String criterio);

    void eliminar(Long empresaId, Long mensajeId);
}
