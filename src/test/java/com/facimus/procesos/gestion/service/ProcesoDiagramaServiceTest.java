package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.*;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.NodoFlujoService;
import com.facimus.procesos.modelado.service.PoolService;

@ExtendWith(MockitoExtension.class)
class ProcesoDiagramaServiceTest {

    @Mock private ProcesoService procesoService;
    @Mock private PoolService poolService;
    @Mock private LaneService laneService;
    @Mock private NodoFlujoService nodoFlujoService;
    @Mock private ArcoService arcoService;
    @Mock private MensajeService mensajeService;

    @InjectMocks
    private ProcesoDiagramaService service;

    @Test
    void obtener_clasifica_nodos_y_excluye_inactivos() {
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Pool pool = new Pool(); pool.setId(1L); pool.setProceso(proceso);
        Lane lane = new Lane(); lane.setId(2L); lane.setPool(pool);

        Actividad actividad = new Actividad(); actividad.setId(3L); actividad.setActivo(true);
        Gateway gateway = new Gateway(); gateway.setId(4L); gateway.setActivo(true);
        EventoMensaje evento = new EventoMensaje(); evento.setId(5L); evento.setActivo(true);

        Arco arco = new Arco(); arco.setId(7L); arco.setActivo(true);
        Mensaje mensaje = new Mensaje(); mensaje.setId(9L);

        when(procesoService.obtener(1L, 10L)).thenReturn(proceso);
        when(poolService.listarPorProceso(1L, 10L)).thenReturn(List.of(pool));
        when(laneService.listarPorProceso(1L, 10L)).thenReturn(List.of(lane));
        when(nodoFlujoService.listarActivosPorProceso(1L, 10L))
                .thenReturn(List.of(actividad, gateway, evento));
        when(arcoService.listarActivosPorProceso(1L, 10L)).thenReturn(List.of(arco));
        when(mensajeService.listarPorProceso(1L, 10L)).thenReturn(List.of(mensaje));

        var detalle = service.obtener(1L, 10L);

        assertEquals(1, detalle.actividades().size());
        assertEquals(1, detalle.gateways().size());
        assertEquals(1, detalle.eventos().size());
        assertEquals(1, detalle.arcos().size());
        assertEquals(1, detalle.mensajes().size());
    }

    @Test
    void proceso_inexistente_falla() {
        when(procesoService.obtener(1L, 10L)).thenThrow(new RecursoNoEncontradoException("Proceso no encontrado."));
        assertThrows(RecursoNoEncontradoException.class, () -> service.obtener(1L, 10L));
    }
}
