package com.facimus.procesos.modelado.service;

import java.util.List;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;

public interface EventoMensajeService {

    EventoMensaje crear(Long empresaId, Long laneId, String nombre, TipoEventoMensaje tipoEvento, String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno);

    EventoMensaje editar(Long empresaId, Long eventoId, String nombre, TipoEventoMensaje tipoEvento, String contenido, String claveCorrelacion, int posX, int posY, boolean origenExterno);

    void eliminar(Long empresaId, Long eventoId);

    EventoMensaje obtener(Long empresaId, Long eventoId);

    List<EventoMensaje> listarPorLane(Long empresaId, Long laneId);
}
