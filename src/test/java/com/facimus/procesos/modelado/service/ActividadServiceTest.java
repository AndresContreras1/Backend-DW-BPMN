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
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;

@ExtendWith(MockitoExtension.class)
class ActividadServiceTest {

    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private LaneService laneService;
    @Mock
    private ArcoService arcoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;

    @InjectMocks
    private ActividadService actividadService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool pool1;
    private Pool pool2;
    private Lane lane1;
    private Lane lane2;
    private Lane lane3;
    private Actividad actividad;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        proceso = new Proceso();
        proceso.setId(10L);
        proceso.setEmpresa(empresa);

        pool1 = new Pool();
        pool1.setId(100L);
        pool1.setProceso(proceso);
        pool1.setEmpresa(empresa);

        pool2 = new Pool();
        pool2.setId(200L);
        pool2.setProceso(proceso);
        pool2.setEmpresa(empresa);

        lane1 = new Lane();
        lane1.setId(1000L);
        lane1.setPool(pool1);
        lane1.setEmpresa(empresa);

        lane2 = new Lane();
        lane2.setId(2000L);
        lane2.setPool(pool1);
        lane2.setEmpresa(empresa);

        lane3 = new Lane();
        lane3.setId(3000L);
        lane3.setPool(pool2);
        lane3.setEmpresa(empresa);

        actividad = new Actividad();
        actividad.setId(1L);
        actividad.setNombre("Tarea A");
        actividad.setDescripcion("Desc");
        actividad.setLane(lane1);
        actividad.setEmpresa(empresa);
        actividad.setPosicionX(0);
        actividad.setPosicionY(0);
    }


    @Test
    void crear_actividad_exitosamente() {
        when(laneService.obtener(1L, 1000L)).thenReturn(lane1);
        when(nodoFlujoService.existeNombreEnProceso(
                "Nueva", 10L, 1L)).thenReturn(false);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Actividad resultado = actividadService.crear(1L, 1000L, "Nueva", "Desc", 10, 20);

        assertEquals("Nueva", resultado.getNombre());
        assertEquals(10, resultado.getPosicionX());
        verify(auditoriaModeladoService).registrar(eq(proceso), contains("Actividad creada"));
    }

    @Test
    void crear_en_pool_caja_negra_falla() {
        pool1.setCajaNegra(true);
        when(laneService.obtener(1L, 1000L)).thenReturn(lane1);

        assertThrows(ReglaNegocioException.class,
                () -> actividadService.crear(1L, 1000L, "Nueva", "Desc", 0, 0));
    }

    @Test
    void crear_con_nombre_duplicado_falla() {
        when(laneService.obtener(1L, 1000L)).thenReturn(lane1);
        when(nodoFlujoService.existeNombreEnProceso(
                "Nueva", 10L, 1L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> actividadService.crear(1L, 1000L, "Nueva", "Desc", 0, 0));
    }

    @Test
    void impacto_eliminacion_detecta_desconexion() {
        Actividad origen = new Actividad();
        origen.setId(20L);
        origen.setNombre("Origen");
        Actividad destino = new Actividad();
        destino.setId(30L);
        destino.setNombre("Destino");

        Arco entrada = new Arco();
        entrada.setId(100L);
        entrada.setOrigen(origen);
        entrada.setDestino(actividad);
        entrada.setActivo(true);

        Arco salida = new Arco();
        salida.setId(101L);
        salida.setOrigen(actividad);
        salida.setDestino(destino);
        salida.setActivo(true);

        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(entrada));
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(salida));
        when(arcoService.listarPorOrigen(1L, 20L)).thenReturn(List.of(entrada));
        when(arcoService.listarPorDestino(1L, 30L)).thenReturn(List.of(salida));

        var impacto = actividadService.evaluarImpactoEliminacion(1L, 1L);

        assertTrue(impacto.rompeContinuidad());
        assertEquals(3, impacto.advertencias().size());
    }

    @Test
    void eliminar_hace_baja_logica_y_desactiva_arcos() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));

        actividadService.eliminar(1L, 1L);

        assertFalse(actividad.isActivo());
        verify(arcoService).desactivarPorNodo(1L, 1L);
        verify(auditoriaModeladoService).registrar(eq(proceso), contains("eliminada"));
    }

    @Test
    void listar_por_lane_filtra_tipo_e_inactivos() {
        Actividad activa = new Actividad(); activa.setActivo(true);
        Actividad inactiva = new Actividad(); inactiva.setActivo(false);
        Gateway gateway = new Gateway(); gateway.setActivo(true);
        when(laneService.obtener(1L, 1000L)).thenReturn(lane1);
        when(nodoFlujoService.listarPorLane(1L, 1000L))
                .thenReturn(List.of(activa, inactiva, gateway));

        var resultado = actividadService.listarPorLane(1L, 1000L);

        assertEquals(1, resultado.size());
        assertSame(activa, resultado.get(0));
    }

    @Test
    @DisplayName("Editar con nombre duplicado en el proceso lanza excepcion")
    void editar_nombre_duplicado() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(nodoFlujoService.existeNombreEnProceso(
                "Tarea B", 10L, 1L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> actividadService.editar(1L, 1L, "Tarea B", "Desc", 0, 0, null));
    }

    @Test
    @DisplayName("Editar conservando el mismo nombre no lanza excepcion")
    void editar_mismo_nombre() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Actividad resultado = actividadService.editar(1L, 1L, "Tarea A", "Desc2", 10, 20, null);

        assertEquals("Tarea A", resultado.getNombre());
        verify(nodoFlujoService, never())
                .existeNombreEnProceso(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Mover actividad a lane del mismo pool funciona")
    void mover_a_lane_mismo_pool() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(laneService.obtener(1L, 2000L)).thenReturn(lane2);
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Actividad resultado = actividadService.editar(1L, 1L, "Tarea A", "Desc", 0, 0, 2000L);

        assertEquals(lane2, resultado.getLane());
    }

    @Test
    @DisplayName("Mover actividad a lane de otro pool sin arcos funciona")
    void mover_a_otro_pool_sin_arcos() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(laneService.obtener(1L, 3000L)).thenReturn(lane3);
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(Collections.emptyList());
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(Collections.emptyList());
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Actividad resultado = actividadService.editar(1L, 1L, "Tarea A", "Desc", 0, 0, 3000L);

        assertEquals(lane3, resultado.getLane());
    }

    @Test
    @DisplayName("Mover actividad a lane de otro pool con arcos lanza excepcion")
    void mover_a_otro_pool_con_arcos() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(laneService.obtener(1L, 3000L)).thenReturn(lane3);
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(List.of(new Arco()));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> actividadService.editar(1L, 1L, "Tarea A", "Desc", 0, 0, 3000L));

        assertTrue(ex.getMessage().contains("arcos conectados"));
    }

    @Test
    @DisplayName("Editar con laneId igual al actual no intenta mover")
    void editar_lane_igual_al_actual() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(nodoFlujoService.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        Actividad resultado = actividadService.editar(1L, 1L, "Tarea A", "Desc", 5, 10, 1000L);

        assertEquals(lane1, resultado.getLane());
        assertEquals(5, resultado.getPosicionX());
        verify(laneService, never()).obtener(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Mover a otro pool con arcos como destino lanza excepcion")
    void mover_a_otro_pool_con_arcos_destino() {
        when(nodoFlujoService.buscar(1L, 1L)).thenReturn(Optional.of(actividad));
        when(laneService.obtener(1L, 3000L)).thenReturn(lane3);
        when(arcoService.listarPorOrigen(1L, 1L)).thenReturn(Collections.emptyList());
        when(arcoService.listarPorDestino(1L, 1L)).thenReturn(List.of(new Arco()));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> actividadService.editar(1L, 1L, "Tarea A", "Desc", 0, 0, 3000L));

        assertTrue(ex.getMessage().contains("arcos conectados"));
    }
}
