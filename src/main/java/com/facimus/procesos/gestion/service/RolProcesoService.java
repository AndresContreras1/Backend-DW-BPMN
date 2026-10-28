package com.facimus.procesos.gestion.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.dto.RolProcesoConsulta;
import com.facimus.procesos.gestion.service.dto.RolProcesoVista;

public interface RolProcesoService {

    RolProceso crear(Long empresaId, String nombre, String descripcion);

    RolProceso editar(Long empresaId, Long rolId, String nombre, String descripcion);

    void eliminar(Long empresaId, Long rolId);

    List<RolProcesoVista> listarConUso(Long empresaId);

    Page<RolProcesoConsulta> buscarConProcesos(Long empresaId, String nombre, Pageable pageable);

    long contarUsos(Long empresaId, Long rolId);

    RolProceso obtener(Long empresaId, Long rolId);
}
