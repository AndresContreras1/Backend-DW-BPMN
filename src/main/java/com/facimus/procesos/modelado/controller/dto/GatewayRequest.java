package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoGateway;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GatewayRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotNull(message = "El tipo de gateway es obligatorio.") TipoGateway tipoGateway,
        int posicionX,
        int posicionY,
        @Schema(description = "Solo al editar: mueve el gateway a otra lane del mismo proceso y pool (opcional).")
        Long laneId) {

    public GatewayRequest(String nombre, TipoGateway tipoGateway, int posicionX, int posicionY) {
        this(nombre, tipoGateway, posicionX, posicionY, null);
    }
}
