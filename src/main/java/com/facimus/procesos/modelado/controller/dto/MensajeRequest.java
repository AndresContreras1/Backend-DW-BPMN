package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El contenido es obligatorio.") String contenido,
        @NotNull(message = "El pool de origen es obligatorio.") Long poolOrigenId,
        @NotNull(message = "El pool de destino es obligatorio.") Long poolDestinoId,
        Long eventoThrowId,
        Long eventoCatchId,
        @Schema(description = "Si se omite, hereda la clave del Message Throw.") String claveCorrelacion,
        TipoDestinoExterno tipoDestinoExterno,
        @Size(max = 200, message = "El destino externo admite como maximo 200 caracteres.")
        @Schema(description = "Descripcion del destino (por ejemplo \"API de facturacion\"). Es solo documentacion: "
                + "no se guardan credenciales, tokens ni direcciones reales.", maxLength = 200)
        String destinoExterno,
        PoliticaFalloNotificacion politicaFalloNotificacion,
        Long actividadErrorId,
        PoliticaMensajeSinCaso politicaSinCaso) {

    public MensajeRequest(String nombre, String contenido, Long poolOrigenId, Long poolDestinoId) {
        this(nombre, contenido, poolOrigenId, poolDestinoId, null, null, null, null, null, null, null, null);
    }

    /**
     * Sin eventoThrowId solo se admite el mensaje basico (nombre, contenido y pools): los datos de
     * correlacion, destino externo y politicas se rechazan con 400 en vez de descartarse en silencio.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Los campos de correlacion, destino externo y politicas requieren eventoThrowId.")
    public boolean isCamposAvanzadosConThrow() {
        return eventoThrowId != null
                || (eventoCatchId == null && claveCorrelacion == null && tipoDestinoExterno == null
                        && destinoExterno == null && politicaFalloNotificacion == null
                        && actividadErrorId == null && politicaSinCaso == null);
    }
}
