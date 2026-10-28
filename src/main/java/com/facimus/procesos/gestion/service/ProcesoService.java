package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.controller.dto.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.EditarProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.HistorialCambioResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

public interface ProcesoService {

    Proceso crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria);

    Proceso editarDatos(Long empresaId, Long procesoId, Long usuarioId, String nombre, String descripcion, String categoria);

    Proceso publicar(Long empresaId, Long procesoId, Long usuarioId);

    Proceso cambiarEstado(Long empresaId, Long procesoId, Long usuarioId, EstadoProceso nuevoEstado);

    void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId);

    Page<Proceso> buscar(Long empresaId, String nombre, EstadoProceso estado, String categoria, Boolean activo, Pageable pageable);

    boolean existe(Long empresaId, Long procesoId);

    Proceso obtener(Long empresaId, Long procesoId);

    ProcesoResponse editarDatos(Long empresaId, Long procesoId, Long usuarioId, EditarProcesoRequest request);
    ProcesoResponse cambiarEstado(Long empresaId, Long procesoId, Long usuarioId, CambiarEstadoProcesoRequest request);
    PageResponse<ProcesoResponse> consultarPagina(Long empresaId, String nombre, EstadoProceso estado, String categoria, Boolean activo, int pagina);
    ProcesoDetalleResponse consultarDetalle(Long empresaId, Long procesoId);
    List<HistorialCambioResponse> consultarHistorial(Long empresaId, Long procesoId);
}
