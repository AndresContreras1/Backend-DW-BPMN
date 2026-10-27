package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * El caso de uso HU-04 (crear proceso + pool inicial) debe quedar atomico: si
 * PoolService.crear falla, la review de PR #37 exige que el proceso no quede persistido sin su
 * pool inicial. A nivel unitario se confirma el orden de llamadas y que una excepcion en el
 * segundo paso se propaga sin swallow.
 */
@ExtendWith(MockitoExtension.class)
class ProcesoOrquestadorServiceTest {

    @Mock
    private ProcesoService procesoService;
    @Mock
    private PoolService poolService;

    @InjectMocks
    private ProcesoOrquestadorService procesoOrquestadorService;

    private Empresa empresa;
    private Proceso proceso;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme");

        proceso = new Proceso();
        proceso.setId(100L);
        proceso.setEmpresa(empresa);
        proceso.setNombre("Compras");
    }

    @Test
    @DisplayName("Crea el proceso y despues el pool inicial, en ese orden")
    void crearConPoolInicial_llama_en_orden() {
        when(procesoService.crear(1L, 10L, "Compras", "desc", "cat")).thenReturn(proceso);

        Proceso resultado = procesoOrquestadorService.crearConPoolInicial(1L, 10L, "Compras", "desc", "cat");

        assertSame(proceso, resultado);
        InOrder orden = inOrder(procesoService, poolService);
        orden.verify(procesoService).crear(1L, 10L, "Compras", "desc", "cat");
        orden.verify(poolService).crear(eq(1L), eq(100L), eq("Acme"), eq(TipoParticipante.EMPRESA), eq(false));
    }

    @Test
    @DisplayName("Si falla la creacion del pool inicial, la excepcion se propaga")
    void crearConPoolInicial_propaga_excepcion_del_segundo_paso() {
        when(procesoService.crear(1L, 10L, "Compras", "desc", "cat")).thenReturn(proceso);
        when(poolService.crear(eq(1L), eq(100L), anyString(), eq(TipoParticipante.EMPRESA), eq(false)))
                .thenThrow(new ReglaNegocioException("pool invalido"));

        assertThrows(ReglaNegocioException.class,
                () -> procesoOrquestadorService.crearConPoolInicial(1L, 10L, "Compras", "desc", "cat"));
    }

    @Test
    @DisplayName("Si falla la creacion del proceso, no se intenta crear el pool")
    void crearConPoolInicial_no_crea_pool_si_falla_el_proceso() {
        when(procesoService.crear(1L, 10L, "Compras", "desc", "cat"))
                .thenThrow(new ReglaNegocioException("nombre duplicado"));

        assertThrows(ReglaNegocioException.class,
                () -> procesoOrquestadorService.crearConPoolInicial(1L, 10L, "Compras", "desc", "cat"));

        verify(poolService, never()).crear(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }
}
