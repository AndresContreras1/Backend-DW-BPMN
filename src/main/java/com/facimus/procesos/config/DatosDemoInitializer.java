package com.facimus.procesos.config;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Crea dos empresas de demostracion con su administrador en el primer arranque, unicamente si la
 * base de datos esta vacia, para poder iniciar sesion sin pasar antes por el formulario de registro
 * y probar el aislamiento entre empresas. Solo corre en el perfil por defecto: ni en prod ni en test.
 */
@Component
@Profile("default")
@Transactional
@RequiredArgsConstructor
public class DatosDemoInitializer implements CommandLineRunner {

    private static final String PASSWORD_DEMO = "admin123";

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }

        crearEmpresaConAdministrador("Empresa Demo S.A.S.", "900123456-1", "contacto@demo.com",
                "Administrador Demo", "admin@demo.com");
        crearEmpresaConAdministrador("Empresa Beta S.A.S.", "900654321-2", "contacto@beta.com",
                "Administrador Beta", "admin@beta.com");
    }

    private void crearEmpresaConAdministrador(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin) {
        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        empresa.setFechaRegistro(LocalDate.now());
        empresa = empresaRepository.save(empresa);

        Usuario admin = new Usuario();
        admin.setEmpresa(empresa);
        admin.setNombre(nombreAdmin);
        admin.setEmail(emailAdmin);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD_DEMO));
        admin.setRolAcceso(RolAcceso.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);
    }
}
