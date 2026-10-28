package com.facimus.procesos.modelado.controller.dto;

import java.util.List;

import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventoMensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotNull(message = "El tipo de evento es obligatorio.") TipoEventoMensaje tipoEvento,
        @NotBlank(message = "El contenido del mensaje es obligatorio.") String contenido,
        @NotBlank(message = "La clave de correlacion es obligatoria.") String claveCorrelacion,
        int posicionX,
        int posicionY,
        boolean origenExterno,
        @Schema(description = "Solo al editar: mueve el evento a otra lane del mismo proceso y pool (opcional).")
        Long laneId,
        @Schema(description = "Solo eventos CATCH: actividades del mismo proceso que usan los datos del mensaje "
                + "(HU-27). Si se omite no se cambian; una lista vacia las quita todas.")
        List<Long> actividadIds) {

    public EventoMensajeRequest(String nombre, TipoEventoMensaje tipoEvento, String contenido,
            String claveCorrelacion, int posicionX, int posicionY, boolean origenExterno) {
        this(nombre, tipoEvento, contenido, claveCorrelacion, posicionX, posicionY, origenExterno, null, null);
    }
}
