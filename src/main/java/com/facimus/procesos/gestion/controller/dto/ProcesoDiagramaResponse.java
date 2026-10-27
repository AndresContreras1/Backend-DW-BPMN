package com.facimus.procesos.gestion.controller.dto;

import java.util.List;

import com.facimus.procesos.modelado.controller.dto.ActividadResponse;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.controller.dto.GatewayResponse;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;

public record ProcesoDiagramaResponse(
        ProcesoResponse proceso,
        List<PoolResponse> pools,
        List<LaneResponse> lanes,
        List<ActividadResponse> actividades,
        List<GatewayResponse> gateways,
        List<EventoMensajeResponse> eventos,
        List<ArcoResponse> arcos,
        List<MensajeResponse> mensajes) {
}
