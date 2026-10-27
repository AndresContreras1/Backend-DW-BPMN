package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.PoolMarcadoCajaNegraEvent;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.LaneRepository;

import lombok.RequiredArgsConstructor;

/**
 * La resolucion del RolProceso a asignar la hace LaneController (via RolProcesoService)
 * antes de llamar a crear()/editar(), para no crear una dependencia circular
 * LaneService<->RolProcesoService. RolProcesoService si depende de LaneService
 * (direccion unica: para saber en que lanes/procesos esta en uso un rol).
 */
@Service
@RequiredArgsConstructor
public class LaneService {

    private final LaneRepository laneRepository;
    private final PoolService poolService;
    private final NodoFlujoService nodoFlujoService;
    private final AuditoriaModeladoService auditoriaModeladoService;

    @Transactional
    public Lane crear(Long empresaId, Long poolId, String nombre, RolProceso rolProceso) {
        Pool pool = poolService.obtener(empresaId, poolId);
        validarProcesoActivo(pool.getProceso());
        if (pool.isCajaNegra()) {
            throw new ReglaNegocioException("Un pool de caja negra no puede contener lanes.");
        }
        int orden = laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId).size();

        Lane lane = new Lane();
        lane.setEmpresa(pool.getEmpresa());
        lane.setPool(pool);
        lane.setNombre(nombre);
        lane.setRolProceso(rolProceso);
        lane.setOrden(orden);
        lane = laneRepository.save(lane);
        auditoriaModeladoService.registrar(pool.getProceso(), "Lane creada: " + lane.getNombre() + ".");
        return lane;
    }

    @Transactional
    public Lane editar(Long empresaId, Long laneId, String nombre, RolProceso rolProceso) {
        Lane lane = obtener(empresaId, laneId);
        validarProcesoActivo(lane.getPool().getProceso());
        lane.setNombre(nombre);
        lane.setRolProceso(rolProceso);
        lane = laneRepository.save(lane);
        auditoriaModeladoService.registrar(lane.getPool().getProceso(), "Lane editada: " + lane.getNombre() + ".");
        return lane;
    }

    @Transactional
    public void eliminar(Long empresaId, Long laneId) {
        Lane lane = obtener(empresaId, laneId);
        validarProcesoActivo(lane.getPool().getProceso());
        if (nodoFlujoService.tieneNodosActivos(empresaId, laneId)) {
            throw new ReglaNegocioException("La lane \"" + lane.getNombre()
                    + "\" contiene elementos activos; primero deben reasignarse.");
        }
        var proceso = lane.getPool().getProceso();
        String nombre = lane.getNombre();
        laneRepository.delete(lane);
        auditoriaModeladoService.registrar(proceso, "Lane eliminada: " + nombre + ".");
    }

    @Transactional
    public List<Lane> reordenar(Long empresaId, Long poolId, List<Long> laneIds) {
        Pool pool = poolService.obtener(empresaId, poolId);
        validarProcesoActivo(pool.getProceso());
        List<Lane> lanes = laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId);
        java.util.Set<Long> idsUnicos = new java.util.LinkedHashSet<>(laneIds);
        if (idsUnicos.size() != laneIds.size()) {
            throw new ReglaNegocioException("La lista de IDs contiene duplicados.");
        }
        if (lanes.size() != laneIds.size()) {
            throw new ReglaNegocioException("La lista de IDs debe contener exactamente todas las lanes del pool.");
        }
        java.util.Map<Long, Lane> laneMap = new java.util.HashMap<>();
        lanes.forEach(l -> laneMap.put(l.getId(), l));
        for (Long id : laneIds) {
            if (!laneMap.containsKey(id)) {
                throw new ReglaNegocioException("El lane con ID " + id + " no pertenece a este pool.");
            }
        }
        for (int i = 0; i < laneIds.size(); i++) {
            Lane lane = laneMap.get(laneIds.get(i));
            lane.setOrden(i);
            laneRepository.save(lane);
        }
        auditoriaModeladoService.registrar(pool.getProceso(), "Lanes reordenadas en el pool " + pool.getNombre() + ".");
        return laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId);
    }

    @Transactional(readOnly = true)
    public List<Lane> listarPorPool(Long empresaId, Long poolId) {
        poolService.obtener(empresaId, poolId);
        return buscarLanesPorPool(empresaId, poolId);
    }

    private List<Lane> buscarLanesPorPool(Long empresaId, Long poolId) {
        return laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId);
    }

    @Transactional(readOnly = true)
    public Lane obtener(Long empresaId, Long laneId) {
        return laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
    }

    @Transactional(readOnly = true)
    public List<Lane> listarPorProceso(Long empresaId, Long procesoId) {
        return laneRepository.findAllByPool_ProcesoIdAndEmpresaId(procesoId, empresaId);
    }

    @Transactional(readOnly = true)
    public List<Lane> listarPorRolProceso(Long empresaId, Long rolProcesoId) {
        return laneRepository.findAllByRolProcesoIdAndEmpresaId(rolProcesoId, empresaId);
    }

    @Transactional(readOnly = true)
    public long contarPorRolProceso(Long empresaId, Long rolProcesoId) {
        return laneRepository.countByRolProcesoIdAndEmpresaId(rolProcesoId, empresaId);
    }

    @Transactional
    public void eliminarPorPool(Long empresaId, Long poolId) {
        laneRepository.deleteAll(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId));
    }

    /**
     * Escucha PoolMarcadoCajaNegraEvent (publicado por PoolService.editar antes de guardar un
     * pool con cajaNegra = true) para que esta validacion se cumpla sin que PoolService dependa
     * de este servicio directamente. Se ejecuta de forma sincrona, dentro de la misma
     * transaccion: si lanza ReglaNegocioException, aborta la edicion del pool.
     */
    @EventListener
    public void alMarcarPoolCajaNegra(PoolMarcadoCajaNegraEvent evento) {
        if (!buscarLanesPorPool(evento.empresaId(), evento.poolId()).isEmpty()) {
            throw new ReglaNegocioException("No se puede marcar como caja negra un pool que contiene lanes.");
        }
    }

    @Transactional(readOnly = true)
    public boolean existe(Long empresaId, Long laneId) {
        return laneRepository.existsByIdAndEmpresaId(laneId, empresaId);
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
