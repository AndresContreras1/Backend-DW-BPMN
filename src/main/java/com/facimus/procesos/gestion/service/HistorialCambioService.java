package com.facimus.procesos.gestion.service;

import java.util.List;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;

public interface HistorialCambioService {

    void registrar(Proceso proceso, Usuario autor, String descripcion);

    List<HistorialCambio> listarPorProceso(Long empresaId, Long procesoId);
}
