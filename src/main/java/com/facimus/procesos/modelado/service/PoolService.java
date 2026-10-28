package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.PoolRequest;
import com.facimus.procesos.modelado.controller.dto.EditarPoolRequest;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;
import java.util.List;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;

public interface PoolService {

    Pool crear(Long empresaId, Long procesoId, String nombre, TipoParticipante tipoParticipante, boolean cajaNegra);

    Pool editar(Long empresaId, Long poolId, String nombre, TipoParticipante tipoParticipante);

    Pool editar(Long empresaId, Long poolId, String nombre, TipoParticipante tipoParticipante, boolean cajaNegra);

    Pool verificarEliminable(Long empresaId, Long poolId);

    void eliminarRegistro(Pool pool);

    List<Pool> listarPorProceso(Long empresaId, Long procesoId);

    Pool obtener(Long empresaId, Long poolId);

    boolean existe(Long empresaId, Long poolId);

    PoolResponse crear(Long empresaId, Long procesoId, PoolRequest request);

    PoolResponse editar(Long empresaId, Long poolId, EditarPoolRequest request);

    List<PoolResponse> consultarPorProceso(Long empresaId, Long procesoId);

    PoolResponse consultarDetalle(Long empresaId, Long poolId);
}
