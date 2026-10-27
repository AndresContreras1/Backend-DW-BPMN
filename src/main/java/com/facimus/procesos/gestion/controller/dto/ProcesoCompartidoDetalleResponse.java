package com.facimus.procesos.gestion.controller.dto;

public record ProcesoCompartidoDetalleResponse(
        ProcesoResponse proceso,
        String empresaPropietaria,
        boolean soloLectura,
        java.util.List<com.facimus.procesos.modelado.controller.dto.PoolResponse> pools,
        java.util.List<com.facimus.procesos.modelado.controller.dto.LaneResponse> lanes,
        java.util.List<com.facimus.procesos.modelado.controller.dto.ActividadResponse> actividades,
        java.util.List<com.facimus.procesos.modelado.controller.dto.GatewayResponse> gateways,
        java.util.List<com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse> eventos,
        java.util.List<com.facimus.procesos.modelado.controller.dto.ArcoResponse> arcos,
        java.util.List<com.facimus.procesos.modelado.controller.dto.MensajeResponse> mensajes) {
}
