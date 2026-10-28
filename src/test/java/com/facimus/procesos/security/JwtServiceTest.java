package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.model.RolAcceso;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

    private static final String SECRETO = "clave-de-pruebas-de-al-menos-32-bytes";

    private final ApiPrincipal editor = new ApiPrincipal(10L, 1L, RolAcceso.EDITOR, "ana@acme.com");

    @Test
    @DisplayName("Un token recien generado es valido y trae los datos del usuario")
    void JwtService_validar_tokenGenerado_devuelveClaimsDelPrincipal() {
        JwtService jwtService = new JwtService(SECRETO, 1800);

        Optional<Claims> claims = jwtService.validar(jwtService.generarToken(editor));

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("ana@acme.com");
        assertThat(claims.get().get(JwtService.CLAIM_USUARIO_ID, Long.class)).isEqualTo(10L);
        assertThat(claims.get().get(JwtService.CLAIM_EMPRESA_ID, Long.class)).isEqualTo(1L);
        assertThat(claims.get().get(JwtService.CLAIM_ROL, String.class)).isEqualTo("EDITOR");
    }

    @Test
    @DisplayName("Cambiar la empresa dentro del token invalida la firma")
    void JwtService_validar_payloadDeOtraEmpresa_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, 1800);
        String[] original = jwtService.generarToken(editor).split("\\.");
        String[] otraEmpresa = jwtService.generarToken(new ApiPrincipal(10L, 2L, RolAcceso.EDITOR, "ana@acme.com"))
                .split("\\.");

        String manipulado = original[0] + "." + otraEmpresa[1] + "." + original[2];

        assertThat(jwtService.validar(manipulado)).isEmpty();
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void JwtService_validar_tokenExpirado_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, -1);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void JwtService_validar_firmadoConOtraClave_devuelveVacio() {
        JwtService emisor = new JwtService("otra-clave-distinta-de-al-menos-32-bytes", 1800);
        JwtService receptor = new JwtService(SECRETO, 1800);

        assertThat(receptor.validar(emisor.generarToken(editor))).isEmpty();
    }

    @Test
    @DisplayName("Un texto que no es JWT se rechaza sin lanzar excepcion")
    void JwtService_validar_textoCualquiera_devuelveVacio() {
        JwtService jwtService = new JwtService(SECRETO, 1800);

        assertThat(jwtService.validar("no-es-un-token")).isEmpty();
    }

    @Test
    @DisplayName("Sin JWT_SECRET usa una clave aleatoria y los tokens siguen funcionando")
    void JwtService_generarToken_sinSecreto_usaClaveAleatoria() {
        JwtService jwtService = new JwtService("", 1800);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isPresent();
    }

    @Test
    @DisplayName("En perfil prod un JWT_SECRET vacio impide arrancar")
    void JwtService_constructor_perfilProdConSecretoVacio_lanzaIllegalState() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService("", 1800, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("En perfil prod un JWT_SECRET de menos de 32 caracteres impide arrancar")
    void JwtService_constructor_perfilProdConSecretoCorto_lanzaIllegalState() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService("corto", 1800, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("En perfil prod un JWT_SECRET de 32 caracteres o mas arranca")
    void JwtService_constructor_perfilProdConSecretoValido_generaTokensValidos() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        JwtService jwtService = new JwtService(SECRETO, 1800, prod);

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isPresent();
    }

    @Test
    @DisplayName("Sin perfil prod un JWT_SECRET vacio sigue usando una clave aleatoria")
    void JwtService_constructor_sinPerfilProdConSecretoVacio_usaClaveAleatoria() {
        JwtService jwtService = new JwtService("", 1800, new MockEnvironment());

        assertThat(jwtService.validar(jwtService.generarToken(editor))).isPresent();
    }
}
