package com.facimus.procesos.gestion.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.repository.EmpresaRepository;

import lombok.RequiredArgsConstructor;

/**
 * HU-01: registro de empresa. La creacion del usuario administrador inicial la orquesta
 * EmpresaController (llama a UsuarioService despues de este metodo) para no crear una
 * dependencia circular EmpresaService<->UsuarioService.
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    @Transactional
    public Empresa registrar(String nombre, String nit, String correoContacto) {
        if (empresaRepository.existsByNit(nit)) {
            throw new ReglaNegocioException("Ya existe una empresa registrada con el NIT " + nit + ".");
        }

        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        empresa.setFechaRegistro(LocalDate.now());
        return empresaRepository.save(empresa);
    }

    @Transactional(readOnly = true)
    public Empresa obtener(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    @Transactional(readOnly = true)
    public Optional<Empresa> buscarPorNit(String nit) {
        return empresaRepository.findByNit(nit);
    }
}
