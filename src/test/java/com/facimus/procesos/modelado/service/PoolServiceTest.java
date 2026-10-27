package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.event.PoolMarcadoCajaNegraEvent;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;

@ExtendWith(MockitoExtension.class)
class PoolServiceTest {

    @Mock
    private PoolRepository poolRepository;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PoolService poolService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool pool;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        proceso = new Proceso();
        proceso.setId(10L);
        proceso.setEmpresa(empresa);

        pool = new Pool();
        pool.setId(100L);
        pool.setNombre("Pool Principal");
        pool.setProceso(proceso);
        pool.setEmpresa(empresa);
        pool.setTipoParticipante(TipoParticipante.EMPRESA);
    }

    @Test
    @DisplayName("Crear pool asigna orden correcto")
    void crear_exitoso() {
        when(procesoService.obtener(1L, 10L)).thenReturn(proceso);
        when(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(10L, 1L))
                .thenReturn(List.of(pool));
        when(poolRepository.save(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        Pool resultado = poolService.crear(1L, 10L, "Nuevo Pool", TipoParticipante.CLIENTE, false);

        assertEquals("Nuevo Pool", resultado.getNombre());
        assertEquals(1, resultado.getOrden());
    }

    @Test
    @DisplayName("Crear pool con proceso inexistente lanza excepcion")
    void crear_proceso_inexistente() {
        when(procesoService.obtener(1L, 10L)).thenThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        assertThrows(RecursoNoEncontradoException.class,
                () -> poolService.crear(1L, 10L, "Pool", TipoParticipante.EMPRESA, false));
    }

    @Test
    @DisplayName("Editar pool actualiza nombre y tipo")
    void editar_exitoso() {
        when(poolRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(pool));
        when(poolRepository.save(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        Pool resultado = poolService.editar(1L, 100L, "Pool Editado", TipoParticipante.PROVEEDOR);

        assertEquals("Pool Editado", resultado.getNombre());
        assertEquals(TipoParticipante.PROVEEDOR, resultado.getTipoParticipante());
    }

    @Test
    @DisplayName("Editar con cajaNegra=true publica PoolMarcadoCajaNegraEvent antes de guardar, para que "
            + "LaneService pueda rechazar la operacion sin que PoolService dependa de el")
    void editar_cajaNegra_publica_evento_antes_de_guardar() {
        when(poolRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(pool));
        when(poolRepository.save(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        poolService.editar(1L, 100L, "Pool Editado", TipoParticipante.PROVEEDOR, true);

        InOrder orden = inOrder(eventPublisher, poolRepository);
        orden.verify(eventPublisher).publishEvent(new PoolMarcadoCajaNegraEvent(1L, 100L));
        orden.verify(poolRepository).save(any(Pool.class));
    }

    @Test
    @DisplayName("Editar con cajaNegra=false no publica PoolMarcadoCajaNegraEvent")
    void editar_sin_cajaNegra_no_publica_evento() {
        when(poolRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(pool));
        when(poolRepository.save(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        poolService.editar(1L, 100L, "Pool Editado", TipoParticipante.PROVEEDOR, false);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Si el listener del evento rechaza marcar caja negra, no se guarda el pool")
    void editar_cajaNegra_no_guarda_si_el_evento_lanza_excepcion() {
        when(poolRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(pool));
        doThrow(new ReglaNegocioException("el pool tiene lanes"))
                .when(eventPublisher).publishEvent(any(PoolMarcadoCajaNegraEvent.class));

        assertThrows(ReglaNegocioException.class,
                () -> poolService.editar(1L, 100L, "Pool Editado", TipoParticipante.PROVEEDOR, true));

        verify(poolRepository, never()).save(any());
    }

    @Test
    @DisplayName("verificarEliminable en pool vacio retorna el pool")
    void verificarEliminable_pool_vacio() {
        when(poolRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(pool));
        when(nodoFlujoService.tieneNodosActivosEnPool(1L, 100L)).thenReturn(false);

        Pool resultado = poolService.verificarEliminable(1L, 100L);

        assertSame(pool, resultado);
    }

    @Test
    @DisplayName("eliminarRegistro borra el pool y audita")
    void eliminarRegistro_borra_pool() {
        poolService.eliminarRegistro(pool);

        verify(poolRepository).delete(pool);
    }

    @Test
    @DisplayName("Eliminar pool con actividades lanza excepcion")
    void eliminar_pool_con_actividades() {
        when(poolRepository.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(pool));
        when(nodoFlujoService.tieneNodosActivosEnPool(1L, 100L)).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> poolService.verificarEliminable(1L, 100L));

        assertTrue(ex.getMessage().contains("actividades") || ex.getMessage().contains("elementos activos"));
        verify(poolRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Listar pools por proceso inexistente lanza excepcion")
    void listar_proceso_inexistente() {
        when(procesoService.existe(1L, 10L)).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class,
                () -> poolService.listarPorProceso(1L, 10L));
    }

    @Test
    @DisplayName("Listar pools por proceso existente retorna lista")
    void listar_exitoso() {
        when(procesoService.existe(1L, 10L)).thenReturn(true);
        when(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(10L, 1L))
                .thenReturn(List.of(pool));

        List<Pool> resultado = poolService.listarPorProceso(1L, 10L);

        assertEquals(1, resultado.size());
    }

    @Test
    @DisplayName("Obtener pool inexistente lanza excepcion")
    void obtener_inexistente() {
        when(poolRepository.findByIdAndEmpresaId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> poolService.obtener(1L, 999L));
    }
}
