package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.PoliticaFalloNotificacion;
import com.facimus.procesos.modelado.model.PoliticaMensajeSinCaso;
import com.facimus.procesos.modelado.model.TipoDestinoExterno;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditarMensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El contenido es obligatorio.") String contenido,
        Long eventoCatchId,
        String claveCorrelacion,
        TipoDestinoExterno tipoDestinoExterno,
        @Size(max = 200, message = "El destino externo admite como maximo 200 caracteres.")
        @Schema(description = "Descripcion del destino (por ejemplo \"API de facturacion\"). Es solo documentacion: "
                + "no se guardan credenciales, tokens ni direcciones reales.", maxLength = 200)
        String destinoExterno,
        PoliticaFalloNotificacion politicaFalloNotificacion,
        Long actividadErrorId,
        PoliticaMensajeSinCaso politicaSinCaso) {

    public EditarMensajeRequest(String nombre, String contenido) {
        this(nombre, contenido, null, null, null, null, null, null, null);
    }
}
