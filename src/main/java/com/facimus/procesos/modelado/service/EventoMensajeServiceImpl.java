package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.EventoMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import org.modelmapper.ModelMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventoMensajeServiceImpl implements EventoMensajeService {

    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final ArcoService arcoService;
    private final ActividadService actividadService;
    private final MensajeService mensajeService;
    private final AuditoriaModeladoService auditoriaModeladoService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public EventoMensaje crear(Long empresaId, Long laneId, String nombre, TipoEventoMensaje tipoEvento,
            String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno) {
        return crear(empresaId, laneId, nombre, tipoEvento, contenido, claveCorrelacion, posX, posY,
                origenExterno, null);
    }

    @Transactional
    @Override
    public EventoMensaje crear(Long empresaId, Long laneId, String nombre, TipoEventoMensaje tipoEvento,
            String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno,
            List<Long> actividadIds) {
        Lane lane = laneService.obtener(empresaId, laneId);
        validarProcesoActivo(lane.getPool().getProceso());
        if (lane.getPool().isCajaNegra()) {
            throw new ReglaNegocioException("Un pool de caja negra no puede contener eventos internos.");
        }
        Long procesoId = lane.getPool().getProceso().getId();
        validarNombreDisponible(empresaId, procesoId, nombre, tipoEvento, null);
        Set<Actividad> actividades = resolverActividades(empresaId, procesoId, tipoEvento, actividadIds);

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
        if (actividades != null) {
            evento.setActividades(actividades);
        }
        evento = (EventoMensaje) nodoFlujoService.guardar(evento);
        auditoriaModeladoService.registrar(lane.getPool().getProceso(),
                "Evento de mensaje creado: " + evento.getNombre() + " (" + evento.getTipoEvento() + ").");
        return evento;
    }

    @Transactional
    @Override
    public EventoMensaje editar(Long empresaId, Long eventoId, String nombre, TipoEventoMensaje tipoEvento,
            String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno) {
        return editar(empresaId, eventoId, nombre, tipoEvento, contenido, claveCorrelacion, posX, posY,
                origenExterno, null, null);
    }

    @Transactional
    @Override
    public EventoMensaje editar(Long empresaId, Long eventoId, String nombre, TipoEventoMensaje tipoEvento,
            String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno,
            Long laneId, List<Long> actividadIds) {
        EventoMensaje evento = obtener(empresaId, eventoId);
        validarProcesoActivo(evento.getLane().getPool().getProceso());
        Long procesoId = evento.getLane().getPool().getProceso().getId();
        validarNombreDisponible(empresaId, procesoId, nombre, tipoEvento, eventoId);

        Lane nuevoLane = ReglasMovimientoNodo.resolverLaneDestino(empresaId, evento, laneId, "eventos internos",
                laneService, arcoService);
        validarCambiosConMensajes(empresaId, evento, nombre, tipoEvento, claveCorrelacion, nuevoLane);

        if (tipoEvento == TipoEventoMensaje.CATCH_INICIO
                && arcoService.listarPorDestino(empresaId, eventoId).stream()
                        .anyMatch(arco -> arco.isActivo())) {
            throw new ReglaNegocioException("Un Message Catch de inicio no puede tener arcos entrantes.");
        }
        Set<Actividad> actividades = resolverActividades(empresaId, procesoId, tipoEvento, actividadIds);

        String nombreAnterior = evento.getNombre();
        TipoEventoMensaje tipoAnterior = evento.getTipoEvento();
        String laneAnterior = evento.getLane().getNombre();
        evento.setNombre(nombre);
        evento.setTipoEvento(tipoEvento);
        evento.setContenido(contenido);
        evento.setClaveCorrelacion(claveCorrelacion);
        evento.setPosicionX(posX);
        evento.setPosicionY(posY);
        evento.setOrigenExterno(origenExterno);
        if (nuevoLane != null) {
            evento.setLane(nuevoLane);
        }
        if (actividades != null) {
            evento.getActividades().clear();
            evento.getActividades().addAll(actividades);
        } else if (tipoEvento == TipoEventoMensaje.THROW) {
            evento.getActividades().clear();
        }
        evento = (EventoMensaje) nodoFlujoService.guardar(evento);

        List<String> cambios = new ArrayList<>();
        if (!nombreAnterior.equals(nombre)) {
            cambios.add("nombre: " + nombreAnterior + " -> " + nombre);
        }
        if (tipoAnterior != tipoEvento) {
            cambios.add("tipo: " + tipoAnterior + " -> " + tipoEvento);
        }
        if (nuevoLane != null) {
            cambios.add("lane: " + laneAnterior + " -> " + nuevoLane.getNombre());
        }
        auditoriaModeladoService.registrar(evento.getLane().getPool().getProceso(),
                "Evento de mensaje editado: " + evento.getNombre()
                        + (cambios.isEmpty() ? "." : " (" + String.join("; ", cambios) + ")."));
        return evento;
    }

    @Transactional(readOnly = true)
    @Override
    public ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long eventoId) {
        EventoMensaje evento = obtener(empresaId, eventoId);
        List<String> advertencias = new ArrayList<>();

        List<Arco> entrantes = arcoService.listarPorDestino(empresaId, eventoId).stream()
                .filter(Arco::isActivo).toList();
        List<Arco> salientes = arcoService.listarPorOrigen(empresaId, eventoId).stream()
                .filter(Arco::isActivo).toList();

        entrantes.forEach(arco -> {
            long otrasSalidas = arcoService.listarPorOrigen(empresaId, arco.getOrigen().getId())
                    .stream().filter(a -> a.isActivo() && !a.getId().equals(arco.getId())).count();
            if (otrasSalidas == 0) {
                advertencias.add("El nodo " + arco.getOrigen().getNombre() + " quedara sin salida.");
            }
        });
        salientes.forEach(arco -> {
            long otrasEntradas = arcoService.listarPorDestino(empresaId, arco.getDestino().getId())
                    .stream().filter(a -> a.isActivo() && !a.getId().equals(arco.getId())).count();
            if (otrasEntradas == 0) {
                advertencias.add("El nodo " + arco.getDestino().getNombre() + " quedara sin entrada.");
            }
        });
        if (!entrantes.isEmpty() || !salientes.isEmpty()) {
            advertencias.add("Se eliminaran los arcos conectados a " + evento.getNombre() + ".");
        }

        List<Mensaje> mensajes = mensajeService.listarActivosPorEvento(empresaId, eventoId);
        if (!mensajes.isEmpty()) {
            advertencias.add("El evento " + evento.getNombre() + " esta vinculado a los mensajes: "
                    + nombresDe(mensajes) + ". No se podra eliminar hasta eliminar o desvincular esos mensajes.");
        }
        return new ImpactoEliminacion(!advertencias.isEmpty(), advertencias);
    }

    @Transactional
    @Override
    public void eliminar(Long empresaId, Long eventoId) {
        EventoMensaje evento = obtener(empresaId, eventoId);
        validarProcesoActivo(evento.getLane().getPool().getProceso());
        List<Mensaje> mensajes = mensajeService.listarActivosPorEvento(empresaId, eventoId);
        if (!mensajes.isEmpty()) {
            throw new ReglaNegocioException("No se puede eliminar el evento \"" + evento.getNombre()
                    + "\": lo usan los mensajes " + nombresDe(mensajes)
                    + ". Elimine o modifique esos mensajes primero.");
        }
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
                request.posicionY(), request.origenExterno(), request.actividadIds());
        return modelMapper.map(evento, EventoMensajeResponse.class);
    }

    @Transactional
    @Override
    public EventoMensajeResponse editar(Long empresaId, Long eventoId, EventoMensajeRequest request) {
        EventoMensaje evento = editar(empresaId, eventoId, request.nombre(), request.tipoEvento(),
                request.contenido(), request.claveCorrelacion(), request.posicionX(),
                request.posicionY(), request.origenExterno(), request.laneId(), request.actividadIds());
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

    @Transactional(readOnly = true)
    @Override
    public ImpactoEliminacionResponse consultarImpactoEliminacion(Long empresaId, Long eventoId) {
        return modelMapper.map(evaluarImpactoEliminacion(empresaId, eventoId),
                ImpactoEliminacionResponse.class);
    }

    /**
     * El nombre debe ser unico en el proceso frente a actividades, gateways y eventos del mismo lado.
     * Un Message Throw y un Message Catch (lados distintos) pueden compartir nombre: MensajeService
     * exige que el nombre del evento coincida con el del mensaje que los vincula.
     */
    private void validarNombreDisponible(Long empresaId, Long procesoId, String nombre,
            TipoEventoMensaje tipoEvento, Long eventoActualId) {
        boolean ladoThrow = tipoEvento == TipoEventoMensaje.THROW;
        boolean conflicto = nodoFlujoService.listarActivosPorNombre(nombre, procesoId, empresaId).stream()
                .filter(nodo -> !Objects.equals(nodo.getId(), eventoActualId))
                .anyMatch(nodo -> !(nodo instanceof EventoMensaje otro)
                        || (otro.getTipoEvento() == TipoEventoMensaje.THROW) == ladoThrow);
        if (conflicto) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
    }

    /** Si hay mensajes activos vinculados, no se pueden cambiar los datos que esos mensajes validan. */
    private void validarCambiosConMensajes(Long empresaId, EventoMensaje evento, String nombre,
            TipoEventoMensaje tipoEvento, String claveCorrelacion, Lane nuevoLane) {
        List<Mensaje> mensajes = mensajeService.listarActivosPorEvento(empresaId, evento.getId());
        if (mensajes.isEmpty()) {
            return;
        }
        boolean cambiaNombre = !evento.getNombre().equalsIgnoreCase(nombre);
        boolean cambiaLado = (evento.getTipoEvento() == TipoEventoMensaje.THROW)
                != (tipoEvento == TipoEventoMensaje.THROW);
        boolean cambiaClave = evento.getClaveCorrelacion() == null
                ? claveCorrelacion != null
                : !evento.getClaveCorrelacion().equalsIgnoreCase(claveCorrelacion);
        boolean cambiaPool = nuevoLane != null
                && !nuevoLane.getPool().getId().equals(evento.getLane().getPool().getId());
        if (cambiaNombre || cambiaLado || cambiaClave || cambiaPool) {
            throw new ReglaNegocioException("El evento \"" + evento.getNombre()
                    + "\" esta vinculado a los mensajes: " + nombresDe(mensajes)
                    + ". No se puede cambiar su nombre, tipo, clave de correlacion ni pool mientras esos "
                    + "mensajes esten activos.");
        }
    }

    /**
     * null = no tocar las actividades. Solo los eventos CATCH las admiten; deben ser activas y del
     * mismo proceso (la empresa la garantiza ActividadService.obtener).
     */
    private Set<Actividad> resolverActividades(Long empresaId, Long procesoId, TipoEventoMensaje tipoEvento,
            List<Long> actividadIds) {
        if (actividadIds == null) {
            return null;
        }
        if (tipoEvento == TipoEventoMensaje.THROW && !actividadIds.isEmpty()) {
            throw new ReglaNegocioException(
                    "Solo un Message Catch puede indicar las actividades que usan los datos del mensaje.");
        }
        Set<Actividad> actividades = new HashSet<>();
        for (Long actividadId : new HashSet<>(actividadIds)) {
            Actividad actividad = actividadService.obtener(empresaId, actividadId);
            if (!actividad.getLane().getPool().getProceso().getId().equals(procesoId)) {
                throw new ReglaNegocioException("La actividad \"" + actividad.getNombre()
                        + "\" no pertenece al mismo proceso del evento.");
            }
            actividades.add(actividad);
        }
        return actividades;
    }

    private String nombresDe(List<Mensaje> mensajes) {
        return mensajes.stream().map(m -> "\"" + m.getNombre() + "\"").collect(Collectors.joining(", "));
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
