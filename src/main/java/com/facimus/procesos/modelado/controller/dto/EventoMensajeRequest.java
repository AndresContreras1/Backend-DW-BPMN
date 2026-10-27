package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoEventoMensaje;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventoMensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotNull(message = "El tipo de evento es obligatorio.") TipoEventoMensaje tipoEvento,
        @NotBlank(message = "El contenido del mensaje es obligatorio.") String contenido,
        @NotBlank(message = "La clave de correlacion es obligatoria.") String claveCorrelacion,
        int posicionX,
        int posicionY,
        boolean origenExterno) {
}
