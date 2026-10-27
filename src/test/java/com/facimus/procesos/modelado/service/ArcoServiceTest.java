package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;

@ExtendWith(MockitoExtension.class)
class ArcoServiceTest {

    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private PoolService poolService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;

    @InjectMocks
    private ArcoService arcoService;

    private Empresa empresa;
    private Pool pool;
    private Lane lane;
    private Actividad nodoA;
    private Actividad nodoB;
    private Gateway gatewayExclusivo;
    private Arco arco;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        Proceso proceso = new Proceso();
        proceso.setId(1L);
        proceso.setActivo(true);
        proceso.setEmpresa(empresa);

        pool = new Pool();
        pool.setId(100L);
        pool.setEmpresa(empresa);
        pool.setProceso(proceso);

        lane = new Lane();
        lane.setId(1000L);
        lane.setPool(pool);

        nodoA = new Actividad();
        nodoA.setId(10L);
        nodoA.setLane(lane);
        nodoA.setEmpresa(empresa);

        nodoB = new Actividad();
        nodoB.setId(20L);
        nodoB.setLane(lane);
        nodoB.setEmpresa(empresa);

        gatewayExclusivo = new Gateway();
        gatewayExclusivo.setId(30L);
        gatewayExclusivo.setTipoGateway(TipoGateway.EXCLUSIVO);
        gatewayExclusivo.setLane(lane);
        gatewayExclusivo.setEmpresa(empresa);

        arco = new Arco();
        arco.setId(1L);
        arco.setOrigen(nodoA);
        arco.setDestino(nodoB);
        arco.setPool(pool);
        arco.setEmpresa(empresa);
        arco.setEtiqueta("si");
        arco.setCondicion(null);
    }


    @Test
    void crear_con_mismo_origen_destino_falla() {
        assertThrows(ReglaNegocioException.class,
                () -> arcoService.crear(1L, 10L, 10L, "loop", null));
    }

    @Test
    void crear_con_nodo_inactivo_falla() {
        nodoA.setActivo(false);
        when(nodoFlujoService.obtener(1L, 10L)).thenReturn(nodoA);
        when(nodoFlujoService.obtener(1L, 20L)).thenReturn(nodoB);

        assertThrows(RecursoNoEncontradoException.class,
                () -> arcoService.crear(1L, 10L, 20L, "x", null));
    }

    @Test
    void crear_hacia_catch_inicio_falla() {
        EventoMensaje catchInicio = new EventoMensaje();
        catchInicio.setId(50L);
        catchInicio.setActivo(true);
        catchInicio.setTipoEvento(TipoEventoMensaje.CATCH_INICIO);
        catchInicio.setLane(lane);

        when(nodoFlujoService.obtener(1L, 10L)).thenReturn(nodoA);
        when(nodoFlujoService.obtener(1L, 50L)).thenReturn(catchInicio);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(10L, 50L, 1L)).thenReturn(false);

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.crear(1L, 10L, 50L, "x", null));
    }

    @Test
    void impacto_sin_alternativas_advierte_ambos_extremos() {
        arco.setActivo(true);
        nodoA.setNombre("A");
        nodoB.setNombre("B");
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(10L, 1L)).thenReturn(List.of(arco));
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(20L, 1L)).thenReturn(List.of(arco));

        var impacto = arcoService.evaluarImpactoEliminacion(1L, 1L);

        assertTrue(impacto.rompeContinuidad());
        assertEquals(2, impacto.advertencias().size());
    }

    @Test
    void impacto_con_alternativas_no_advierte() {
        arco.setActivo(true);
        Arco salidaAlterna = new Arco();
        salidaAlterna.setId(2L);
        salidaAlterna.setActivo(true);
        Arco entradaAlterna = new Arco();
        entradaAlterna.setId(3L);
        entradaAlterna.setActivo(true);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(10L, 1L)).thenReturn(List.of(arco, salidaAlterna));
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(20L, 1L)).thenReturn(List.of(arco, entradaAlterna));

        var impacto = arcoService.evaluarImpactoEliminacion(1L, 1L);

        assertFalse(impacto.rompeContinuidad());
        assertTrue(impacto.advertencias().isEmpty());
    }

    @Test
    void eliminar_hace_baja_logica() {
        arco.setActivo(true);
        nodoA.setNombre("A");
        nodoB.setNombre("B");
        com.facimus.procesos.gestion.model.Proceso proceso = new com.facimus.procesos.gestion.model.Proceso();
        pool.setProceso(proceso);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));

        arcoService.eliminar(1L, 1L);

        assertFalse(arco.isActivo());
        verify(arcoRepository).save(arco);
        verify(auditoriaModeladoService).registrar(eq(proceso), contains("eliminado"));
    }

    @Test
    void listar_por_pool_filtra_inactivos() {
        Arco activo = new Arco(); activo.setActivo(true);
        Arco inactivo = new Arco(); inactivo.setActivo(false);
        when(poolService.obtener(1L, 100L)).thenReturn(pool);
        when(arcoRepository.findAllByPoolIdAndEmpresaId(100L, 1L)).thenReturn(List.of(activo, inactivo));

        var resultado = arcoService.listarPorPool(1L, 100L);

        assertEquals(1, resultado.size());
        assertSame(activo, resultado.get(0));
    }

    @Test
    void listar_pool_inexistente_falla() {
        when(poolService.obtener(1L, 999L)).thenThrow(new RecursoNoEncontradoException("Pool no encontrado."));

        assertThrows(RecursoNoEncontradoException.class,
                () -> arcoService.listarPorPool(1L, 999L));
    }

    @Test
    @DisplayName("Crear arco saliente de gateway EXCLUSIVO sin condicion lanza excepcion")
    void crear_arco_desde_gateway_exclusivo_sin_condicion() {
        when(nodoFlujoService.obtener(1L, 30L)).thenReturn(gatewayExclusivo);
        when(nodoFlujoService.obtener(1L, 20L)).thenReturn(nodoB);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(30L, 20L, 1L)).thenReturn(false);

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.crear(1L, 30L, 20L, "si", null));
    }

    @Test
    @DisplayName("Crear arco saliente de gateway EXCLUSIVO con condicion funciona")
    void crear_arco_desde_gateway_exclusivo_con_condicion() {
        when(nodoFlujoService.obtener(1L, 30L)).thenReturn(gatewayExclusivo);
        when(nodoFlujoService.obtener(1L, 20L)).thenReturn(nodoB);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(30L, 20L, 1L)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.crear(1L, 30L, 20L, "si", "monto > 100");

        assertEquals("monto > 100", resultado.getCondicion());
    }

    @Test
    @DisplayName("Crear arco hacia gateway EXCLUSIVO sin condicion NO lanza excepcion")
    void crear_arco_hacia_gateway_exclusivo_sin_condicion() {
        when(nodoFlujoService.obtener(1L, 10L)).thenReturn(nodoA);
        when(nodoFlujoService.obtener(1L, 30L)).thenReturn(gatewayExclusivo);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(10L, 30L, 1L)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.crear(1L, 10L, 30L, "entrada", null);

        assertEquals(nodoA, resultado.getOrigen());
        assertEquals(gatewayExclusivo, resultado.getDestino());
        assertEquals("entrada", resultado.getEtiqueta());
        assertNull(resultado.getCondicion());
    }

    @Test
    @DisplayName("Editar arco cambiando origen funciona")
    void editar_cambiar_origen() {
        Actividad nodoC = new Actividad();
        nodoC.setId(40L);
        nodoC.setLane(lane);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(nodoFlujoService.obtener(1L, 40L)).thenReturn(nodoC);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(40L, 20L, 1L)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.editar(1L, 1L, "si", null, 40L, null);

        assertEquals(nodoC, resultado.getOrigen());
        assertEquals(nodoB, resultado.getDestino());
    }

    @Test
    @DisplayName("Editar arco poniendo origen = destino lanza excepcion")
    void editar_origen_igual_destino() {
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.editar(1L, 1L, "si", null, 20L, null));
    }

    @Test
    @DisplayName("Editar arco sin cambiar nodos actualiza solo etiqueta y condicion")
    void editar_sin_cambiar_nodos() {
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.editar(1L, 1L, "nueva etiqueta", null, null, null);

        assertEquals("nueva etiqueta", resultado.getEtiqueta());
        assertNull(resultado.getCondicion());
        assertEquals(nodoA, resultado.getOrigen());
        assertEquals(nodoB, resultado.getDestino());
    }

    @Test
    @DisplayName("Editar arco cambiando solo destino funciona")
    void editar_cambiar_solo_destino() {
        Actividad nodoC = new Actividad();
        nodoC.setId(40L);
        nodoC.setLane(lane);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(nodoFlujoService.obtener(1L, 40L)).thenReturn(nodoC);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(10L, 40L, 1L)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.editar(1L, 1L, "si", null, null, 40L);

        assertEquals(nodoA, resultado.getOrigen());
        assertEquals(nodoC, resultado.getDestino());
    }

    @Test
    @DisplayName("Editar arco cambiando ambos nodos funciona")
    void editar_cambiar_ambos_nodos() {
        Actividad nodoC = new Actividad();
        nodoC.setId(40L);
        nodoC.setLane(lane);
        Actividad nodoD = new Actividad();
        nodoD.setId(50L);
        nodoD.setLane(lane);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(nodoFlujoService.obtener(1L, 40L)).thenReturn(nodoC);
        when(nodoFlujoService.obtener(1L, 50L)).thenReturn(nodoD);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(40L, 50L, 1L)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.editar(1L, 1L, "si", null, 40L, 50L);

        assertEquals(nodoC, resultado.getOrigen());
        assertEquals(nodoD, resultado.getDestino());
    }

    @Test
    @DisplayName("Editar arco a pools diferentes lanza excepcion")
    void editar_pool_diferente() {
        Pool pool2 = new Pool();
        pool2.setId(200L);
        Lane lane2 = new Lane();
        lane2.setId(2000L);
        lane2.setPool(pool2);

        Actividad nodoC = new Actividad();
        nodoC.setId(40L);
        nodoC.setLane(lane2);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(nodoFlujoService.obtener(1L, 40L)).thenReturn(nodoC);

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.editar(1L, 1L, "si", null, null, 40L));
    }

    @Test
    @DisplayName("Editar arco duplicado lanza excepcion")
    void editar_arco_duplicado() {
        Actividad nodoC = new Actividad();
        nodoC.setId(40L);
        nodoC.setLane(lane);

        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(nodoFlujoService.obtener(1L, 40L)).thenReturn(nodoC);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(10L, 40L, 1L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.editar(1L, 1L, "si", null, null, 40L));
    }

    @Test
    @DisplayName("Editar arco con origen gateway exclusivo sin condicion lanza excepcion")
    void editar_gateway_origen_sin_condicion() {
        arco.setOrigen(gatewayExclusivo);
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.editar(1L, 1L, "si", null, null, null));
    }

    @Test
    @DisplayName("Editar arco con origen gateway inclusivo con condicion funciona")
    void editar_gateway_inclusivo_origen_con_condicion() {
        Gateway gatewayInclusivo = new Gateway();
        gatewayInclusivo.setId(35L);
        gatewayInclusivo.setTipoGateway(TipoGateway.INCLUSIVO);
        gatewayInclusivo.setLane(lane);
        gatewayInclusivo.setEmpresa(empresa);

        arco.setOrigen(gatewayInclusivo);
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.editar(1L, 1L, "si", "monto > 50", null, null);

        assertEquals("monto > 50", resultado.getCondicion());
    }

    @Test
    @DisplayName("Editar arco con origenId igual al actual no verifica duplicados")
    void editar_mismo_origen_no_verifica_duplicado() {
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));
        when(nodoFlujoService.obtener(1L, 10L)).thenReturn(nodoA);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        Arco resultado = arcoService.editar(1L, 1L, "nueva", null, 10L, null);

        assertEquals("nueva", resultado.getEtiqueta());
        verify(arcoRepository, never()).existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Editar arco poniendo destino = origen via destinoId lanza excepcion")
    void editar_destino_igual_origen() {
        when(arcoRepository.findByIdAndEmpresaId(1L, 1L)).thenReturn(Optional.of(arco));

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.editar(1L, 1L, "si", null, null, 10L));
    }
}
