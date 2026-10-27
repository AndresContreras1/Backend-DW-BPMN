package com.facimus.procesos.gestion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.PoolService;

import lombok.RequiredArgsConstructor;

/**
 * Orquestador transaccional del caso de uso HU-04 (crear proceso + su pool inicial). Depende de
 * ProcesoService y PoolService, ninguno de los cuales depende de este orquestador, asi que no
 * reintroduce el ciclo ProcesoService<->PoolService que el refactor elimino.
 */
@Service
@RequiredArgsConstructor
public class ProcesoOrquestadorService {

    private final ProcesoService procesoService;
    private final PoolService poolService;

    @Transactional
    public Proceso crearConPoolInicial(Long empresaId, Long usuarioId, String nombre, String descripcion,
            String categoria) {
        Proceso proceso = procesoService.crear(empresaId, usuarioId, nombre, descripcion, categoria);
        poolService.crear(empresaId, proceso.getId(), proceso.getEmpresa().getNombre(),
                TipoParticipante.EMPRESA, false);
        return proceso;
    }
}
