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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.model.*;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.gestion.service.dto.ProcesoCompartidoDetalle;

@WebMvcTest(ProcesoCompartidoController.class)
@Import(ModelMapperConfig.class)
class ProcesoCompartidoControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProcesoCompartidoService service;

    @Test
    void listar_compartidos_retorna_200() throws Exception {
        ProcesoCompartido compartido = compartido();
        given(service.listarCompartidosPorPropietario(1L, 10L)).willReturn(List.of(compartido));

        mockMvc.perform(get("/api/v1/procesos/10/compartidos")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].procesoNombre").value("Compras"))
                .andExpect(jsonPath("$[0].soloLectura").value(true));
    }

    @Test
    void compartir_retorna_201() throws Exception {
        ProcesoCompartido compartido = compartido();
        given(service.compartir(1L, 1L, 10L, 2L)).willReturn(compartido);

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
        given(service.listarRecibidos(1L)).willReturn(List.of(compartido()));

        mockMvc.perform(get("/api/v1/procesos-compartidos")
                .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].procesoId").value(10));
    }

    @Test
    void detalle_compartido_retorna_200() throws Exception {
        ProcesoCompartido compartido = compartido();
        ProcesoCompartidoDetalle detalle = new ProcesoCompartidoDetalle(
                compartido.getProceso(), compartido,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        given(service.obtenerCompartido(1L, 10L)).willReturn(detalle);

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

    private ProcesoCompartido compartido() {
        Empresa propietaria = new Empresa();
        propietaria.setId(1L);
        propietaria.setNombre("Acme");

        Empresa invitada = new Empresa();
        invitada.setId(2L);
        invitada.setNombre("Aliada");

        Proceso proceso = new Proceso();
        proceso.setId(10L);
        proceso.setNombre("Compras");
        proceso.setDescripcion("Proceso");
        proceso.setCategoria("Operativo");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso.setEmpresa(propietaria);

        ProcesoCompartido compartido = new ProcesoCompartido();
        compartido.setId(7L);
        compartido.setEmpresa(propietaria);
        compartido.setProceso(proceso);
        compartido.setEmpresaInvitada(invitada);
        compartido.setSoloLectura(true);
        compartido.setActivo(true);
        return compartido;
    }
}
