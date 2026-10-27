package com.facimus.procesos.gestion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;

import lombok.RequiredArgsConstructor;

/**
 * Orquestador transaccional del caso de uso HU-01 (registrar empresa + su administrador inicial).
 * Depende de EmpresaService y UsuarioService, ninguno de los cuales depende de este orquestador,
 * asi que no reintroduce el ciclo EmpresaService<->UsuarioService que el refactor elimino.
 */
@Service
@RequiredArgsConstructor
public class EmpresaOrquestadorService {

    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;

    @Transactional
    public Empresa registrarConAdministrador(String nombreEmpresa, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin) {
        Empresa empresa = empresaService.registrar(nombreEmpresa, nit, correoContacto);
        usuarioService.crearColaborador(empresa.getId(), nombreAdmin, emailAdmin, passwordAdmin,
                RolAcceso.ADMINISTRADOR);
        return empresa;
    }
}
