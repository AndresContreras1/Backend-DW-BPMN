package com.facimus.procesos.modelado.service;

import java.util.List;
import java.util.Optional;
import com.facimus.procesos.modelado.model.NodoFlujo;

public interface NodoFlujoService {

    NodoFlujo obtener(Long empresaId, Long nodoId);

    Optional<NodoFlujo> buscar(Long empresaId, Long nodoId);

    List<NodoFlujo> listarActivosPorProceso(Long empresaId, Long procesoId);

    List<NodoFlujo> listarPorLane(Long empresaId, Long laneId);

    boolean tieneNodosActivos(Long empresaId, Long laneId);

    boolean tieneNodosActivosEnPool(Long empresaId, Long poolId);

    boolean existeNombreEnProceso(String nombre, Long procesoId, Long empresaId);

    /** Nodos activos del proceso con ese nombre (sin distinguir mayusculas), de cualquier subtipo. */
    List<NodoFlujo> listarActivosPorNombre(String nombre, Long procesoId, Long empresaId);

    NodoFlujo guardar(NodoFlujo nodo);
}
