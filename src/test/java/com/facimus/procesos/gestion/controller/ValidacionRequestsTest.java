package com.facimus.procesos.gestion.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.controller.dto.AceptarInvitacionRequest;
import com.facimus.procesos.gestion.controller.dto.CrearUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.model.RolAcceso;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Reglas de validacion de los DTO de entrada de gestion (contrasena minima unificada, correos). */
class ValidacionRequestsTest {

    private static ValidatorFactory fabrica;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private <T> Set<String> campos(T request) {
        return fabrica.getValidator().validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("RegistroEmpresaRequest_contrasenaDeSieteCaracteres_esInvalida")
    void RegistroEmpresaRequest_contrasenaDeSieteCaracteres_esInvalida() {
        var corta = new RegistroEmpresaRequest("Acme", "900", "a@acme.com", "Ana", "ana@acme.com", "1234567");
        var valida = new RegistroEmpresaRequest("Acme", "900", "a@acme.com", "Ana", "ana@acme.com", "12345678");

        assertThat(campos(corta)).containsExactly("passwordAdmin");
        assertThat(campos(valida)).isEmpty();
    }

    @Test
    @DisplayName("CrearUsuarioRequest_contrasenaDeSieteCaracteres_esInvalida")
    void CrearUsuarioRequest_contrasenaDeSieteCaracteres_esInvalida() {
        var corta = new CrearUsuarioRequest("Ana", "ana@acme.com", "1234567", RolAcceso.EDITOR);
        var valida = new CrearUsuarioRequest("Ana", "ana@acme.com", "12345678", RolAcceso.EDITOR);

        assertThat(campos(corta)).containsExactly("password");
        assertThat(campos(valida)).isEmpty();
    }

    @Test
    @DisplayName("AceptarInvitacionRequest_contrasenaDeSieteCaracteres_esInvalida")
    void AceptarInvitacionRequest_contrasenaDeSieteCaracteres_esInvalida() {
        assertThat(campos(new AceptarInvitacionRequest("Ana", "1234567"))).containsExactly("password");
        assertThat(campos(new AceptarInvitacionRequest("Ana", "12345678"))).isEmpty();
    }

    @Test
    @DisplayName("InvitacionUsuarioRequest_emailNuloOVacio_esInvalido")
    void InvitacionUsuarioRequest_emailNuloOVacio_esInvalido() {
        assertThat(campos(new InvitacionUsuarioRequest(null, RolAcceso.EDITOR))).containsExactly("email");
        assertThat(campos(new InvitacionUsuarioRequest(" ", RolAcceso.EDITOR))).contains("email");
        assertThat(campos(new InvitacionUsuarioRequest("nuevo@acme.com", RolAcceso.EDITOR))).isEmpty();
    }

    @Test
    @DisplayName("LoginRequest_correoSinFormatoDeEmail_esInvalido")
    void LoginRequest_correoSinFormatoDeEmail_esInvalido() {
        assertThat(campos(new LoginRequest("no-es-correo", "clave"))).containsExactly("email");
        assertThat(campos(new LoginRequest("ana@acme.com", "clave"))).isEmpty();
    }
}
