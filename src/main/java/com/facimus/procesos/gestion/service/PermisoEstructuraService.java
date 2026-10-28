package com.facimus.procesos.gestion.service;

import java.util.List;
import com.facimus.procesos.gestion.model.PermisoEstructura;
import com.facimus.procesos.gestion.model.RolAcceso;

public interface PermisoEstructuraService {

    void validar(Long empresaId, RolAcceso rol, OperacionEstructura operacion);

    PermisoEstructura obtener(Long empresaId, RolAcceso rol);

    List<PermisoEstructura> listar(Long empresaId);

    PermisoEstructura actualizar(Long empresaId, RolAcceso rol, boolean crearPool, boolean editarPool, boolean eliminarPool, boolean crearLane, boolean editarLane, boolean eliminarLane);
}
