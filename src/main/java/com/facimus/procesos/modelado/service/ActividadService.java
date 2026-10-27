package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

import lombok.RequiredArgsConstructor;

/** HU-08 a HU-10: actividades (tareas del proceso). */
@Service
@RequiredArgsConstructor
public class ActividadService {

    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final ArcoService arcoService;
    private final AuditoriaModeladoService auditoriaModeladoService;

    @Transactional
    public Actividad crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX, int posY) {
        return crear(empresaId, laneId, nombre, descripcion, posX, posY, TipoActividad.TAREA);
    }

    @Transactional
    public Actividad crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX, int posY,
            TipoActividad tipoActividad) {
        Lane lane = laneService.obtener(empresaId, laneId);
        validarProcesoActivo(lane.getPool().getProceso());
        if (lane.getPool().isCajaNegra()) {
            throw new ReglaNegocioException("Un pool de caja negra no puede contener actividades.");
        }
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Actividad actividad = new Actividad();
        actividad.setEmpresa(lane.getEmpresa());
        actividad.setLane(lane);
        actividad.setNombre(nombre);
        actividad.setDescripcion(descripcion);
        actividad.setTipoActividad(tipoActividad != null ? tipoActividad : TipoActividad.TAREA);
        actividad.setPosicionX(posX);
        actividad.setPosicionY(posY);
        actividad = (Actividad) nodoFlujoService.guardar(actividad);
        auditoriaModeladoService.registrar(lane.getPool().getProceso(), "Actividad creada: " + actividad.getNombre() + ".");
        return actividad;
    }

    @Transactional
    public Actividad editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX, int posY,
            Long laneId) {
        return editar(empresaId, actividadId, nombre, descripcion, posX, posY, laneId, null);
    }

    @Transactional
    public Actividad editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX, int posY,
            Long laneId, TipoActividad tipoActividad) {
        Actividad actividad = obtener(empresaId, actividadId);
        validarProcesoActivo(actividad.getLane().getPool().getProceso());
        if (laneId != null && !laneId.equals(actividad.getLane().getId())) {
            Lane nuevoLane = laneService.obtener(empresaId, laneId);
            if (nuevoLane.getPool().isCajaNegra()) {
                throw new ReglaNegocioException("Un pool de caja negra no puede contener actividades.");
            }
            Long procesoActual = actividad.getLane().getPool().getProceso().getId();
            Long procesoNuevo = nuevoLane.getPool().getProceso().getId();
            if (!procesoActual.equals(procesoNuevo)) {
                throw new ReglaNegocioException("El lane destino debe pertenecer al mismo proceso.");
            }
            Long poolActual = actividad.getLane().getPool().getId();
            Long poolNuevo = nuevoLane.getPool().getId();
            if (!poolActual.equals(poolNuevo)) {
                boolean tieneArcos = !arcoService.listarPorOrigen(empresaId, actividadId).isEmpty()
                        || !arcoService.listarPorDestino(empresaId, actividadId).isEmpty();
                if (tieneArcos) {
                    throw new ReglaNegocioException(
                            "No se puede mover la actividad a otro pool porque tiene arcos conectados.");
                }
            }
            actividad.setLane(nuevoLane);
        }
        Long procesoId = actividad.getLane().getPool().getProceso().getId();
        if (!actividad.getNombre().equalsIgnoreCase(nombre)
                && nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        actividad.setNombre(nombre);
        actividad.setDescripcion(descripcion);
        if (tipoActividad != null) {
            actividad.setTipoActividad(tipoActividad);
        }
        actividad.setPosicionX(posX);
        actividad.setPosicionY(posY);
        actividad = (Actividad) nodoFlujoService.guardar(actividad);
        auditoriaModeladoService.registrar(actividad.getLane().getPool().getProceso(),
                "Actividad editada: " + actividad.getNombre() + ".");
        return actividad;
    }

    @Transactional(readOnly = true)
    public ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long actividadId) {
        Actividad actividad = obtener(empresaId, actividadId);
        List<String> advertencias = new java.util.ArrayList<>();

        var entrantes = arcoService.listarPorDestino(empresaId, actividadId).stream()
                .filter(arco -> arco.isActivo()).toList();
        var salientes = arcoService.listarPorOrigen(empresaId, actividadId).stream()
                .filter(arco -> arco.isActivo()).toList();

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

        if (!entrantes.isEmpty() && !salientes.isEmpty()) {
            advertencias.add("Se eliminaran los arcos conectados a " + actividad.getNombre() + ".");
        }
        return new ImpactoEliminacion(!advertencias.isEmpty(), advertencias);
    }

    @Transactional
    public void eliminar(Long empresaId, Long actividadId) {
        Actividad actividad = obtener(empresaId, actividadId);
        validarProcesoActivo(actividad.getLane().getPool().getProceso());
        var proceso = actividad.getLane().getPool().getProceso();
        String nombre = actividad.getNombre();
        arcoService.desactivarPorNodo(empresaId, actividadId);
        actividad.setActivo(false);
        nodoFlujoService.guardar(actividad);
        auditoriaModeladoService.registrar(proceso, "Actividad eliminada (baja logica): " + nombre + ".");
    }

    @Transactional(readOnly = true)
    public Actividad obtener(Long empresaId, Long actividadId) {
        return nodoFlujoService.buscar(empresaId, actividadId)
                .filter(nodo -> nodo.isActivo())
                .filter(Actividad.class::isInstance)
                .map(Actividad.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
    }

    @Transactional(readOnly = true)
    public List<Actividad> listarPorLane(Long empresaId, Long laneId) {
        laneService.obtener(empresaId, laneId);
        return nodoFlujoService.listarPorLane(empresaId, laneId).stream()
                .filter(nodo -> nodo.isActivo())
                .filter(Actividad.class::isInstance)
                .map(Actividad.class::cast)
                .toList();
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
