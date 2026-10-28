package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.GatewayRequest;
import com.facimus.procesos.modelado.controller.dto.GatewayResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import java.util.List;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

public interface GatewayService {

    Gateway crear(Long empresaId, Long laneId, String nombre, TipoGateway tipoGateway, int posX, int posY);

    Gateway editar(Long empresaId, Long gatewayId, String nombre, TipoGateway tipoGateway, int posX, int posY);

    ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long gatewayId);

    void eliminar(Long empresaId, Long gatewayId);

    Gateway obtener(Long empresaId, Long gatewayId);

    List<Gateway> listarPorLane(Long empresaId, Long laneId);

    GatewayResponse crear(Long empresaId, Long laneId, GatewayRequest request);

    GatewayResponse editar(Long empresaId, Long gatewayId, GatewayRequest request);

    GatewayResponse consultarDetalle(Long empresaId, Long gatewayId);

    List<GatewayResponse> consultarPorLane(Long empresaId, Long laneId);

    ImpactoEliminacionResponse consultarImpactoEliminacion(Long empresaId, Long gatewayId);
}
