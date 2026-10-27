package com.facimus.procesos.common.event;

/**
 * Publicado por ProcesoService.cambiarEstado justo antes de persistir la transicion a PUBLICADO.
 * ValidacionModeloService lo escucha para validar el modelo BPMN sin que ProcesoService dependa de
 * el directamente (evitaria un ciclo: ProcesoService -> ValidacionModeloService -> PoolService/
 * MensajeService -> ProcesoService). El listener se ejecuta de forma sincrona, dentro de la misma
 * transaccion: si lanza una excepcion, aborta la publicacion antes de guardar nada.
 */
public record ProcesoPublicacionEvent(Long empresaId, Long procesoId) {
}
