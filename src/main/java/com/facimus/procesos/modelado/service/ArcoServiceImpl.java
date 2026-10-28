package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.ArcoRequest;
import com.facimus.procesos.modelado.controller.dto.EditarArcoRequest;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import org.modelmapper.ModelMapper;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArcoServiceImpl implements ArcoService {

    private final ArcoRepository arcoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final PoolService poolService;
    private final AuditoriaModeladoService auditoriaModeladoService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public Arco crear(Long empresaId, Long origenId, Long destinoId, String etiqueta, String condicion) {
        if (origenId.equals(destinoId)) {
            throw new ReglaNegocioException("Un arco no puede tener el mismo nodo como origen y destino.");
        }
        NodoFlujo origen = nodoFlujoService.obtener(empresaId, origenId);
        NodoFlujo destino = nodoFlujoService.obtener(empresaId, destinoId);
        validarProcesoActivo(origen.getLane().getPool().getProceso());

        validarConexion(empresaId, origen, destino, condicion, null);

        Arco arco = new Arco();
        arco.setEmpresa(origen.getEmpresa());
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setPool(origen.getLane().getPool());
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        arco = arcoRepository.save(arco);
        auditoriaModeladoService.registrar(origen.getLane().getPool().getProceso(),
                "Arco creado: " + origen.getNombre() + " -> " + destino.getNombre() + ".");
        return arco;
    }

    @Transactional
    @Override
    public Arco editar(Long empresaId, Long arcoId, String etiqueta, String condicion,
            Long origenId, Long destinoId) {
        Arco arco = obtener(empresaId, arcoId);
        validarProcesoActivo(arco.getPool().getProceso());
        NodoFlujo origen = arco.getOrigen();
        NodoFlujo destino = arco.getDestino();

        Long nuevoOrigenId = origenId != null ? origenId : origen.getId();
        Long nuevoDestinoId = destinoId != null ? destinoId : destino.getId();
        if (nuevoOrigenId.equals(nuevoDestinoId)) {
            throw new ReglaNegocioException("Un arco no puede tener el mismo nodo como origen y destino.");
        }
        if (origenId != null) {
            origen = nodoFlujoService.obtener(empresaId, origenId);
        }
        if (destinoId != null) {
            destino = nodoFlujoService.obtener(empresaId, destinoId);
        }

        validarConexion(empresaId, origen, destino, condicion, arcoId);
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setPool(origen.getLane().getPool());
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        arco = arcoRepository.save(arco);
        auditoriaModeladoService.registrar(origen.getLane().getPool().getProceso(),
                "Arco editado: " + origen.getNombre() + " -> " + destino.getNombre() + ".");
        return arco;
    }

    @Transactional(readOnly = true)
    @Override
    public ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long arcoId) {
        Arco arco = obtener(empresaId, arcoId);
        List<String> advertencias = new java.util.ArrayList<>();

        long otrasSalidas = arcoRepository.findAllByOrigenIdAndEmpresaId(arco.getOrigen().getId(), empresaId)
                .stream().filter(a -> a.isActivo() && !a.getId().equals(arcoId)).count();
        long otrasEntradas = arcoRepository.findAllByDestinoIdAndEmpresaId(arco.getDestino().getId(), empresaId)
                .stream().filter(a -> a.isActivo() && !a.getId().equals(arcoId)).count();

        if (otrasSalidas == 0) {
            advertencias.add("El nodo " + arco.getOrigen().getNombre() + " quedara sin salida.");
        }
        if (otrasEntradas == 0) {
            advertencias.add("El nodo " + arco.getDestino().getNombre() + " quedara sin entrada.");
        }
        return new ImpactoEliminacion(!advertencias.isEmpty(), advertencias);
    }

    @Transactional
    @Override
    public void eliminar(Long empresaId, Long arcoId) {
        Arco arco = obtener(empresaId, arcoId);
        validarProcesoActivo(arco.getPool().getProceso());
        var proceso = arco.getPool().getProceso();
        String descripcion = arco.getOrigen().getNombre() + " -> " + arco.getDestino().getNombre();
        arco.setActivo(false);
        arcoRepository.save(arco);
        auditoriaModeladoService.registrar(proceso, "Arco eliminado (baja logica): " + descripcion + ".");
    }

    @Transactional(readOnly = true)
    @Override
    public Arco obtener(Long empresaId, Long arcoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .filter(arco -> arco.isActivo())
                .orElseThrow(() -> new RecursoNoEncontradoException("Arco no encontrado."));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Arco> listarActivosPorProceso(Long empresaId, Long procesoId) {
        return arcoRepository.findAllByPool_ProcesoIdAndEmpresaId(procesoId, empresaId).stream()
                .filter(Arco::isActivo)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<Arco> listarPorPool(Long empresaId, Long poolId) {
        poolService.obtener(empresaId, poolId);
        return arcoRepository.findAllByPoolIdAndEmpresaId(poolId, empresaId).stream()
                .filter(arco -> arco.isActivo())
                .toList();
    }

    private void validarConexion(Long empresaId, NodoFlujo origen, NodoFlujo destino, String condicion,
            Long arcoActualId) {
        if (!origen.isActivo() || !destino.isActivo()) {
            throw new RecursoNoEncontradoException("Los nodos del arco deben estar activos.");
        }
        Pool poolOrigen = origen.getLane().getPool();
        Pool poolDestino = destino.getLane().getPool();
        if (!poolOrigen.getId().equals(poolDestino.getId())) {
            throw new ReglaNegocioException("El origen y el destino de un arco deben pertenecer al mismo pool.");
        }
        boolean mismosExtremos = arcoActualId != null
                && esMismoArco(arcoActualId, empresaId, origen.getId(), destino.getId());
        if (!mismosExtremos
                && arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(
                        origen.getId(), destino.getId(), empresaId)) {
            throw new ReglaNegocioException("Ya existe un arco entre estos dos nodos.");
        }
        boolean origenAdmiteCondicion = origen instanceof Gateway gatewayOrigen
                && (gatewayOrigen.getTipoGateway() == TipoGateway.EXCLUSIVO
                        || gatewayOrigen.getTipoGateway() == TipoGateway.INCLUSIVO);
        if (origenAdmiteCondicion) {
            // Un gateway de convergencia (2 o mas arcos entrantes) solo une ramas: su salida no decide nada.
            if (!StringUtils.hasText(condicion) && !esConvergencia(empresaId, origen.getId())) {
                throw new ReglaNegocioException(
                        "Un arco desde un gateway exclusivo o inclusivo requiere condicion "
                                + "(salvo que el gateway sea de convergencia, con 2 o mas arcos entrantes).");
            }
        } else if (StringUtils.hasText(condicion)) {
            // Se rechaza (409) en vez de descartarla en silencio: la condicion solo tiene sentido en
            // las salidas de un gateway exclusivo o inclusivo.
            throw new ReglaNegocioException("Solo un arco que sale de un gateway exclusivo o inclusivo puede llevar "
                    + "condicion; el nodo \"" + origen.getNombre() + "\" no la admite.");
        }
        if (destino instanceof EventoMensaje evento
                && evento.getTipoEvento() == TipoEventoMensaje.CATCH_INICIO) {
            throw new ReglaNegocioException("Un Message Catch de inicio no puede tener arcos entrantes.");
        }
    }

    private boolean esConvergencia(Long empresaId, Long nodoId) {
        return arcoRepository.findAllByDestinoIdAndEmpresaId(nodoId, empresaId).stream()
                .filter(Arco::isActivo).count() >= 2;
    }

    private boolean esMismoArco(Long arcoId, Long empresaId, Long origenId, Long destinoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .map(arco -> arco.getOrigen().getId().equals(origenId)
                        && arco.getDestino().getId().equals(destinoId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Arco> listarPorOrigen(Long empresaId, Long origenId) {
        return arcoRepository.findAllByOrigenIdAndEmpresaId(origenId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Arco> listarPorDestino(Long empresaId, Long destinoId) {
        return arcoRepository.findAllByDestinoIdAndEmpresaId(destinoId, empresaId);
    }

    @Transactional
    @Override
    public void desactivarPorNodo(Long empresaId, Long nodoId) {
        arcoRepository.findAllByOrigenIdAndEmpresaId(nodoId, empresaId).forEach(arco -> {
            arco.setActivo(false);
            arcoRepository.save(arco);
        });
        arcoRepository.findAllByDestinoIdAndEmpresaId(nodoId, empresaId).forEach(arco -> {
            arco.setActivo(false);
            arcoRepository.save(arco);
        });
    }

    @Transactional
    @Override
    public Arco guardar(Arco arco) {
        return arcoRepository.save(arco);
    }

    @Transactional
    @Override
    public ArcoResponse crear(Long empresaId, ArcoRequest request) {
        Arco arco = crear(empresaId, request.origenId(), request.destinoId(), request.etiqueta(),
                request.condicion());
        return modelMapper.map(arco, ArcoResponse.class);
    }

    @Transactional
    @Override
    public ArcoResponse editar(Long empresaId, Long arcoId, EditarArcoRequest request) {
        Arco arco = editar(empresaId, arcoId, request.etiqueta(), request.condicion(),
                request.origenId(), request.destinoId());
        return modelMapper.map(arco, ArcoResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public ArcoResponse consultarDetalle(Long empresaId, Long arcoId) {
        return modelMapper.map(obtener(empresaId, arcoId), ArcoResponse.class);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ArcoResponse> consultarPorPool(Long empresaId, Long poolId) {
        return listarPorPool(empresaId, poolId).stream()
                .map(a -> modelMapper.map(a, ArcoResponse.class)).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public ImpactoEliminacionResponse consultarImpactoEliminacion(Long empresaId, Long arcoId) {
        return modelMapper.map(evaluarImpactoEliminacion(empresaId, arcoId),
                ImpactoEliminacionResponse.class);
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
