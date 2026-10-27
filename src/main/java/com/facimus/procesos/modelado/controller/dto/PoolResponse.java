package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoParticipante;

public record PoolResponse(Long id, String nombre, TipoParticipante tipoParticipante, boolean cajaNegra,
        int orden, Long procesoId) {
}
