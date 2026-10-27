package com.facimus.procesos.modelado.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;

import lombok.RequiredArgsConstructor;

/**
 * No depende de MensajeService: el llamador (MensajeService cuando sincroniza su propia
 * correlacion, o CorrelacionController para el endpoint dedicado) resuelve el Mensaje -y,
 * para la validacion de ambiguedad, la lista de mensajes del mismo proceso- y los pasa ya
 * cargados, para no crear una dependencia circular MensajeService<->CorrelacionService.
 * Actualizar Mensaje.claveCorrelacion y persistirlo (mensajeService.guardar) tambien queda
 * a cargo del llamador.
 */
@Service
@RequiredArgsConstructor
public class CorrelacionService {

    private final CorrelacionRepository correlacionRepository;
    private final AuditoriaModeladoService auditoriaModeladoService;

    @Transactional
    public Correlacion definir(Mensaje mensaje, List<Mensaje> mensajesDelProceso, String criterio) {
        validarAmbiguedad(mensaje, mensajesDelProceso, criterio);

        EventoMensaje eventoThrow = mensaje.getEventoThrow();
        EventoMensaje eventoCatch = mensaje.getEventoCatch();
        if (eventoThrow != null && !criterio.equalsIgnoreCase(eventoThrow.getClaveCorrelacion())) {
            throw new ReglaNegocioException("La correlacion debe coincidir con la definida en el Message Throw.");
        }
        if (eventoCatch != null && !criterio.equalsIgnoreCase(eventoCatch.getClaveCorrelacion())) {
            throw new ReglaNegocioException("La correlacion debe coincidir con la definida en el Message Catch.");
        }

        Correlacion correlacion = correlacionRepository.findByMensajeIdAndEmpresaId(mensaje.getId(),
                        mensaje.getEmpresa().getId())
                .orElseGet(() -> {
                    Correlacion nueva = new Correlacion();
                    nueva.setEmpresa(mensaje.getEmpresa());
                    nueva.setMensaje(mensaje);
                    return nueva;
                });
        correlacion.setCriterio(criterio);
        correlacion = correlacionRepository.save(correlacion);
        auditoriaModeladoService.registrar(mensaje.getProceso(),
                "Correlacion actualizada para el mensaje " + mensaje.getNombre() + ".");
        return correlacion;
    }

    @Transactional(readOnly = true)
    public Correlacion obtener(Long empresaId, Long mensajeId) {
        return correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Este mensaje no tiene correlacion definida."));
    }

    @Transactional
    public void eliminar(Long empresaId, Mensaje mensaje) {
        Correlacion correlacion = correlacionRepository.findByMensajeIdAndEmpresaId(mensaje.getId(), empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Este mensaje no tiene correlacion definida."));
        correlacionRepository.delete(correlacion);
        auditoriaModeladoService.registrar(mensaje.getProceso(),
                "Correlacion eliminada del mensaje " + mensaje.getNombre() + ".");
    }

    @Transactional
    public Correlacion guardar(Correlacion correlacion) {
        return correlacionRepository.save(correlacion);
    }

    @Transactional(readOnly = true)
    public Optional<Correlacion> buscarPorMensaje(Long empresaId, Long mensajeId) {
        return correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId);
    }

    @Transactional
    public void eliminarPorMensaje(Long empresaId, Long mensajeId) {
        correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId)
                .ifPresent(correlacionRepository::delete);
    }

    private void validarAmbiguedad(Mensaje mensaje, List<Mensaje> mensajesDelProceso, String criterio) {
        boolean ambiguo = mensajesDelProceso.stream()
                .filter(otro -> !otro.getId().equals(mensaje.getId()))
                .anyMatch(otro -> otro.getNombre().equalsIgnoreCase(mensaje.getNombre())
                        && criterio.equalsIgnoreCase(otro.getClaveCorrelacion() != null
                                ? otro.getClaveCorrelacion() : ""));
        if (ambiguo) {
            throw new ReglaNegocioException(
                    "La combinacion nombre y clave de correlacion es ambigua dentro del proceso.");
        }
    }
}
