package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.PoolMarcadoCajaNegraEvent;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/**
 * La cascada de borrado de lanes (LaneService) la orquesta PoolOrquestadorService, para no
 * crear una dependencia circular PoolService<->LaneService. La validacion de "pool sin lanes
 * antes de marcarlo caja negra" no depende de LaneService directamente (eso si seria un ciclo,
 * ya que LaneService depende de PoolService): en vez de eso, editar() publica
 * PoolMarcadoCajaNegraEvent y LaneService lo escucha, para que la regla se cumpla incluso si
 * algo llama a este metodo directamente, sin pasar por un Controller u orquestador.
 */
@Service
@RequiredArgsConstructor
public class PoolService {

    private final PoolRepository poolRepository;
    private final ProcesoService procesoService;
    private final NodoFlujoService nodoFlujoService;
    private final AuditoriaModeladoService auditoriaModeladoService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Pool crear(Long empresaId, Long procesoId, String nombre, TipoParticipante tipoParticipante,
            boolean cajaNegra) {
        Proceso proceso = procesoService.obtener(empresaId, procesoId);
        int orden = poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId).size();

        Pool pool = new Pool();
        pool.setEmpresa(proceso.getEmpresa());
        pool.setProceso(proceso);
        pool.setNombre(nombre);
        pool.setTipoParticipante(tipoParticipante);
        pool.setCajaNegra(cajaNegra);
        pool.setOrden(orden);
        pool = poolRepository.save(pool);
        auditoriaModeladoService.registrar(proceso, "Pool creado: " + pool.getNombre() + ".");
        return pool;
    }

    @Transactional
    public Pool editar(Long empresaId, Long poolId, String nombre, TipoParticipante tipoParticipante) {
        Pool pool = obtener(empresaId, poolId);
        return editar(empresaId, poolId, nombre, tipoParticipante, pool.isCajaNegra());
    }

    /**
     * Si se pide activar cajaNegra, publica PoolMarcadoCajaNegraEvent antes de guardar;
     * LaneService lo escucha y rechaza la operacion si el pool tiene lanes.
     */
    @Transactional
    public Pool editar(Long empresaId, Long poolId, String nombre, TipoParticipante tipoParticipante,
            boolean cajaNegra) {
        Pool pool = obtener(empresaId, poolId);
        validarProcesoActivo(pool.getProceso());
        if (cajaNegra) {
            eventPublisher.publishEvent(new PoolMarcadoCajaNegraEvent(empresaId, poolId));
        }
        pool.setNombre(nombre);
        pool.setTipoParticipante(tipoParticipante);
        pool.setCajaNegra(cajaNegra);
        pool = poolRepository.save(pool);
        auditoriaModeladoService.registrar(pool.getProceso(), "Pool editado: " + pool.getNombre() + ".");
        return pool;
    }

    /**
     * Valida que el pool no tenga elementos de flujo activos (usa solo NodoFlujoService,
     * sin cruzar a LaneService) y devuelve el pool listo para eliminar. El llamador
     * (PoolController) debe borrar las lanes del pool con LaneService.eliminarPorPool()
     * antes de llamar a eliminarRegistro(), porque Lane->Pool es una FK obligatoria.
     */
    @Transactional(readOnly = true)
    public Pool verificarEliminable(Long empresaId, Long poolId) {
        Pool pool = obtener(empresaId, poolId);
        if (nodoFlujoService.tieneNodosActivosEnPool(empresaId, poolId)) {
            throw new ReglaNegocioException("El pool \"" + pool.getNombre()
                    + "\" tiene lanes con actividades o elementos activos; no se puede eliminar.");
        }
        return pool;
    }

    @Transactional
    public void eliminarRegistro(Pool pool) {
        validarProcesoActivo(pool.getProceso());
        var proceso = pool.getProceso();
        String nombre = pool.getNombre();
        poolRepository.delete(pool);
        auditoriaModeladoService.registrar(proceso, "Pool eliminado: " + nombre + ".");
    }

    @Transactional(readOnly = true)
    public List<Pool> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoService.existe(empresaId, procesoId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId);
    }

    @Transactional(readOnly = true)
    public Pool obtener(Long empresaId, Long poolId) {
        return poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado."));
    }

    @Transactional(readOnly = true)
    public boolean existe(Long empresaId, Long poolId) {
        return poolRepository.existsByIdAndEmpresaId(poolId, empresaId);
    }

    private void validarProcesoActivo(Proceso proceso) {
        if (!proceso.isActivo()) {
            throw new ReglaNegocioException("No se permite modelar en un proceso inactivo.");
        }
    }
}
