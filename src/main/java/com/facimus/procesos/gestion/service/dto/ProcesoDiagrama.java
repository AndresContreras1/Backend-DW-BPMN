package com.facimus.procesos.gestion.service.dto;

import java.util.List;

import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;

public record ProcesoDiagrama(
        Proceso proceso,
        List<Pool> pools,
        List<Lane> lanes,
        List<Actividad> actividades,
        List<Gateway> gateways,
        List<EventoMensaje> eventos,
        List<Arco> arcos,
        List<Mensaje> mensajes) {
}
