package com.facimus.procesos.common;

/**
 * Se lanza cuando la peticion es incoherente por si misma (combinacion de campos que no tiene sentido),
 * a diferencia de {@link ReglaNegocioException}, que depende del estado de los datos. El manejador global
 * la traduce a 400.
 */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
