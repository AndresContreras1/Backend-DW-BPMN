package com.facimus.procesos.modelado.controller.dto;

public record ArcoResponse(Long id, String etiqueta, String condicion, Long origenId, Long destinoId,
        Long poolId) {
}
