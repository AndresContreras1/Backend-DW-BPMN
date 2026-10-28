package com.facimus.procesos.modelado.service;

import java.util.ArrayList;
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
import com.facimus.procesos.modelado.controller.dto.ValidacionModeloResponse;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;

import lombok.RequiredArgsConstructor;

/**
 * Validaciones de coherencia del modelo. obtenerAdvertencias las reune todas sin lanzar (para que el
 * usuario vea el estado del modelo mientras trabaja); la publicacion usa la misma lista y falla con
 * 409 si no esta vacia.
 */
@Service
@RequiredArgsConstructor
public class ValidacionModeloServiceImpl implements ValidacionModeloService {

    private final PoolService poolService;
    private final LaneService laneService;
    private final NodoFlujoService nodoFlujoService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    @Transactional(readOnly = true)
    @Override
    public void validarParaPublicacion(Long empresaId, Long procesoId) {
        ejecutarValidaciones(empresaId, procesoId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> obtenerAdvertencias(Long empresaId, Long procesoId) {
        List<String> advertencias = new ArrayList<>();
        validarPools(empresaId, procesoId, advertencias);
        validarNodosYGateways(empresaId, procesoId, advertencias);
        validarMensajes(empresaId, procesoId, advertencias);
        return advertencias;
    }

    @Transactional(readOnly = true)
    @Override
    public ValidacionModeloResponse consultarValidacion(Long empresaId, Long procesoId) {
        List<String> advertencias = obtenerAdvertencias(empresaId, procesoId);
        return new ValidacionModeloResponse(advertencias.isEmpty(), advertencias);
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
        List<String> advertencias = obtenerAdvertencias(empresaId, procesoId);
        if (!advertencias.isEmpty()) {
            throw new ReglaNegocioException("El proceso no se puede publicar: " + String.join(" ", advertencias));
        }
    }

    private void validarPools(Long empresaId, Long procesoId, List<String> advertencias) {
        List<Pool> pools = poolService.listarPorProceso(empresaId, procesoId);
        var lanesPorPool = laneService.listarPorProceso(empresaId, procesoId).stream()
                .collect(Collectors.groupingBy(lane -> lane.getPool().getId()));
        pools.forEach(pool -> {
            if (pool.isCajaNegra() && !lanesPorPool.getOrDefault(pool.getId(), List.of()).isEmpty()) {
                advertencias.add("El pool de caja negra \"" + pool.getNombre() + "\" no puede contener lanes.");
            }
        });

        List<Pool> poolesEmpresa = pools.stream()
                .filter(pool -> pool.getTipoParticipante() == TipoParticipante.EMPRESA).toList();
        if (poolesEmpresa.size() != 1) {
            advertencias.add("El proceso debe tener exactamente un pool de tipo EMPRESA (tiene "
                    + poolesEmpresa.size() + ").");
        } else if (poolesEmpresa.get(0).isCajaNegra()) {
            advertencias.add("El pool EMPRESA \"" + poolesEmpresa.get(0).getNombre()
                    + "\" no puede ser de caja negra.");
        }
    }

    private void validarNodosYGateways(Long empresaId, Long procesoId, List<String> advertencias) {
        var nodos = nodoFlujoService.listarActivosPorProceso(empresaId, procesoId);
        var arcos = arcoService.listarActivosPorProceso(empresaId, procesoId);
        var mensajes = mensajeService.listarPorProceso(empresaId, procesoId);

        Set<Long> catchConMensaje = mensajes.stream()
                .filter(m -> m.getEventoCatch() != null)
                .map(m -> m.getEventoCatch().getId())
                .collect(Collectors.toSet());
        Map<Long, List<Arco>> salientesPorNodo = arcos.stream()
                .collect(Collectors.groupingBy(arco -> arco.getOrigen().getId()));
        Map<Long, List<Arco>> entrantesPorNodo = arcos.stream()
                .collect(Collectors.groupingBy(arco -> arco.getDestino().getId()));

        nodos.forEach(nodo -> {
            var salientes = salientesPorNodo.getOrDefault(nodo.getId(), List.of());
            var entrantes = entrantesPorNodo.getOrDefault(nodo.getId(), List.of());

            if (nodo instanceof Gateway gateway) {
                boolean convergente = entrantes.size() >= 2;
                if (convergente && salientes.isEmpty()) {
                    advertencias.add("El gateway convergente \"" + gateway.getNombre()
                            + "\" debe tener una salida.");
                } else if (!convergente && salientes.size() < 2) {
                    advertencias.add("El gateway divergente \"" + gateway.getNombre()
                            + "\" debe tener al menos dos salidas.");
                }
                if (salientes.size() >= 2
                        && (gateway.getTipoGateway() == TipoGateway.EXCLUSIVO
                                || gateway.getTipoGateway() == TipoGateway.INCLUSIVO)
                        && salientes.stream().anyMatch(arco -> !StringUtils.hasText(arco.getCondicion()))) {
                    advertencias.add("Todos los arcos salientes del gateway \"" + gateway.getNombre()
                            + "\" deben tener condicion.");
                }
            }

            if (nodo instanceof EventoMensaje evento) {
                boolean esCatch = evento.getTipoEvento() == TipoEventoMensaje.CATCH_INICIO
                        || evento.getTipoEvento() == TipoEventoMensaje.CATCH_INTERMEDIO;
                if (evento.getTipoEvento() == TipoEventoMensaje.CATCH_INICIO && !entrantes.isEmpty()) {
                    advertencias.add("El Message Catch de inicio \"" + evento.getNombre()
                            + "\" no puede tener arcos entrantes.");
                }
                if (esCatch && !evento.isOrigenExterno() && !catchConMensaje.contains(evento.getId())) {
                    advertencias.add("El Message Catch \"" + evento.getNombre()
                            + "\" no tiene un mensaje vinculado ni origen externo.");
                }
                if (evento.getTipoEvento() == TipoEventoMensaje.CATCH_INTERMEDIO
                        && !StringUtils.hasText(evento.getClaveCorrelacion())) {
                    advertencias.add("El Message Catch intermedio \"" + evento.getNombre()
                            + "\" debe tener clave de correlacion.");
                }
            }
        });
    }

    private void validarMensajes(Long empresaId, Long procesoId, List<String> advertencias) {
        Set<String> claves = new HashSet<>();
        boolean ambiguedadReportada = false;
        for (Mensaje mensaje : mensajeService.listarPorProceso(empresaId, procesoId)) {
            String nombre = mensaje.getNombre();
            String clave = mensaje.getClaveCorrelacion();
            if (mensaje.getEventoThrow() == null) {
                advertencias.add("El mensaje \"" + nombre + "\" debe tener un Message Throw asociado.");
            }
            if (mensaje.getEventoCatch() == null && mensaje.getTipoDestinoExterno() == null) {
                advertencias.add("El mensaje \"" + nombre
                        + "\" no tiene receptor: debe tener un Message Catch o un destino externo.");
            }
            if (!StringUtils.hasText(clave)) {
                advertencias.add("El mensaje \"" + nombre + "\" debe tener clave de correlacion.");
            } else {
                comprobarCoherencia(advertencias, mensaje, mensaje.getEventoThrow(), "Throw");
                comprobarCoherencia(advertencias, mensaje, mensaje.getEventoCatch(), "Catch");
            }
            if (correlacionService.buscarPorMensaje(empresaId, mensaje.getId()).isEmpty()) {
                advertencias.add("El mensaje \"" + nombre + "\" debe tener correlacion definida.");
            }
            if (StringUtils.hasText(clave)) {
                String llave = nombre.trim().toLowerCase() + "|" + clave.trim().toLowerCase();
                if (!claves.add(llave) && !ambiguedadReportada) {
                    advertencias.add("Hay mensajes ambiguos con el mismo nombre y clave de correlacion.");
                    ambiguedadReportada = true;
                }
            }
            if (mensaje.getTipoDestinoExterno() != null
                    && (mensaje.getPoolDestino().getTipoParticipante() != TipoParticipante.SISTEMA_EXTERNO
                            || !mensaje.getPoolDestino().isCajaNegra()
                            || !StringUtils.hasText(mensaje.getDestinoExterno()))) {
                advertencias.add("Los mensajes externos deben apuntar a un pool SISTEMA_EXTERNO de caja negra "
                        + "y documentar el destino (mensaje \"" + nombre + "\").");
            }
        }
    }

    private void comprobarCoherencia(List<String> advertencias, Mensaje mensaje, EventoMensaje evento,
            String lado) {
        if (evento != null && !mensaje.getClaveCorrelacion().trim().equalsIgnoreCase(
                evento.getClaveCorrelacion() != null ? evento.getClaveCorrelacion().trim() : "")) {
            advertencias.add("La clave de correlacion del Message " + lado + " \"" + evento.getNombre()
                    + "\" no coincide con la del mensaje \"" + mensaje.getNombre() + "\".");
        }
    }
}
