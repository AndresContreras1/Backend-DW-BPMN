package com.facimus.procesos.modelado.service;

import java.util.List;
import java.util.Optional;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;

public interface CorrelacionService {

    Correlacion definir(Mensaje mensaje, List<Mensaje> mensajesDelProceso, String criterio);

    Correlacion obtener(Long empresaId, Long mensajeId);

    void eliminar(Long empresaId, Mensaje mensaje);

    Correlacion guardar(Correlacion correlacion);

    Optional<Correlacion> buscarPorMensaje(Long empresaId, Long mensajeId);

    void eliminarPorMensaje(Long empresaId, Long mensajeId);
}
