package com.facimus.procesos.common.event;

/**
 * Publicado por PoolService.editar justo antes de persistir un cambio del tipo de participante o
 * de la caja negra de un pool. MensajeService lo escucha para impedir el cambio si el pool es el
 * destino externo de mensajes activos, sin que PoolService dependa de el (MensajeService ya depende
 * de PoolService). Se ejecuta de forma sincrona, en la misma transaccion.
 */
public record PoolParticipanteModificadoEvent(Long empresaId, Long poolId) {
}
