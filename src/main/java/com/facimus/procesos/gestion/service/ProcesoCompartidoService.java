package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.controller.dto.CompartirProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoResponse;

import java.util.List;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.service.dto.ProcesoCompartidoDetalle;

public interface ProcesoCompartidoService {

    ProcesoCompartido compartir(Long empresaId, Long usuarioId, Long procesoId, Long empresaInvitadaId);

    void dejarDeCompartir(Long empresaId, Long usuarioId, Long procesoId, Long empresaInvitadaId);

    List<ProcesoCompartido> listarCompartidosPorPropietario(Long empresaId, Long procesoId);

    List<ProcesoCompartido> listarRecibidos(Long empresaInvitadaId);

    ProcesoCompartidoDetalle obtenerCompartido(Long empresaInvitadaId, Long procesoId);

    List<ProcesoCompartidoResponse> consultarCompartidosPorPropietario(Long empresaId, Long procesoId);
    ProcesoCompartidoResponse compartir(Long empresaId, Long usuarioId, Long procesoId, CompartirProcesoRequest request);
    List<ProcesoCompartidoResponse> consultarRecibidos(Long empresaInvitadaId);
    ProcesoCompartidoDetalleResponse consultarCompartido(Long empresaInvitadaId, Long procesoId);
}
