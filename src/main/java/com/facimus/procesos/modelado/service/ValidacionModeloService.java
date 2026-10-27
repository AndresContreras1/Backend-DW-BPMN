package com.facimus.procesos.modelado.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.event.ProcesoPublicacionEvent;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;

import lombok.RequiredArgsConstructor;

/** Validaciones de coherencia que deben cumplirse antes de publicar un proceso. */
@Service
@RequiredArgsConstructor
public class ValidacionModeloService {

    private final PoolService poolService;
    private final LaneService laneService;
    private final NodoFlujoService nodoFlujoService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    @Transactional(readOnly = true)
    public void validarParaPublicacion(Long empresaId, Long procesoId) {
        ejecutarValidaciones(empresaId, procesoId);
    }

    /**
     * Escucha ProcesoPublicacionEvent (publicado por ProcesoService.cambiarEstado antes de guardar
     * la transicion a PUBLICADO) para que esta validacion se cumpla sin que ProcesoService dependa
     * de este servicio directamente. Se ejecuta de forma sincrona, dentro de la misma transaccion:
     * si lanza ReglaNegocioException, aborta la publicacion.
     */
    @EventListener
    public void alPublicarProceso(ProcesoPublicacionEvent evento) {
        ejecutarValidaciones(evento.empresaId(), evento.procesoId());
    }

    private void ejecutarValidaciones(Long empresaId, Long procesoId) {
        validarPools(empresaId, procesoId);
        validarNodosYGateways(empresaId, procesoId);
        validarMensajes(empresaId, procesoId);
    }

    private void validarPools(Long empresaId, Long procesoId) {
        var pools = poolService.listarPorProceso(empresaId, procesoId);
        var lanesPorPool = laneService.listarPorProceso(empresaId, procesoId).stream()
                .collect(Collectors.groupingBy(lane -> lane.getPool().getId()));
        pools.forEach(pool -> {
            if (pool.isCajaNegra() && !lanesPorPool.getOrDefault(pool.getId(), List.of()).isEmpty()) {
                throw new ReglaNegocioException(
                        "El pool de caja negra \"" + pool.getNombre() + "\" no puede contener lanes.");
            }
        });
    }

    private void validarNodosYGateways(Long empresaId, Long procesoId) {
        var nodos = nodoFlujoService.listarActivosPorProceso(empresaId, procesoId);
        var arcos = arcoService.listarActivosPorProceso(empresaId, procesoId);

        Map<Long, List<com.facimus.procesos.modelado.model.Arco>> salientesPorNodo = arcos.stream()
                .collect(Collectors.groupingBy(arco -> arco.getOrigen().getId()));
        Map<Long, List<com.facimus.procesos.modelado.model.Arco>> entrantesPorNodo = arcos.stream()
                .collect(Collectors.groupingBy(arco -> arco.getDestino().getId()));

        nodos.forEach(nodo -> {
            var salientes = salientesPorNodo.getOrDefault(nodo.getId(), List.of());
            var entrantes = entrantesPorNodo.getOrDefault(nodo.getId(), List.of());

            if (nodo instanceof Gateway gateway) {
                boolean divergente = entrantes.size() <= 1;
                if (divergente && salientes.size() < 2) {
                    throw new ReglaNegocioException(
                            "El gateway divergente \"" + gateway.getNombre()
                                    + "\" debe tener al menos dos salidas.");
                }
                if (gateway.getTipoGateway() == TipoGateway.EXCLUSIVO
                        || gateway.getTipoGateway() == TipoGateway.INCLUSIVO) {
                    boolean sinCondicion = salientes.stream()
                            .anyMatch(arco -> !StringUtils.hasText(arco.getCondicion()));
                    if (sinCondicion) {
                        throw new ReglaNegocioException(
                                "Todos los arcos salientes del gateway \"" + gateway.getNombre()
                                        + "\" deben tener condicion.");
                    }
                }
            }

            if (nodo instanceof EventoMensaje evento
                    && evento.getTipoEvento() == TipoEventoMensaje.CATCH_INICIO
                    && !entrantes.isEmpty()) {
                throw new ReglaNegocioException(
                        "El Message Catch de inicio \"" + evento.getNombre()
                                + "\" no puede tener arcos entrantes.");
            }
        });
    }

    private void validarMensajes(Long empresaId, Long procesoId) {
        Set<String> claves = new HashSet<>();
        for (Mensaje mensaje : mensajeService.listarPorProceso(empresaId, procesoId)) {
            if (mensaje.getEventoThrow() == null) {
                throw new ReglaNegocioException(
                        "El mensaje \"" + mensaje.getNombre() + "\" debe tener un Message Throw asociado.");
            }
            if (mensaje.getEventoCatch() == null && mensaje.getTipoDestinoExterno() == null) {
                throw new ReglaNegocioException(
                        "El mensaje \"" + mensaje.getNombre()
                                + "\" debe tener un Message Catch o un destino externo.");
            }
            if (!StringUtils.hasText(mensaje.getClaveCorrelacion())) {
                throw new ReglaNegocioException(
                        "El mensaje \"" + mensaje.getNombre() + "\" debe tener clave de correlacion.");
            }
            if (correlacionService.buscarPorMensaje(empresaId, mensaje.getId()).isEmpty()) {
                throw new ReglaNegocioException(
                        "El mensaje \"" + mensaje.getNombre() + "\" debe tener correlacion definida.");
            }
            String llave = mensaje.getNombre().trim().toLowerCase() + "|"
                    + mensaje.getClaveCorrelacion().trim().toLowerCase();
            if (!claves.add(llave)) {
                throw new ReglaNegocioException(
                        "Hay mensajes ambiguos con el mismo nombre y clave de correlacion.");
            }
            if (mensaje.getTipoDestinoExterno() != null) {
                if (mensaje.getPoolDestino().getTipoParticipante() != TipoParticipante.SISTEMA_EXTERNO
                        || !mensaje.getPoolDestino().isCajaNegra()
                        || !StringUtils.hasText(mensaje.getDestinoExterno())) {
                    throw new ReglaNegocioException(
                            "Los mensajes externos deben apuntar a un pool SISTEMA_EXTERNO de caja negra y documentar el destino.");
                }
            }
        }
    }
}
