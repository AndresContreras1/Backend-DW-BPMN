package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.controller.dto.EditarMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;
import com.facimus.procesos.modelado.repository.MensajeRepository;

/** Metodos de MensajeService que devuelven el DTO de respuesta (mapeo dentro del service). */
@ExtendWith(MockitoExtension.class)
class MensajeServiceMapeoTest {

    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private PoolService poolService;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private CorrelacionService correlacionService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private AuditoriaModeladoService auditoriaModeladoService;
    @Spy
    private ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    @Spy
    @InjectMocks
    private MensajeServiceImpl mensajeService;

    @Test
    void MensajeServiceImpl_crearSinEventoThrow_usaFirmaCortaYDevuelveResponse() {
        doReturn(mensaje(2L, "Factura")).when(mensajeService).crear(1L, 10L, "Factura", "Contenido", 1L, 2L);

        MensajeResponse r = mensajeService.crear(1L, 10L, new MensajeRequest("Factura", "Contenido", 1L, 2L));

        assertThat(r.id()).isEqualTo(2L);
        assertThat(r.poolOrigenId()).isEqualTo(1L);
        assertThat(r.poolDestinoId()).isEqualTo(2L);
        assertThat(r.procesoId()).isEqualTo(10L);
        assertThat(r.sinReceptor()).isTrue();
    }

    @Test
    void MensajeServiceImpl_crearConEventoThrow_usaFirmaCompletaYDevuelveResponse() {
        doReturn(mensaje(2L, "Factura")).when(mensajeService).crear(1L, 10L, "Factura", "Contenido", 1L, 2L,
                30L, 40L, "clave", TipoDestinoExterno.CORREO, "a@b.c",
                PoliticaFalloNotificacion.CONTINUAR_FLUJO, 50L, PoliticaMensajeSinCaso.DESCARTAR);

        MensajeResponse r = mensajeService.crear(1L, 10L, new MensajeRequest("Factura", "Contenido", 1L, 2L,
                30L, 40L, "clave", TipoDestinoExterno.CORREO, "a@b.c",
                PoliticaFalloNotificacion.CONTINUAR_FLUJO, 50L, PoliticaMensajeSinCaso.DESCARTAR));

        assertThat(r.id()).isEqualTo(2L);
        verify(mensajeService, never()).crear(1L, 10L, "Factura", "Contenido", 1L, 2L);
    }

    @Test
    void MensajeServiceImpl_editarLegacy_usaFirmaCortaYDevuelveResponse() {
        doReturn(mensaje(1L, "Nuevo")).when(mensajeService).editar(1L, 1L, "Nuevo", "Contenido");

        MensajeResponse r = mensajeService.editar(1L, 1L, new EditarMensajeRequest("Nuevo", "Contenido"));

        assertThat(r.nombre()).isEqualTo("Nuevo");
        verify(mensajeService, never()).editar(anyLong(), anyLong(), anyString(), anyString(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void MensajeServiceImpl_editarCompleto_usaFirmaCompletaYDevuelveResponse() {
        Mensaje conThrow = mensaje(1L, "Nuevo");
        conThrow.setEventoThrow(new EventoMensaje());
        doReturn(conThrow).when(mensajeService).obtener(1L, 1L);
        doReturn(mensaje(1L, "Nuevo")).when(mensajeService).editar(1L, 1L, "Nuevo", "Contenido", 40L, "clave",
                TipoDestinoExterno.COLA, "cola-1", PoliticaFalloNotificacion.FINALIZAR_PROCESO, 50L,
                PoliticaMensajeSinCaso.INICIAR_CASO_NUEVO);

        MensajeResponse r = mensajeService.editar(1L, 1L, new EditarMensajeRequest("Nuevo", "Contenido", 40L,
                "clave", TipoDestinoExterno.COLA, "cola-1", PoliticaFalloNotificacion.FINALIZAR_PROCESO, 50L,
                PoliticaMensajeSinCaso.INICIAR_CASO_NUEVO));

        assertThat(r.nombre()).isEqualTo("Nuevo");
    }

    @Test
    void MensajeServiceImpl_editarCamposAvanzadosSinThrow_lanza400() {
        doReturn(mensaje(1L, "Nuevo")).when(mensajeService).obtener(1L, 1L);

        assertThatThrownBy(() -> mensajeService.editar(1L, 1L, new EditarMensajeRequest("Nuevo", "Contenido", null,
                "clave", null, null, null, null, null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting(e -> ((org.springframework.web.server.ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void MensajeServiceImpl_consultarPorProceso_devuelveListaDeResponses() {
        doReturn(List.of(mensaje(1L, "A"), mensaje(2L, "B"))).when(mensajeService).listarPorProceso(1L, 10L);

        List<MensajeResponse> r = mensajeService.consultarPorProceso(1L, 10L);

        assertThat(r).extracting(MensajeResponse::nombre).containsExactly("A", "B");
    }

    @Test
    void MensajeServiceImpl_consultarDetalle_devuelveResponse() {
        doReturn(mensaje(1L, "A")).when(mensajeService).obtener(1L, 1L);

        assertThat(mensajeService.consultarDetalle(1L, 1L).contenido()).isEqualTo("Contenido");
    }

    private Mensaje mensaje(Long id, String nombre) {
        Pool origen = new Pool();
        origen.setId(1L);
        Pool destino = new Pool();
        destino.setId(2L);
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        Mensaje m = new Mensaje();
        m.setId(id);
        m.setNombre(nombre);
        m.setContenido("Contenido");
        m.setPoolOrigen(origen);
        m.setPoolDestino(destino);
        m.setProceso(proceso);
        return m;
    }
}
