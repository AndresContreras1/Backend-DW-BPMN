package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoGateway;

public record GatewayResponse(Long id, String nombre, TipoGateway tipoGateway, int posicionX, int posicionY,
        Long laneId) {
}
