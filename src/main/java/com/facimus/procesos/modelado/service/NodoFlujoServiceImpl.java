package com.facimus.procesos.modelado.service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NodoFlujoServiceImpl implements NodoFlujoService {

    private final NodoFlujoRepository nodoFlujoRepository;

    @Transactional(readOnly = true)
    @Override
    public NodoFlujo obtener(Long empresaId, Long nodoId) {
        return nodoFlujoRepository.findByIdAndEmpresaId(nodoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nodo no encontrado."));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<NodoFlujo> buscar(Long empresaId, Long nodoId) {
        return nodoFlujoRepository.findByIdAndEmpresaId(nodoId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public List<NodoFlujo> listarActivosPorProceso(Long empresaId, Long procesoId) {
        return nodoFlujoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaId(procesoId, empresaId).stream()
                .filter(NodoFlujo::isActivo)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<NodoFlujo> listarPorLane(Long empresaId, Long laneId) {
        return nodoFlujoRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean tieneNodosActivos(Long empresaId, Long laneId) {
        return nodoFlujoRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId).stream()
                .anyMatch(NodoFlujo::isActivo);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean tieneNodosActivosEnPool(Long empresaId, Long poolId) {
        return nodoFlujoRepository.existsByLane_PoolIdAndEmpresaIdAndActivoTrue(poolId, empresaId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existeNombreEnProceso(String nombre, Long procesoId, Long empresaId) {
        return nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndActivoTrue(
                nombre, procesoId, empresaId);
    }

    @Override
    public NodoFlujo guardar(NodoFlujo nodo) {
        return nodoFlujoRepository.save(nodo);
    }
}
