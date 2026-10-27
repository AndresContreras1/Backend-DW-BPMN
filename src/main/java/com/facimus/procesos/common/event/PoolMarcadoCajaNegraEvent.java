package com.facimus.procesos.common.event;

/**
 * Publicado por PoolService.editar justo antes de persistir un pool con cajaNegra = true.
 * LaneService lo escucha para validar que el pool no tenga lanes, sin que PoolService dependa de
 * el directamente (evitaria un ciclo, ya que LaneService depende de PoolService). El listener se
 * ejecuta de forma sincrona, dentro de la misma transaccion: si lanza una excepcion, aborta la
 * edicion antes de guardar nada.
 */
public record PoolMarcadoCajaNegraEvent(Long empresaId, Long poolId) {
}
