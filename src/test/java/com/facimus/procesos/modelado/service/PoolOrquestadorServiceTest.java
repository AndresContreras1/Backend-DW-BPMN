package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.modelado.model.Pool;

/**
 * El caso de uso de eliminar un pool (verificar eliminable + borrar lanes + eliminar registro)
 * debe quedar atomico: si la ultima escritura falla, la review de PR #37 exige que las lanes no
 * queden borradas sin haberse eliminado el pool. A nivel unitario se confirma el orden de
 * llamadas y que una excepcion detiene la secuencia sin continuar con los pasos siguientes.
 */
@ExtendWith(MockitoExtension.class)
class PoolOrquestadorServiceTest {

    @Mock
    private PoolService poolService;
    @Mock
    private LaneService laneService;
    @Mock
    private MensajeService mensajeService;

    @InjectMocks
    private PoolOrquestadorService poolOrquestadorService;

    private Pool pool;

    @BeforeEach
    void setUp() {
        pool = new Pool();
        pool.setId(100L);
        pool.setNombre("Cliente");
    }

    @Test
    @DisplayName("Verifica eliminable, borra las lanes y elimina el registro, en ese orden")
    void eliminar_llama_en_orden() {
        when(poolService.verificarEliminable(1L, 100L)).thenReturn(pool);
        doNothing().when(laneService).eliminarPorPool(1L, 100L);
        doNothing().when(poolService).eliminarRegistro(pool);

        poolOrquestadorService.eliminar(1L, 100L);

        InOrder orden = inOrder(poolService, laneService);
        orden.verify(poolService).verificarEliminable(1L, 100L);
        orden.verify(laneService).eliminarPorPool(1L, 100L);
        orden.verify(poolService).eliminarRegistro(pool);
    }

    @Test
    @DisplayName("Si el pool no es eliminable, no se borran las lanes ni el pool")
    void eliminar_no_continua_si_no_es_eliminable() {
        when(poolService.verificarEliminable(1L, 100L))
                .thenThrow(new ReglaNegocioException("tiene elementos activos"));

        assertThrows(ReglaNegocioException.class, () -> poolOrquestadorService.eliminar(1L, 100L));

        verify(laneService, never()).eliminarPorPool(1L, 100L);
        verify(poolService, never()).eliminarRegistro(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Si el pool tiene mensajes activos, no se borran las lanes ni el pool")
    void eliminar_no_continua_si_tiene_mensajes_activos() {
        when(poolService.verificarEliminable(1L, 100L)).thenReturn(pool);
        when(mensajeService.tieneMensajesActivosEnPool(1L, 100L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> poolOrquestadorService.eliminar(1L, 100L));

        verify(laneService, never()).eliminarPorPool(1L, 100L);
        verify(poolService, never()).eliminarRegistro(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Si falla el borrado del registro del pool, la excepcion se propaga")
    void eliminar_propaga_excepcion_del_ultimo_paso() {
        when(poolService.verificarEliminable(1L, 100L)).thenReturn(pool);
        doNothing().when(laneService).eliminarPorPool(1L, 100L);
        doThrow(new RuntimeException("fallo de base de datos")).when(poolService).eliminarRegistro(pool);

        assertThrows(RuntimeException.class, () -> poolOrquestadorService.eliminar(1L, 100L));
    }
}
