package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GatewayService {

    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final ArcoService arcoService;
    private final AuditoriaModeladoService auditoriaModeladoService;

    @Transactional
    public Gateway crear(Long empresaId, Long laneId, String nombre, TipoGateway tipoGateway, int posX, int posY) {
        Lane lane = laneService.obtener(empresaId, laneId);
        validarProcesoActivo(lane.getPool().getProceso());
        if (lane.getPool().isCajaNegra()) {
            throw new ReglaNegocioException("Un pool de caja negra no puede contener gateways.");
        }
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Gateway gateway = new Gateway();
        gateway.setEmpresa(lane.getEmpresa());
        gateway.setLane(lane);
        gateway.setNombre(nombre);
        gateway.setTipoGateway(tipoGateway);
        gateway.setPosicionX(posX);
        gateway.setPosicionY(posY);
        gateway = (Gateway) nodoFlujoService.guardar(gateway);
        auditoriaModeladoService.registrar(lane.getPool().getProceso(),
                "Gateway creado: " + gateway.getNombre() + " (" + gateway.getTipoGateway() + ").");
        return gateway;
    }

    @Transactional
    public Gateway editar(Long empresaId, Long gatewayId, String nombre, TipoGateway tipoGateway, int posX, int posY) {
        Gateway gateway = obtener(empresaId, gatewayId);
        validarProcesoActivo(gateway.getLane().getPool().getProceso());
        Long procesoId = gateway.getLane().getPool().getProceso().getId();
        if (!gateway.getNombre().equalsIgnoreCase(nombre)
                && nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        if (tipoGateway == TipoGateway.PARALELO && gateway.getTipoGateway() != TipoGateway.PARALELO) {
            arcoService.listarPorOrigen(empresaId, gatewayId).stream()
                    .filter(arco -> arco.isActivo())
                    .forEach(arco -> {
                        arco.setCondicion(null);
                        arcoService.guardar(arco);
                    });
        }
        if ((tipoGateway == TipoGateway.EXCLUSIVO || tipoGateway == TipoGateway.INCLUSIVO)
                && gateway.getTipoGateway() == TipoGateway.PARALELO) {
            boolean arcosSinCondicion = arcoService.listarPorOrigen(empresaId, gatewayId).stream()
                    .filter(arco -> arco.isActivo())
                    .anyMatch(arco -> !org.springframework.util.StringUtils.hasText(arco.getCondicion()));
            if (arcosSinCondicion) {
                throw new ReglaNegocioException(
                        "No se puede cambiar a " + tipoGateway + " porque existen arcos salientes sin condicion.");
            }
        }
        gateway.setNombre(nombre);
        gateway.setTipoGateway(tipoGateway);
        gateway.setPosicionX(posX);
        gateway.setPosicionY(posY);
        gateway = (Gateway) nodoFlujoService.guardar(gateway);
        auditoriaModeladoService.registrar(gateway.getLane().getPool().getProceso(),
                "Gateway editado: " + gateway.getNombre() + ".");
        return gateway;
    }

    @Transactional(readOnly = true)
    public ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long gatewayId) {
        Gateway gateway = obtener(empresaId, gatewayId);
        List<String> advertencias = new java.util.ArrayList<>();

        long entradas = arcoService.listarPorDestino(empresaId, gatewayId)
                .stream().filter(arco -> arco.isActivo()).count();
        long salidas = arcoService.listarPorOrigen(empresaId, gatewayId)
                .stream().filter(arco -> arco.isActivo()).count();

        if (entradas > 0 || salidas > 0) {
            advertencias.add("Se eliminaran las conexiones asociadas al gateway " + gateway.getNombre() + ".");
        }
        if (salidas >= 2) {
            advertencias.add("La eliminacion rompe una ramificacion con " + salidas + " salidas.");
        }
        return new ImpactoEliminacion(!advertencias.isEmpty(), advertencias);
    }

    @Transactional
    public void eliminar(Long empresaId, Long gatewayId) {
        Gateway gateway = obtener(empresaId, gatewayId);
        validarProcesoActivo(gateway.getLane().getPool().getProceso());
        var proceso = gateway.getLane().getPool().getProceso();
        String nombre = gateway.getNombre();
        arcoService.desactivarPorNodo(empresaId, gatewayId);
        gateway.setActivo(false);
        nodoFlujoService.guardar(gateway);
        auditoriaModeladoService.registrar(proceso, "Gateway eliminado (baja logica): " + nombre + ".");
    }

    @Transactional(readOnly = true)
    public Gateway obtener(Long empresaId, Long gatewayId) {
        return nodoFlujoService.buscar(empresaId, gatewayId)
                .filter(nodo -> nodo.isActivo())
                .filter(Gateway.class::isInstance)
                .map(Gateway.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gateway no encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Gateway> listarPorLane(Long empresaId, Long laneId) {
        laneService.obtener(empresaId, laneId);
        return nodoFlujoService.listarPorLane(empresaId, laneId).stream()
                .filter(nodo -> nodo.isActivo())
                .filter(Gateway.class::isInstance)
                .map(Gateway.class::cast)
                .toList();
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
