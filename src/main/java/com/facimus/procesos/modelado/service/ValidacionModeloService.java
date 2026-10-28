package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.controller.dto.ValidacionModeloResponse;

public interface ValidacionModeloService {

    /** Lanza ReglaNegocioException (409) con todas las advertencias si el modelo no se puede publicar. */
    void validarParaPublicacion(Long empresaId, Long procesoId);

    /** Devuelve TODAS las advertencias del modelo, sin lanzar nada. Lista vacia = se puede publicar. */
    List<String> obtenerAdvertencias(Long empresaId, Long procesoId);

    ValidacionModeloResponse consultarValidacion(Long empresaId, Long procesoId);
}
