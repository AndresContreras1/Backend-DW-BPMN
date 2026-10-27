package com.facimus.procesos.arquitectura;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica en el codigo las dos reglas de "refactor: ModelMapper y separacion de
 * repositorios" (PR #37, punto 4 de la review de Segiraldo0610): que ningun Service
 * inyecte el Repository de otro dominio, y que no existan ciclos de dependencia entre
 * Services.
 */
class SeparacionRepositoriosTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    /**
     * NodoFlujoRepository es la unica excepcion documentada: por la herencia JPA
     * SINGLE_TABLE de NodoFlujo (Actividad, Gateway y EventoMensaje comparten tabla y
     * discriminador), varios services de "nodos" delegan sus consultas en
     * NodoFlujoService en vez de tener cada uno su propia copia de esas queries. Ningun
     * otro Service debe inyectar ese Repository directamente.
     */
    @TestFactory
    @DisplayName("Cada Repository solo lo inyecta su Service dueno (por nombre)")
    List<DynamicTest> cada_repository_solo_lo_inyecta_su_service() {
        return clases.stream()
                .filter(clase -> clase.getSimpleName().endsWith("Repository"))
                .filter(JavaClass::isInterface)
                .<DynamicTest>map(repo -> {
                    String nombreRepo = repo.getSimpleName();
                    String nombreServiceDueno = nombreRepo.substring(0, nombreRepo.length() - "Repository".length())
                            + "Service";
                    return DynamicTest.dynamicTest(nombreRepo + " -> solo " + nombreServiceDueno, () ->
                            noClasses()
                                    .that(DescribedPredicate.not(nombreSimpleEs(nombreServiceDueno)))
                                    .and().resideInAPackage("..service..")
                                    .should().dependOnClassesThat().areAssignableTo(repo.reflect())
                                    .because("solo " + nombreServiceDueno + " debe inyectar " + nombreRepo
                                            + "; el resto debe pasar por ese service")
                                    .check(clases));
                })
                .toList();
    }

    @DisplayName("No hay ciclos de dependencia entre Services")
    @org.junit.jupiter.api.Test
    void servicios_sin_ciclos() {
        SlicesRuleDefinition.slices()
                .matching("com.facimus.procesos.(*).service.(*)")
                .namingSlices("$2")
                .should().beFreeOfCycles()
                .because("cada dependencia entre Services debe ser unidireccional; si dos Services se " +
                        "necesitan mutuamente, quien resuelve la entidad ajena debe subir a la capa de " +
                        "controller en vez de usar @Lazy")
                .check(clases);
    }

    private static DescribedPredicate<JavaClass> nombreSimpleEs(String nombre) {
        return DescribedPredicate.describe("tiene nombre simple '" + nombre + "'",
                clase -> clase.getSimpleName().equals(nombre));
    }
}
