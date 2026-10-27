package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;

import jakarta.validation.constraints.NotBlank;

public record EditarMensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El contenido es obligatorio.") String contenido,
        Long eventoCatchId,
        String claveCorrelacion,
        TipoDestinoExterno tipoDestinoExterno,
        String destinoExterno,
        PoliticaFalloNotificacion politicaFalloNotificacion,
        Long actividadErrorId,
        PoliticaMensajeSinCaso politicaSinCaso) {

    public EditarMensajeRequest(String nombre, String contenido) {
        this(nombre, contenido, null, null, null, null, null, null, null);
    }
}
