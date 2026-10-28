package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.GatewayRequest;
import com.facimus.procesos.modelado.controller.dto.GatewayResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import org.modelmapper.ModelMapper;
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
public class GatewayServiceImpl implements GatewayService {

    private final NodoFlujoService nodoFlujoService;
    private final LaneService laneService;
    private final ArcoService arcoService;
    private final AuditoriaModeladoService auditoriaModeladoService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
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
    @Override
    public Gateway editar(Long empresaId, Long gatewayId, String nombre, TipoGateway tipoGateway, int posX, int posY) {
        return editar(empresaId, gatewayId, nombre, tipoGateway, posX, posY, null);
    }

    @Transactional
    @Override
    public Gateway editar(Long empresaId, Long gatewayId, String nombre, TipoGateway tipoGateway, int posX, int posY,
            Long laneId) {
        Gateway gateway = obtener(empresaId, gatewayId);
        validarProcesoActivo(gateway.getLane().getPool().getProceso());
        Long procesoId = gateway.getLane().getPool().getProceso().getId();
        if (!gateway.getNombre().equalsIgnoreCase(nombre)
                && nodoFlujoService.existeNombreEnProceso(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        Lane nuevoLane = ReglasMovimientoNodo.resolverLaneDestino(empresaId, gateway, laneId, "gateways",
                laneService, arcoService);
        long condicionesEliminadas = 0;
        if (tipoGateway == TipoGateway.PARALELO && gateway.getTipoGateway() != TipoGateway.PARALELO) {
            var conCondicion = arcoService.listarPorOrigen(empresaId, gatewayId).stream()
                    .filter(arco -> arco.isActivo())
                    .toList();
            for (var arco : conCondicion) {
                if (org.springframework.util.StringUtils.hasText(arco.getCondicion())) {
                    condicionesEliminadas++;
                }
                arco.setCondicion(null);
                arcoService.guardar(arco);
            }
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
        String nombreAnterior = gateway.getNombre();
        TipoGateway tipoAnterior = gateway.getTipoGateway();
        String laneAnterior = gateway.getLane().getNombre();
        gateway.setNombre(nombre);
        gateway.setTipoGateway(tipoGateway);
        gateway.setPosicionX(posX);
        gateway.setPosicionY(posY);
        if (nuevoLane != null) {
            gateway.setLane(nuevoLane);
        }
        gateway = (Gateway) nodoFlujoService.guardar(gateway);

        List<String> cambios = new java.util.ArrayList<>();
        if (!nombreAnterior.equals(nombre)) {
            cambios.add("nombre: " + nombreAnterior + " -> " + nombre);
        }
        if (tipoAnterior != tipoGateway) {
            cambios.add("tipo: " + tipoAnterior + " -> " + tipoGateway);
        }
        if (condicionesEliminadas > 0) {
            cambios.add("condiciones eliminadas en " + condicionesEliminadas + " arco(s) saliente(s)");
        }
        if (nuevoLane != null) {
            cambios.add("lane: " + laneAnterior + " -> " + nuevoLane.getNombre());
        }
        auditoriaModeladoService.registrar(gateway.getLane().getPool().getProceso(),
                "Gateway editado: " + gateway.getNombre()
                        + (cambios.isEmpty() ? "." : " (" + String.join("; ", cambios) + ")."));
        return gateway;
    }

    @Transactional(readOnly = true)
    @Override
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
        if (entradas >= 2 && salidas >= 1) {
            advertencias.add("La eliminacion rompe una convergencia de " + entradas + " entradas hacia "
                    + salidas + " salida(s).");
        }
        return new ImpactoEliminacion(!advertencias.isEmpty(), advertencias);
    }

    @Transactional
    @Override
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
    @Override
    public Gateway obtener(Long empresaId, Long gatewayId) {
        return nodoFlujoService.buscar(empresaId, gatewayId)
                .filter(nodo -> nodo.isActivo())
                .filter(Gateway.class::isInstance)
                .map(Gateway.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gateway no encontrado."));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Gateway> listarPorLane(Long empresaId, Long laneId) {
        laneService.obtener(empresaId, laneId);
        return nodoFlujoService.listarPorLane(empresaId, laneId).stream()
                .filter(nodo -> nodo.isActivo())
                .filter(Gateway.class::isInstance)
                .map(Gateway.class::cast)
                .toList();
    }

    @Transactional
    @Override
    public GatewayResponse crear(Long empresaId, Long laneId, GatewayRequest request) {
        Gateway gateway = crear(empresaId, laneId, request.nombre(), request.tipoGateway(),
                request.posicionX(), request.posicionY());
        return modelMapper.map(gateway, GatewayResponse.class);
    }

    @Transactional
    @Override
    public GatewayResponse editar(Long empresaId, Long gatewayId, GatewayRequest request) {
        Gateway gateway = editar(empresaId, gatewayId, request.nombre(), request.tipoGateway(),
                request.posicionX(), request.posicionY(), request.laneId());
        return modelMapper.map(gateway, GatewayResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public GatewayResponse consultarDetalle(Long empresaId, Long gatewayId) {
        return modelMapper.map(obtener(empresaId, gatewayId), GatewayResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public List<GatewayResponse> consultarPorLane(Long empresaId, Long laneId) {
        return listarPorLane(empresaId, laneId).stream()
                .map(g -> modelMapper.map(g, GatewayResponse.class)).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public ImpactoEliminacionResponse consultarImpactoEliminacion(Long empresaId, Long gatewayId) {
        return modelMapper.map(evaluarImpactoEliminacion(empresaId, gatewayId),
                ImpactoEliminacionResponse.class);
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
