package com.facimus.procesos.modelado.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.NodoFlujo;

/**
 * Regla compartida para mover un nodo a otra lane al editarlo (la misma de ActividadService.editar):
 * la lane destino debe ser del mismo proceso, no puede ser un pool de caja negra y, si es de otro
 * pool, el nodo no puede tener arcos conectados.
 */
final class ReglasMovimientoNodo {

    private ReglasMovimientoNodo() {
    }

    /** Devuelve la lane destino validada, o null si no hay movimiento que hacer. */
    static Lane resolverLaneDestino(Long empresaId, NodoFlujo nodo, Long laneId, String plural,
            LaneService laneService, ArcoService arcoService) {
        if (laneId == null || laneId.equals(nodo.getLane().getId())) {
            return null;
        }
        Lane nuevoLane = laneService.obtener(empresaId, laneId);
        if (nuevoLane.getPool().isCajaNegra()) {
            throw new ReglaNegocioException("Un pool de caja negra no puede contener " + plural + ".");
        }
        Long procesoActual = nodo.getLane().getPool().getProceso().getId();
        if (!procesoActual.equals(nuevoLane.getPool().getProceso().getId())) {
            throw new ReglaNegocioException("El lane destino debe pertenecer al mismo proceso.");
        }
        if (!nodo.getLane().getPool().getId().equals(nuevoLane.getPool().getId())) {
            boolean tieneArcos = arcoService.listarPorOrigen(empresaId, nodo.getId()).stream()
                    .anyMatch(Arco::isActivo)
                    || arcoService.listarPorDestino(empresaId, nodo.getId()).stream().anyMatch(Arco::isActivo);
            if (tieneArcos) {
                throw new ReglaNegocioException(
                        "No se puede mover el nodo a otro pool porque tiene arcos conectados.");
            }
        }
        return nuevoLane;
    }
}
