package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolOrquestadorService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * Prueba de integracion pedida por la review del PR #37 (hallazgo bloqueante en
 * PoolController): si falla el ultimo paso (eliminar el registro del pool), las lanes que ya se
 * borraron en el paso anterior deben quedar restauradas (rollback real de toda la transaccion).
 * Usa el contexto Spring real y la base H2 real; solo PoolService se reemplaza por un mock
 * (dejando LaneService real, para que el borrado de lanes sea una escritura real que despues se
 * debe revertir).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:eliminar-pool-it;DB_CLOSE_DELAY=-1")
class EliminarPoolTransaccionalTest {

    @Autowired
    private PoolOrquestadorService poolOrquestadorService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private RolProcesoService rolProcesoService;

    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private PoolRepository poolRepository;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private LaneService laneService;

    @MockitoBean
    private PoolService poolService;

    @Test
    @DisplayName("Si falla eliminar el pool, las lanes ya borradas quedan restauradas (rollback real)")
    void eliminar_revierte_las_lanes_borradas_si_falla_eliminar_el_pool() {
        Empresa empresa = empresaService.registrar("Acme Pools", "900888888", "info@acme-pools.com");

        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre("Proceso con pool");
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        Pool pool = new Pool();
        pool.setEmpresa(empresa);
        pool.setProceso(proceso);
        pool.setNombre("Pool a eliminar");
        pool.setTipoParticipante(TipoParticipante.CLIENTE);
        pool.setCajaNegra(false);
        pool.setOrden(0);
        pool = poolRepository.save(pool);

        Long empresaId = empresa.getId();
        Long poolId = pool.getId();
        RolProceso rol = rolProcesoService.crear(empresaId, "Analista", "desc");

        when(poolService.obtener(empresaId, poolId)).thenReturn(pool);
        var lane = laneService.crear(empresaId, poolId, "Lane 1", rol);

        when(poolService.verificarEliminable(empresaId, poolId)).thenReturn(pool);
        doThrow(new RuntimeException("fallo forzado")).when(poolService).eliminarRegistro(pool);

        assertThrows(RuntimeException.class,
                () -> poolOrquestadorService.eliminar(empresaId, poolId));

        var lanesRestantes = laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId);
        assertFalse(lanesRestantes.isEmpty(),
                "Las lanes no debieron quedar borradas si el pool no se pudo eliminar");
        assertTrue(lanesRestantes.stream().anyMatch(l -> l.getId().equals(lane.getId())));
    }
}
