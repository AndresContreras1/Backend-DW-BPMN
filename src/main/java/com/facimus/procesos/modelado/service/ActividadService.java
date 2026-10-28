package com.facimus.procesos.modelado.service;

import java.util.List;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

public interface ActividadService {

    Actividad crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX, int posY);

    Actividad crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX, int posY, TipoActividad tipoActividad);

    Actividad editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX, int posY, Long laneId);

    Actividad editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX, int posY, Long laneId, TipoActividad tipoActividad);

    ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long actividadId);

    void eliminar(Long empresaId, Long actividadId);

    Actividad obtener(Long empresaId, Long actividadId);

    List<Actividad> listarPorLane(Long empresaId, Long laneId);
}
