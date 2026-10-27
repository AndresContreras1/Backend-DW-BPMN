package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoEventoMensaje;

public record EventoMensajeResponse(Long id, String nombre, TipoEventoMensaje tipoEvento, String contenido,
        String claveCorrelacion, int posicionX, int posicionY, Long laneId, boolean origenExterno) {
}
