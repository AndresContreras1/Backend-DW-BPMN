package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.PoolMarcadoCajaNegraEvent;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.LaneRepository;

@ExtendWith(MockitoExtension.class)
class LaneServiceTest {

    @Mock
    private LaneRepository laneRepository;
    @Mock
    private PoolService poolService;
    @Mock
    private RolProcesoService rolProcesoService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;

    @InjectMocks
    private LaneService laneService;

    private Pool pool;
    private Lane lane1;
    private Lane lane2;
    private Lane lane3;

    @BeforeEach
    void setUp() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);

        Proceso proceso = new Proceso();
        proceso.setId(1L);
        proceso.setActivo(true);
        proceso.setEmpresa(empresa);

        pool = new Pool();
        pool.setId(100L);
        pool.setEmpresa(empresa);
        pool.setProceso(proceso);

        lane1 = new Lane();
        lane1.setId(1L);
        lane1.setPool(pool);
        lane1.setOrden(0);

        lane2 = new Lane();
        lane2.setId(2L);
        lane2.setPool(pool);
        lane2.setOrden(1);

        lane3 = new Lane();
        lane3.setId(3L);
        lane3.setPool(pool);
        lane3.setOrden(2);
    }

    @Test
    @DisplayName("Reordenar lanes con IDs duplicados lanza excepcion")
    void reordenar_con_duplicados() {
        when(poolService.obtener(1L, 100L)).thenReturn(pool);
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(100L, 1L))
                .thenReturn(List.of(lane1, lane2, lane3));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> laneService.reordenar(1L, 100L, List.of(1L, 1L, 3L)));

        assertTrue(ex.getMessage().contains("duplicados"));
    }

    @Test
    @DisplayName("Reordenar lanes con cantidad incorrecta lanza excepcion")
    void reordenar_cantidad_incorrecta() {
        when(poolService.obtener(1L, 100L)).thenReturn(pool);
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(100L, 1L))
                .thenReturn(List.of(lane1, lane2, lane3));

        assertThrows(ReglaNegocioException.class,
                () -> laneService.reordenar(1L, 100L, List.of(1L, 2L)));
    }

    @Test
    @DisplayName("Reordenar lanes con ID ajeno lanza excepcion")
    void reordenar_id_ajeno() {
        when(poolService.obtener(1L, 100L)).thenReturn(pool);
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(100L, 1L))
                .thenReturn(List.of(lane1, lane2, lane3));

        assertThrows(ReglaNegocioException.class,
                () -> laneService.reordenar(1L, 100L, List.of(1L, 2L, 999L)));
    }

    @Test
    @DisplayName("Reordenar lanes con datos validos actualiza el orden")
    void reordenar_exitoso() {
        when(poolService.obtener(1L, 100L)).thenReturn(pool);
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(100L, 1L))
                .thenReturn(List.of(lane1, lane2, lane3));
        when(laneRepository.save(any(Lane.class))).thenAnswer(inv -> inv.getArgument(0));

        laneService.reordenar(1L, 100L, List.of(3L, 1L, 2L));

        assertEquals(1, lane1.getOrden());
        assertEquals(2, lane2.getOrden());
        assertEquals(0, lane3.getOrden());
    }

    @Test
    @DisplayName("Reordenar lanes con pool inexistente lanza excepcion")
    void reordenar_pool_inexistente() {
        when(poolService.obtener(1L, 999L)).thenThrow(new RecursoNoEncontradoException("Pool no encontrado."));

        assertThrows(RecursoNoEncontradoException.class,
                () -> laneService.reordenar(1L, 999L, List.of(1L, 2L)));
    }

    @Test
    @DisplayName("alMarcarPoolCajaNegra rechaza si el pool tiene lanes")
    void alMarcarPoolCajaNegra_con_lanes_lanza_excepcion() {
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(100L, 1L))
                .thenReturn(List.of(lane1));

        assertThrows(ReglaNegocioException.class,
                () -> laneService.alMarcarPoolCajaNegra(new PoolMarcadoCajaNegraEvent(1L, 100L)));
    }

    @Test
    @DisplayName("alMarcarPoolCajaNegra no hace nada si el pool no tiene lanes")
    void alMarcarPoolCajaNegra_sin_lanes_no_lanza() {
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(100L, 1L))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> laneService.alMarcarPoolCajaNegra(new PoolMarcadoCajaNegraEvent(1L, 100L)));
    }
}
