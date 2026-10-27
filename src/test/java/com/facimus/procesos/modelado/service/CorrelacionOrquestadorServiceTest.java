package com.facimus.procesos.modelado.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;

/**
 * El caso de uso HU-28 (definir/eliminar la correlacion de un mensaje) debe quedar atomico: si
 * la segunda escritura (MensajeService.guardar) falla, la review de PR #37 exige que la
 * Correlacion no quede persistida sin reflejarse en Mensaje.claveCorrelacion. A nivel unitario se
 * confirma el orden de llamadas y que una excepcion en el segundo paso se propaga sin swallow.
 */
@ExtendWith(MockitoExtension.class)
class CorrelacionOrquestadorServiceTest {

    @Mock
    private MensajeService mensajeService;
    @Mock
    private CorrelacionService correlacionService;

    @InjectMocks
    private CorrelacionOrquestadorService correlacionOrquestadorService;

    private Mensaje mensaje;
    private Correlacion correlacion;

    @BeforeEach
    void setUp() {
        Proceso proceso = new Proceso();
        proceso.setId(10L);

        mensaje = new Mensaje();
        mensaje.setId(5L);
        mensaje.setNombre("Orden");
        mensaje.setProceso(proceso);

        correlacion = new Correlacion();
        correlacion.setId(1L);
        correlacion.setCriterio("orderId");
        correlacion.setMensaje(mensaje);
    }

    @Test
    @DisplayName("Definir resuelve el mensaje, define la correlacion y guarda el mensaje, en ese orden")
    void definir_llama_en_orden() {
        when(mensajeService.obtener(1L, 5L)).thenReturn(mensaje);
        when(mensajeService.listarTodosPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.definir(mensaje, List.of(mensaje), "orderId")).thenReturn(correlacion);

        Correlacion resultado = correlacionOrquestadorService.definir(1L, 5L, "orderId");

        assertSame(correlacion, resultado);
        InOrder orden = inOrder(correlacionService, mensajeService);
        orden.verify(correlacionService).definir(mensaje, List.of(mensaje), "orderId");
        orden.verify(mensajeService).guardar(mensaje);
        assertEquals("orderId", mensaje.getClaveCorrelacion());
    }

    @Test
    @DisplayName("Si falla correlacionService.definir, no se guarda el mensaje")
    void definir_no_guarda_mensaje_si_falla_correlacion() {
        when(mensajeService.obtener(1L, 5L)).thenReturn(mensaje);
        when(mensajeService.listarTodosPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.definir(any(Mensaje.class), any(), anyString()))
                .thenThrow(new ReglaNegocioException("ambiguo"));

        assertThrows(ReglaNegocioException.class, () -> correlacionOrquestadorService.definir(1L, 5L, "orderId"));

        verify(mensajeService, never()).guardar(any());
    }

    @Test
    @DisplayName("Si falla mensajeService.guardar, la excepcion se propaga")
    void definir_propaga_excepcion_al_guardar_mensaje() {
        when(mensajeService.obtener(1L, 5L)).thenReturn(mensaje);
        when(mensajeService.listarTodosPorProceso(1L, 10L)).thenReturn(List.of(mensaje));
        when(correlacionService.definir(any(Mensaje.class), any(), anyString())).thenReturn(correlacion);
        when(mensajeService.guardar(mensaje)).thenThrow(new RuntimeException("fallo de base de datos"));

        assertThrows(RuntimeException.class, () -> correlacionOrquestadorService.definir(1L, 5L, "orderId"));
    }

    @Test
    @DisplayName("Eliminar resuelve el mensaje, elimina la correlacion y guarda el mensaje")
    void eliminar_llama_en_orden() {
        mensaje.setClaveCorrelacion("orderId");
        when(mensajeService.obtener(1L, 5L)).thenReturn(mensaje);

        correlacionOrquestadorService.eliminar(1L, 5L);

        InOrder orden = inOrder(correlacionService, mensajeService);
        orden.verify(correlacionService).eliminar(1L, mensaje);
        orden.verify(mensajeService).guardar(mensaje);
        assertNull(mensaje.getClaveCorrelacion());
    }

    @Test
    @DisplayName("Si falla correlacionService.eliminar, no se guarda el mensaje")
    void eliminar_no_guarda_mensaje_si_falla_correlacion() {
        when(mensajeService.obtener(1L, 5L)).thenReturn(mensaje);
        org.mockito.Mockito.doThrow(new com.facimus.procesos.common.RecursoNoEncontradoException("sin correlacion"))
                .when(correlacionService).eliminar(eq(1L), eq(mensaje));

        assertThrows(com.facimus.procesos.common.RecursoNoEncontradoException.class,
                () -> correlacionOrquestadorService.eliminar(1L, 5L));

        verify(mensajeService, never()).guardar(any());
    }
}
