package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;

public record MensajeResponse(Long id, String nombre, String contenido, Long poolOrigenId,
        Long poolDestinoId, Long procesoId, Long eventoThrowId, Long eventoCatchId,
        String claveCorrelacion, TipoDestinoExterno tipoDestinoExterno, String destinoExterno,
        PoliticaFalloNotificacion politicaFalloNotificacion, Long actividadErrorId,
        PoliticaMensajeSinCaso politicaSinCaso, boolean sinReceptor) {
}
