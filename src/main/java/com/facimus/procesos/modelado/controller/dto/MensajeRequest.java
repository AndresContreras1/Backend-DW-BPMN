package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El contenido es obligatorio.") String contenido,
        @NotNull(message = "El pool de origen es obligatorio.") Long poolOrigenId,
        @NotNull(message = "El pool de destino es obligatorio.") Long poolDestinoId,
        Long eventoThrowId,
        Long eventoCatchId,
        String claveCorrelacion,
        TipoDestinoExterno tipoDestinoExterno,
        String destinoExterno,
        PoliticaFalloNotificacion politicaFalloNotificacion,
        Long actividadErrorId,
        PoliticaMensajeSinCaso politicaSinCaso) {

    public MensajeRequest(String nombre, String contenido, Long poolOrigenId, Long poolDestinoId) {
        this(nombre, contenido, poolOrigenId, poolDestinoId, null, null, null, null, null, null, null, null);
    }
}
