package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.EventoMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import org.modelmapper.ModelMapper;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventoMensajeServiceImpl implements EventoMensajeService {

    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final ArcoService arcoService;
    private final AuditoriaModeladoService auditoriaModeladoService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public EventoMensaje crear(Long empresaId, Long laneId, String nombre, TipoEventoMensaje tipoEvento,
            String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno) {
        Lane lane = laneService.obtener(empresaId, laneId);
        validarProcesoActivo(lane.getPool().getProceso());
        if (lane.getPool().isCajaNegra()) {
            throw new ReglaNegocioException("Un pool de caja negra no puede contener eventos internos.");
        }
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        EventoMensaje evento = new EventoMensaje();
        evento.setEmpresa(lane.getEmpresa());
        evento.setLane(lane);
        evento.setNombre(nombre);
        evento.setTipoEvento(tipoEvento);
        evento.setContenido(contenido);
        evento.setClaveCorrelacion(claveCorrelacion);
        evento.setPosicionX(posX);
        evento.setPosicionY(posY);
        evento.setOrigenExterno(origenExterno);
        evento = (EventoMensaje) nodoFlujoService.guardar(evento);
        auditoriaModeladoService.registrar(lane.getPool().getProceso(),
                "Evento de mensaje creado: " + evento.getNombre() + " (" + evento.getTipoEvento() + ").");
        return evento;
    }

    @Transactional
    @Override
    public EventoMensaje editar(Long empresaId, Long eventoId, String nombre, TipoEventoMensaje tipoEvento,
            String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno) {
        EventoMensaje evento = obtener(empresaId, eventoId);
        validarProcesoActivo(evento.getLane().getPool().getProceso());
        Long procesoId = evento.getLane().getPool().getProceso().getId();
        if (!evento.getNombre().equalsIgnoreCase(nombre)
                && nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        if (tipoEvento == TipoEventoMensaje.CATCH_INICIO
                && arcoService.listarPorDestino(empresaId, eventoId).stream()
                        .anyMatch(arco -> arco.isActivo())) {
            throw new ReglaNegocioException("Un Message Catch de inicio no puede tener arcos entrantes.");
        }
        evento.setNombre(nombre);
        evento.setTipoEvento(tipoEvento);
        evento.setContenido(contenido);
        evento.setClaveCorrelacion(claveCorrelacion);
        evento.setPosicionX(posX);
        evento.setPosicionY(posY);
        evento.setOrigenExterno(origenExterno);
        evento = (EventoMensaje) nodoFlujoService.guardar(evento);
        auditoriaModeladoService.registrar(evento.getLane().getPool().getProceso(),
                "Evento de mensaje editado: " + evento.getNombre() + ".");
        return evento;
    }

    @Transactional
    @Override
    public void eliminar(Long empresaId, Long eventoId) {
        EventoMensaje evento = obtener(empresaId, eventoId);
        validarProcesoActivo(evento.getLane().getPool().getProceso());
        var proceso = evento.getLane().getPool().getProceso();
        String nombre = evento.getNombre();
        arcoService.desactivarPorNodo(empresaId, eventoId);
        evento.setActivo(false);
        nodoFlujoService.guardar(evento);
        auditoriaModeladoService.registrar(proceso, "Evento de mensaje eliminado (baja logica): " + nombre + ".");
    }

    @Transactional(readOnly = true)
    @Override
    public EventoMensaje obtener(Long empresaId, Long eventoId) {
        return nodoFlujoService.buscar(empresaId, eventoId)
                .filter(nodo -> nodo.isActivo())
                .filter(EventoMensaje.class::isInstance)
                .map(EventoMensaje.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento de mensaje no encontrado."));
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventoMensaje> listarPorLane(Long empresaId, Long laneId) {
        laneService.obtener(empresaId, laneId);
        return nodoFlujoService.listarPorLane(empresaId, laneId).stream()
                .filter(nodo -> nodo.isActivo())
                .filter(EventoMensaje.class::isInstance)
                .map(EventoMensaje.class::cast)
                .toList();
    }

    @Transactional
    @Override
    public EventoMensajeResponse crear(Long empresaId, Long laneId, EventoMensajeRequest request) {
        EventoMensaje evento = crear(empresaId, laneId, request.nombre(), request.tipoEvento(),
                request.contenido(), request.claveCorrelacion(), request.posicionX(),
                request.posicionY(), request.origenExterno());
        return modelMapper.map(evento, EventoMensajeResponse.class);
    }

    @Transactional
    @Override
    public EventoMensajeResponse editar(Long empresaId, Long eventoId, EventoMensajeRequest request) {
        EventoMensaje evento = editar(empresaId, eventoId, request.nombre(), request.tipoEvento(),
                request.contenido(), request.claveCorrelacion(), request.posicionX(),
                request.posicionY(), request.origenExterno());
        return modelMapper.map(evento, EventoMensajeResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public EventoMensajeResponse consultarDetalle(Long empresaId, Long eventoId) {
        return modelMapper.map(obtener(empresaId, eventoId), EventoMensajeResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public List<EventoMensajeResponse> consultarPorLane(Long empresaId, Long laneId) {
        return listarPorLane(empresaId, laneId).stream()
                .map(e -> modelMapper.map(e, EventoMensajeResponse.class)).toList();
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
