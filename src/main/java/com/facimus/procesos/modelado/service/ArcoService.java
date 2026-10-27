package com.facimus.procesos.modelado.service;

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
public class ArcoService {

    private final ArcoRepository arcoRepository;
    private final NodoFlujoService nodoFlujoService;
    private final PoolService poolService;
    private final AuditoriaModeladoService auditoriaModeladoService;

    @Transactional
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
    public Arco obtener(Long empresaId, Long arcoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .filter(arco -> arco.isActivo())
                .orElseThrow(() -> new RecursoNoEncontradoException("Arco no encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Arco> listarActivosPorProceso(Long empresaId, Long procesoId) {
        return arcoRepository.findAllByPool_ProcesoIdAndEmpresaId(procesoId, empresaId).stream()
                .filter(Arco::isActivo)
                .toList();
    }

    @Transactional(readOnly = true)
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
        if (origen instanceof Gateway gatewayOrigen
                && (gatewayOrigen.getTipoGateway() == TipoGateway.EXCLUSIVO
                        || gatewayOrigen.getTipoGateway() == TipoGateway.INCLUSIVO)
                && !StringUtils.hasText(condicion)) {
            throw new ReglaNegocioException("Un arco desde un gateway exclusivo o inclusivo requiere condicion.");
        }
        if (destino instanceof EventoMensaje evento
                && evento.getTipoEvento() == TipoEventoMensaje.CATCH_INICIO) {
            throw new ReglaNegocioException("Un Message Catch de inicio no puede tener arcos entrantes.");
        }
    }

    private boolean esMismoArco(Long arcoId, Long empresaId, Long origenId, Long destinoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .map(arco -> arco.getOrigen().getId().equals(origenId)
                        && arco.getDestino().getId().equals(destinoId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Arco> listarPorOrigen(Long empresaId, Long origenId) {
        return arcoRepository.findAllByOrigenIdAndEmpresaId(origenId, empresaId);
    }

    @Transactional(readOnly = true)
    public List<Arco> listarPorDestino(Long empresaId, Long destinoId) {
        return arcoRepository.findAllByDestinoIdAndEmpresaId(destinoId, empresaId);
    }

    @Transactional
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
    public Arco guardar(Arco arco) {
        return arcoRepository.save(arco);
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
