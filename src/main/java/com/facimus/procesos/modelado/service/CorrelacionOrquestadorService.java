package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;

import lombok.RequiredArgsConstructor;

/**
 * Orquestador transaccional del caso de uso HU-28 (definir/eliminar la correlacion de un mensaje,
 * que tambien actualiza Mensaje.claveCorrelacion). Depende de MensajeService y CorrelacionService,
 * ninguno de los cuales depende de este orquestador, asi que no reintroduce el ciclo
 * MensajeService<->CorrelacionService que el refactor elimino.
 */
@Service
@RequiredArgsConstructor
public class CorrelacionOrquestadorService {

    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    @Transactional
    public Correlacion definir(Long empresaId, Long mensajeId, String criterio) {
        Mensaje mensaje = mensajeService.obtener(empresaId, mensajeId);
        List<Mensaje> mensajesDelProceso = mensajeService.listarTodosPorProceso(empresaId,
                mensaje.getProceso().getId());
        Correlacion correlacion = correlacionService.definir(mensaje, mensajesDelProceso, criterio);
        mensaje.setClaveCorrelacion(criterio);
        mensajeService.guardar(mensaje);
        return correlacion;
    }

    @Transactional
    public void eliminar(Long empresaId, Long mensajeId) {
        Mensaje mensaje = mensajeService.obtener(empresaId, mensajeId);
        correlacionService.eliminar(empresaId, mensaje);
        mensaje.setClaveCorrelacion(null);
        mensajeService.guardar(mensaje);
    }
}
