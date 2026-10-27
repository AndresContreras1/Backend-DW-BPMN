package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.service.EventoMensajeService;

@WebMvcTest(EventoMensajeController.class)
@Import(ModelMapperConfig.class)
class EventoMensajeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EventoMensajeService service;

    @Test
    void crear_retorna_201() throws Exception {
        EventoMensaje evento = evento(5L, "Orden", TipoEventoMensaje.THROW);
        given(service.crear(eq(1L), eq(2L), anyString(), eq(TipoEventoMensaje.THROW),
                anyString(), anyString(), anyInt(), anyInt(), anyBoolean())).willReturn(evento);

        mockMvc.perform(post("/api/v1/lanes/2/eventos-mensaje")
                .with(principal(RolAcceso.EDITOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre":"Orden","tipoEvento":"THROW","contenido":"payload",
                     "claveCorrelacion":"pedidoId","posicionX":10,"posicionY":20,"origenExterno":false}
                    """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/eventos-mensaje/5"))
                .andExpect(jsonPath("$.tipoEvento").value("THROW"));
    }

    @Test
    void detalle_y_listado_retornan_200() throws Exception {
        EventoMensaje evento = evento(5L, "Orden", TipoEventoMensaje.CATCH_INTERMEDIO);
        given(service.obtener(1L, 5L)).willReturn(evento);
        given(service.listarPorLane(1L, 2L)).willReturn(List.of(evento));

        mockMvc.perform(get("/api/v1/eventos-mensaje/5").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Orden"));

        mockMvc.perform(get("/api/v1/lanes/2/eventos-mensaje").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].laneId").value(2));
    }

    @Test
    void editar_retorna_200() throws Exception {
        EventoMensaje evento = evento(5L, "Orden v2", TipoEventoMensaje.CATCH_INTERMEDIO);
        given(service.editar(eq(1L), eq(5L), anyString(), any(), anyString(), anyString(),
                anyInt(), anyInt(), anyBoolean())).willReturn(evento);

        mockMvc.perform(put("/api/v1/eventos-mensaje/5")
                .with(principal(RolAcceso.EDITOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre":"Orden v2","tipoEvento":"CATCH_INTERMEDIO","contenido":"payload",
                     "claveCorrelacion":"pedidoId","posicionX":10,"posicionY":20,"origenExterno":false}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Orden v2"));
    }

    @Test
    void eliminar_como_admin_retorna_204() throws Exception {
        doNothing().when(service).eliminar(1L, 5L);
        mockMvc.perform(delete("/api/v1/eventos-mensaje/5")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    void sin_sesion_retorna_401() throws Exception {
        mockMvc.perform(get("/api/v1/eventos-mensaje/5"))
                .andExpect(status().isUnauthorized());
    }

    private EventoMensaje evento(Long id, String nombre, TipoEventoMensaje tipo) {
        Lane lane = new Lane(); lane.setId(2L);
        EventoMensaje e = new EventoMensaje();
        e.setId(id);
        e.setNombre(nombre);
        e.setTipoEvento(tipo);
        e.setContenido("payload");
        e.setClaveCorrelacion("pedidoId");
        e.setPosicionX(10);
        e.setPosicionY(20);
        e.setLane(lane);
        e.setActivo(true);
        return e;
    }
}
