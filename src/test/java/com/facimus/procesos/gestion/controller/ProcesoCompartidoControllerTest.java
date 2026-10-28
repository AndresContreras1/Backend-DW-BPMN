package com.facimus.procesos.gestion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.controller.dto.CompartirProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;

@WebMvcTest(ProcesoCompartidoController.class)
class ProcesoCompartidoControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProcesoCompartidoService service;

    @Test
    void listar_compartidos_retorna_200() throws Exception {
        ProcesoCompartidoResponse compartido = compartido();
        given(service.consultarCompartidosPorPropietario(1L, 10L)).willReturn(List.of(compartido));

        mockMvc.perform(get("/api/v1/procesos/10/compartidos")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].procesoNombre").value("Compras"))
                .andExpect(jsonPath("$[0].soloLectura").value(true));
    }

    @Test
    void compartir_retorna_201() throws Exception {
        ProcesoCompartidoResponse compartido = compartido();
        given(service.compartir(1L, 1L, 10L, new CompartirProcesoRequest(2L))).willReturn(compartido);

        mockMvc.perform(post("/api/v1/procesos/10/compartidos")
                .with(principal(RolAcceso.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"empresaInvitadaId":2}
                    """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/procesos-compartidos/10"))
                .andExpect(jsonPath("$.empresaInvitada").value("Aliada"));
    }

    @Test
    void revocar_retorna_204() throws Exception {
        doNothing().when(service).dejarDeCompartir(1L, 1L, 10L, 2L);

        mockMvc.perform(delete("/api/v1/procesos/10/compartidos/2")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listar_recibidos_retorna_200() throws Exception {
        given(service.consultarRecibidos(1L)).willReturn(List.of(compartido()));

        mockMvc.perform(get("/api/v1/procesos-compartidos")
                .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].procesoId").value(10));
    }

    @Test
    void detalle_compartido_retorna_200() throws Exception {
        ProcesoResponse proceso = new ProcesoResponse(10L, "Compras", "Proceso", "Operativo",
                EstadoProceso.BORRADOR, true, LocalDateTime.now(), LocalDateTime.now());
        ProcesoCompartidoDetalleResponse detalle = new ProcesoCompartidoDetalleResponse(proceso, "Acme", true,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        given(service.consultarCompartido(1L, 10L)).willReturn(detalle);

        mockMvc.perform(get("/api/v1/procesos-compartidos/10")
                .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.nombre").value("Compras"))
                .andExpect(jsonPath("$.empresaPropietaria").value("Acme"))
                .andExpect(jsonPath("$.soloLectura").value(true));
    }

    @Test
    void editor_no_puede_compartir() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/10/compartidos")
                .with(principal(RolAcceso.EDITOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"empresaInvitadaId":2}
                    """))
                .andExpect(status().isForbidden());
    }

    private ProcesoCompartidoResponse compartido() {
        return new ProcesoCompartidoResponse(7L, 10L, "Compras", 1L, "Acme", 2L, "Aliada", true);
    }
}
