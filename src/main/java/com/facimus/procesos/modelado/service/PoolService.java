package com.facimus.procesos.modelado.service;

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
}
