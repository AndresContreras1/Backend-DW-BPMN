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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;

@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {

    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private LaneService laneService;
    @Mock
    private ArcoService arcoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;

    @InjectMocks
    private GatewayServiceImpl gatewayService;

    private Gateway gateway;

    @BeforeEach
    void setUp() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);

        Proceso proceso = new Proceso();
        proceso.setId(10L);
        proceso.setEmpresa(empresa);

        Pool pool = new Pool();
        pool.setId(100L);
        pool.setProceso(proceso);

        Lane lane = new Lane();
        lane.setId(1000L);
        lane.setPool(pool);

        gateway = new Gateway();
        gateway.setId(1L);
        gateway.setNombre("Decision");
        gateway.setTipoGateway(TipoGateway.EXCLUSIVO);
        gateway.setLane(lane);
        gateway.setEmpresa(empresa);
        gateway.setPosicionX(0);
        gateway.setPosicionY(0);
    }


    @Test
    void crear_gateway_exitosamente() {
        Lane lane = gateway.getLane();
        when(laneService.obtener(1L, 1000L)).thenReturn(lane);
        when(nodoFlujoService.existeNombreEnProceso(
                "Nuevo", 10L, 1L)).thenReturn(false);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Gateway resultado = gatewayService.crear(1L, 1000L, "Nuevo", TipoGateway.EXCLUSIVO, 5, 6);

        assertEquals("Nuevo", resultado.getNombre());
        verify(auditoriaModeladoService).registrar(any(), contains("Gateway creado"));
    }

    @Test
    void crear_en_pool_caja_negra_falla() {
        Lane lane = gateway.getLane();
        lane.getPool().setCajaNegra(true);
        when(laneService.obtener(1L, 1000L)).thenReturn(lane);

        assertThrows(ReglaNegocioException.class,
                () -> gatewayService.crear(1L, 1000L, "Nuevo", TipoGateway.EXCLUSIVO, 0, 0));
    }

    @Test
    void impacto_eliminacion_detecta_ramificacion() {
        Arco entrada = new Arco(); entrada.setActivo(true);
        Arco salida1 = new Arco(); salida1.setActivo(true);
        Arco salida2 = new Arco(); salida2.setActivo(true);

        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(entrada));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(salida1, salida2));

        var impacto = gatewayService.evaluarImpactoEliminacion(1L, 1L);

        assertTrue(impacto.rompeContinuidad());
        assertEquals(2, impacto.advertencias().size());
    }

    @Test
    void eliminar_desactiva_gateway_y_arcos() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));

        gatewayService.eliminar(1L, 1L);

        assertFalse(gateway.isActivo());
        verify(arcoService).desactivarPorNodo(1L, 1L);
        verify(auditoriaModeladoService).registrar(any(), contains("eliminado"));
    }

    @Test
    void listar_por_lane_filtra_gateways_activos() {
        Gateway activo = new Gateway(); activo.setActivo(true);
        Gateway inactivo = new Gateway(); inactivo.setActivo(false);
        com.facimus.procesos.modelado.model.Actividad actividad =
                new com.facimus.procesos.modelado.model.Actividad();
        actividad.setActivo(true);

        Lane lane = new Lane();
        when(laneService.obtener(1L, 1000L)).thenReturn(lane);
        when(nodoFlujoService.listarPorLane(1L, 1000L))
                .thenReturn(List.of(activo, inactivo, actividad));

        var resultado = gatewayService.listarPorLane(1L, 1000L);

        assertEquals(1, resultado.size());
        assertSame(activo, resultado.get(0));
    }

    @Test
    @DisplayName("Editar con nombre duplicado lanza excepcion")
    void editar_nombre_duplicado() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(nodoFlujoService.existeNombreEnProceso(
                "Otro", 10L, 1L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> gatewayService.editar(1L, 1L, "Otro", TipoGateway.EXCLUSIVO, 0, 0));
    }

    @Test
    @DisplayName("Cambiar de EXCLUSIVO a PARALELO limpia condiciones de arcos salientes")
    void cambiar_a_paralelo_limpia_condiciones() {
        Arco arco = new Arco();
        arco.setCondicion("x > 5");
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arco));
        when(arcoService.guardar(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        gatewayService.editar(1L, 1L, "Decision", TipoGateway.PARALELO, 0, 0);

        assertNull(arco.getCondicion());
        verify(arcoService).guardar(arco);
    }

    @Test
    @DisplayName("Cambiar de PARALELO a EXCLUSIVO con arcos sin condicion lanza excepcion")
    void cambiar_paralelo_a_exclusivo_sin_condicion() {
        gateway.setTipoGateway(TipoGateway.PARALELO);
        Arco arco = new Arco();
        arco.setCondicion(null);
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arco));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0));

        assertTrue(ex.getMessage().contains("arcos salientes sin condicion"));
    }

    @Test
    @DisplayName("Cambiar de PARALELO a EXCLUSIVO con arcos con condicion funciona")
    void cambiar_paralelo_a_exclusivo_con_condicion() {
        gateway.setTipoGateway(TipoGateway.PARALELO);
        Arco arco = new Arco();
        arco.setCondicion("x > 5");
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arco));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Gateway resultado = gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0);

        assertEquals(TipoGateway.EXCLUSIVO, resultado.getTipoGateway());
    }

    @Test
    @DisplayName("Cambiar de PARALELO a INCLUSIVO sin arcos funciona")
    void cambiar_paralelo_a_inclusivo_sin_arcos() {
        gateway.setTipoGateway(TipoGateway.PARALELO);
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(Collections.emptyList());
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Gateway resultado = gatewayService.editar(1L, 1L, "Decision", TipoGateway.INCLUSIVO, 0, 0);

        assertEquals(TipoGateway.INCLUSIVO, resultado.getTipoGateway());
    }

    @Test
    @DisplayName("Cambiar de PARALELO a INCLUSIVO con arcos sin condicion lanza excepcion")
    void cambiar_paralelo_a_inclusivo_sin_condicion() {
        gateway.setTipoGateway(TipoGateway.PARALELO);
        Arco arco = new Arco();
        arco.setCondicion(null);
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arco));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> gatewayService.editar(1L, 1L, "Decision", TipoGateway.INCLUSIVO, 0, 0));

        assertTrue(ex.getMessage().contains("arcos salientes sin condicion"));
    }

    @Test
    @DisplayName("Editar gateway sin cambiar tipo ni nombre funciona")
    void editar_sin_cambiar_tipo() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Gateway resultado = gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 5, 10);

        assertEquals(TipoGateway.EXCLUSIVO, resultado.getTipoGateway());
        assertEquals(5, resultado.getPosicionX());
    }
    @Test
    @DisplayName("Arcos inactivos no bloquean cambio de PARALELO a EXCLUSIVO")
    void arcos_inactivos_no_bloquean_cambio_tipo() {
        gateway.setTipoGateway(TipoGateway.PARALELO);
        Arco arcoInactivo = new Arco();
        arcoInactivo.setActivo(false);
        arcoInactivo.setCondicion(null);

        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arcoInactivo));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Gateway resultado = gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0);

        assertEquals(TipoGateway.EXCLUSIVO, resultado.getTipoGateway());
    }

    // ---- impacto ----

    private Arco arcoActivo(Long id, boolean conCondicion) {
        Arco arco = new Arco();
        arco.setId(id);
        arco.setActivo(true);
        arco.setCondicion(conCondicion ? "x > " + id : null);
        return arco;
    }

    @Test
    void impacto_de_romper_una_convergencia_avisa_de_las_entradas_y_la_salida() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(arcoActivo(1L, false), arcoActivo(2L, false)));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arcoActivo(3L, false)));

        var impacto = gatewayService.evaluarImpactoEliminacion(1L, 1L);

        assertTrue(impacto.rompeContinuidad());
        assertTrue(impacto.advertencias().stream().anyMatch(a -> a.contains("convergencia de 2 entradas")));
    }

    @Test
    void impacto_de_un_gateway_divergente_no_avisa_de_convergencia() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(arcoActivo(1L, false)));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arcoActivo(3L, true), arcoActivo(4L, true)));

        var impacto = gatewayService.evaluarImpactoEliminacion(1L, 1L);

        assertTrue(impacto.advertencias().stream().noneMatch(a -> a.contains("convergencia")));
        assertTrue(impacto.advertencias().stream().anyMatch(a -> a.contains("ramificacion con 2 salidas")));
    }

    @Test
    void impacto_de_una_convergencia_sin_salida_no_avisa_de_convergencia_rota() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(arcoActivo(1L, false), arcoActivo(2L, false)));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of());

        var impacto = gatewayService.evaluarImpactoEliminacion(1L, 1L);

        assertTrue(impacto.advertencias().stream().noneMatch(a -> a.contains("convergencia")));
    }

    // ---- historial ----

    @Test
    void editar_describe_el_nombre_el_tipo_y_las_condiciones_eliminadas() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(arcoActivo(1L, true), arcoActivo(2L, true),
                arcoActivo(3L, false)));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        gatewayService.editar(1L, 1L, "Reparte", TipoGateway.PARALELO, 0, 0);

        verify(auditoriaModeladoService).registrar(any(), eq("Gateway editado: Reparte (nombre: Decision -> Reparte; "
                + "tipo: EXCLUSIVO -> PARALELO; condiciones eliminadas en 2 arco(s) saliente(s))."));
    }

    @Test
    void editar_sin_cambios_deja_un_historial_simple() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 9, 9);

        verify(auditoriaModeladoService).registrar(any(), eq("Gateway editado: Decision."));
    }

    // ---- laneId opcional ----

    private Lane laneDe(Long laneId, Long poolId, Long procesoId, boolean cajaNegra) {
        Proceso proceso = new Proceso();
        proceso.setId(procesoId);
        Pool pool = new Pool();
        pool.setId(poolId);
        pool.setProceso(proceso);
        pool.setCajaNegra(cajaNegra);
        Lane l = new Lane();
        l.setId(laneId);
        l.setNombre("Lane " + laneId);
        l.setPool(pool);
        return l;
    }

    @Test
    void editar_con_laneId_del_mismo_pool_mueve_el_gateway_y_lo_registra() {
        Lane destino = laneDe(1001L, 100L, 10L, false);
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(laneService.obtener(1L, 1001L)).thenReturn(destino);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Gateway resultado = gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0, 1001L);

        assertSame(destino, resultado.getLane());
        verify(auditoriaModeladoService).registrar(any(), contains("lane: null -> Lane 1001"));
    }

    @Test
    void editar_con_laneId_igual_a_la_actual_o_nulo_no_consulta_lanes() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0, 1000L);
        gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0, null);

        verify(laneService, never()).obtener(anyLong(), anyLong());
    }

    @Test
    void editar_con_laneId_de_otro_pool_y_con_arcos_activos_falla() {
        Lane destino = laneDe(2000L, 200L, 10L, false);
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(laneService.obtener(1L, 2000L)).thenReturn(destino);
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(arcoActivo(1L, false)));

        var ex = assertThrows(ReglaNegocioException.class,
                () -> gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0, 2000L));

        assertTrue(ex.getMessage().contains("otro pool"));
        verify(nodoFlujoService, never()).guardar(any());
    }

    @Test
    void editar_con_laneId_de_pool_caja_negra_o_de_otro_proceso_falla() {
        Lane cajaNegra = laneDe(3000L, 300L, 10L, true);
        Lane otroProceso = laneDe(4000L, 400L, 99L, false);
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(gateway));
        when(laneService.obtener(1L, 3000L)).thenReturn(cajaNegra);
        when(laneService.obtener(1L, 4000L)).thenReturn(otroProceso);

        assertThrows(ReglaNegocioException.class,
                () -> gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0, 3000L));
        assertThrows(ReglaNegocioException.class,
                () -> gatewayService.editar(1L, 1L, "Decision", TipoGateway.EXCLUSIVO, 0, 0, 4000L));
    }
}
