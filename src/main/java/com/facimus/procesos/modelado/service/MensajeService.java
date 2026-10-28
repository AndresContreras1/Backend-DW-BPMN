package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.MensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EditarMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import java.util.List;
import java.util.Optional;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;

public interface MensajeService {

    Mensaje crear(Long empresaId, Long procesoId, String nombre, String contenido, Long poolOrigenId, Long poolDestinoId);

    Mensaje crear(Long empresaId, Long procesoId, String nombre, String contenido, Long poolOrigenId, Long poolDestinoId, Long eventoThrowId, Long eventoCatchId, String claveCorrelacion, TipoDestinoExterno tipoDestinoExterno, String destinoExterno, PoliticaFalloNotificacion politicaFalloNotificacion, Long actividadErrorId, PoliticaMensajeSinCaso politicaSinCaso);

    Mensaje editar(Long empresaId, Long mensajeId, String nombre, String contenido);

    Mensaje editar(Long empresaId, Long mensajeId, String nombre, String contenido, Long eventoCatchId, String claveCorrelacion, TipoDestinoExterno tipoDestinoExterno, String destinoExterno, PoliticaFalloNotificacion politicaFalloNotificacion, Long actividadErrorId, PoliticaMensajeSinCaso politicaSinCaso);

    void eliminar(Long empresaId, Long mensajeId);

    List<Mensaje> listarPorProceso(Long empresaId, Long procesoId);

    Mensaje obtener(Long empresaId, Long mensajeId);

    Mensaje guardar(Mensaje mensaje);

    Optional<Mensaje> buscar(Long empresaId, Long mensajeId);

    List<Mensaje> listarTodosPorProceso(Long empresaId, Long procesoId);

    boolean tieneMensajesActivosEnPool(Long empresaId, Long poolId);

    /** Mensajes activos que usan el evento como Message Throw o Message Catch. */
    List<Mensaje> listarActivosPorEvento(Long empresaId, Long eventoId);

    MensajeResponse crear(Long empresaId, Long procesoId, MensajeRequest request);

    MensajeResponse editar(Long empresaId, Long mensajeId, EditarMensajeRequest request);

    List<MensajeResponse> consultarPorProceso(Long empresaId, Long procesoId);

    MensajeResponse consultarDetalle(Long empresaId, Long mensajeId);
}
