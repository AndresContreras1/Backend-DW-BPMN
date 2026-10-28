package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.ProcesoDiagramaResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import com.facimus.procesos.modelado.controller.dto.ActividadResponse;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.controller.dto.GatewayResponse;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;
import org.modelmapper.ModelMapper;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.dto.ProcesoDiagrama;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.NodoFlujoService;
import com.facimus.procesos.modelado.service.PoolService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProcesoDiagramaServiceImpl implements ProcesoDiagramaService {

    private final ProcesoService procesoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final NodoFlujoService nodoFlujoService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public ProcesoDiagrama obtener(Long empresaId, Long procesoId) {
        Proceso proceso = procesoService.obtener(empresaId, procesoId);
        List<Pool> pools = poolService.listarPorProceso(empresaId, procesoId);
        List<Lane> lanes = laneService.listarPorProceso(empresaId, procesoId);
        var nodos = nodoFlujoService.listarActivosPorProceso(empresaId, procesoId);
        List<Actividad> actividades = nodos.stream()
                .filter(Actividad.class::isInstance).map(Actividad.class::cast).toList();
        List<Gateway> gateways = nodos.stream()
                .filter(Gateway.class::isInstance).map(Gateway.class::cast).toList();
        List<EventoMensaje> eventos = nodos.stream()
                .filter(EventoMensaje.class::isInstance).map(EventoMensaje.class::cast).toList();
        List<Arco> arcos = arcoService.listarActivosPorProceso(empresaId, procesoId);
        List<Mensaje> mensajes = mensajeService.listarPorProceso(empresaId, procesoId);
        return new ProcesoDiagrama(proceso, pools, lanes, actividades, gateways, eventos, arcos, mensajes);
    }

    @Transactional(readOnly = true)
    @Override
    public ProcesoDiagramaResponse consultarDiagrama(Long empresaId, Long procesoId) {
        ProcesoDiagrama diagrama = obtener(empresaId, procesoId);
        return new ProcesoDiagramaResponse(
                modelMapper.map(diagrama.proceso(), ProcesoResponse.class),
                diagrama.pools().stream().map(p -> modelMapper.map(p, PoolResponse.class)).toList(),
                diagrama.lanes().stream().map(l -> modelMapper.map(l, LaneResponse.class)).toList(),
                diagrama.actividades().stream().map(a -> modelMapper.map(a, ActividadResponse.class)).toList(),
                diagrama.gateways().stream().map(g -> modelMapper.map(g, GatewayResponse.class)).toList(),
                diagrama.eventos().stream().map(e -> modelMapper.map(e, EventoMensajeResponse.class)).toList(),
                diagrama.arcos().stream().map(a -> modelMapper.map(a, ArcoResponse.class)).toList(),
                diagrama.mensajes().stream().map(m -> modelMapper.map(m, MensajeResponse.class)).toList());
    }
}
