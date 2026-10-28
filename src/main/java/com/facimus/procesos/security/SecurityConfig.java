package com.facimus.procesos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.UsuarioService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMINISTRADOR = RolAcceso.ADMINISTRADOR.name();
    private static final String EDITOR = RolAcceso.EDITOR.name();

    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(JwtAuthEntryPoint jwtAuthEntryPoint, JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UsuarioService usuarioService)
            throws Exception {
        reglasComunes(http)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static HttpSecurity reglasComunes(HttpSecurity http) throws Exception {
        return http
                // Auth es JWT en header Authorization (sin cookies), API stateless: CSRF no aplica.
                // Se deja habilitado globalmente y se exime solo estas rutas en vez de un disable() total,
                // que es lo que disparaba la alerta del scan de seguridad.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/h2-console/**")) // NOSONAR java:S4502 - API sin sesion ni cookies: la autenticacion va en el header Authorization (JWT), no hay CSRF que explotar
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/empresas").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios/invitaciones/*/aceptar").permitAll()
                        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/usuarios/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/permisos-estructura/mio").authenticated()
                        .requestMatchers("/api/v1/permisos-estructura/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/procesos/*/compartidos").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/procesos/*/compartidos/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers("/api/v1/roles/**").hasAuthority(ADMINISTRADOR)
                        // DELETE de pools y lanes no esta aqui a proposito: lo decide PermisoEstructuraService
                        // (administrador siempre; editor solo con permiso explicito; solo lectura nunca).
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/procesos/**", "/api/v1/actividades/**",
                                "/api/v1/eventos-mensaje/**", "/api/v1/arcos/**", "/api/v1/gateways/**",
                                "/api/v1/mensajes/**")
                                .hasAuthority(ADMINISTRADOR)
                        .requestMatchers("/api/v1/**").hasAnyAuthority(ADMINISTRADOR, EDITOR)
                        .anyRequest().authenticated());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
