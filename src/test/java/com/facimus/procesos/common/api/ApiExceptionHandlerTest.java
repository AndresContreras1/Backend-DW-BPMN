package com.facimus.procesos.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("ApiExceptionHandler_manejarIntegridadDeDatos_violacionDeRestriccion_devuelve409SinExponerElSql")
    void ApiExceptionHandler_manejarIntegridadDeDatos_violacionDeRestriccion_devuelve409SinExponerElSql() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Unique index violation: UK_USUARIOS_EMAIL] "
                        + "[insert into usuarios (email) values (?)]");

        ProblemDetail problema = handler.manejarIntegridadDeDatos(ex,
                new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/usuarios")));

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getTitle()).isEqualTo("Conflicto de datos");
        assertThat(problema.getDetail()).doesNotContainIgnoringCase("insert")
                .doesNotContainIgnoringCase("UK_USUARIOS")
                .doesNotContainIgnoringCase("sql");
        assertThat(problema.getInstance()).hasToString("/api/v1/usuarios");
    }
}
