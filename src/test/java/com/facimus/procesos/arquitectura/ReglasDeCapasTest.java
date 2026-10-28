package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

import jakarta.persistence.Entity;

/**
 * Reglas del patron de capas: controller -> service -> repository. La seguridad y el inicializador
 * de datos son los unicos que se salen del flujo (el segundo, a proposito: habla con repositorios).
 */
class ReglasDeCapasTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    @Test
    @DisplayName("Las capas se respetan: controller -> service -> repository")
    void las_capas_se_respetan() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("..controller")
                .layer("Service").definedBy("..service..")
                .layer("Repository").definedBy("..repository..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .ignoreDependency(resideInAPackage("com.facimus.procesos.config.."), resideInAPackage("..repository.."))
                .ignoreDependency(resideInAPackage("com.facimus.procesos.security.."), resideInAPackage("..service.."))
                .check(clases);
    }

    @Test
    @DisplayName("Los controllers no exponen entidades JPA ni usan ModelMapper")
    void las_entidades_no_salen_por_la_api() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.modelmapper.ModelMapper")
                .because("el controller solo recibe y devuelve DTOs; el mapeo ocurre en el service")
                .check(clases);
    }

    @Test
    @DisplayName("Los @Service son clases <Nombre>ServiceImpl que implementan la interfaz <Nombre>Service")
    void los_servicios_son_interfaz_mas_impl() {
        classes().that().areAnnotatedWith(Service.class)
                .should().haveSimpleNameEndingWith("ServiceImpl")
                .andShould(implementarLaInterfazSinImpl())
                .because("el service se escribe como interfaz + Impl, la capa que mas facil cambia de implementacion")
                .check(clases);
    }

    @Test
    @DisplayName("Las interfaces del paquete service se llaman *Service")
    void las_interfaces_de_service_terminan_en_service() {
        classes().that().resideInAPackage("..service").and().areInterfaces()
                .should().haveSimpleNameEndingWith("Service")
                .check(clases);
    }

    @Test
    @DisplayName("Los @RestController se llaman *Controller")
    void los_controllers_terminan_en_controller() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .check(clases);
    }

    @Test
    @DisplayName("Los Request y Response son records")
    void los_dto_son_records() {
        classes().that().resideInAPackage("..controller.dto..")
                .and().haveSimpleNameEndingWith("Request").or().haveSimpleNameEndingWith("Response")
                .should().beRecords()
                .check(clases);
    }

    @Test
    @DisplayName("Las transacciones las maneja el service, no el controller")
    void transactional_no_va_en_controllers() {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .should().notBeAnnotatedWith(Transactional.class)
                .check(clases);
        classes().that().resideInAPackage("..controller..")
                .should().notBeAnnotatedWith(Transactional.class)
                .check(clases);
    }

    @Test
    @DisplayName("El service no depende de las anotaciones web ni de la API de servlets")
    void el_service_no_conoce_la_capa_web() {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web.bind.annotation..",
                        "jakarta.servlet..", "org.springframework.http..")
                .because("la capa de servicio no sabe de HTTP: eso es del controller")
                .check(clases);
    }

    @Test
    @DisplayName("No se usa System.out ni printStackTrace: se usa Logger")
    void sin_salida_estandar() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(clases);
    }

    @Test
    @DisplayName("No se inyecta por campo: se usa constructor")
    void sin_inyeccion_por_campo() {
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(clases);
    }

    private static ArchCondition<JavaClass> implementarLaInterfazSinImpl() {
        return new ArchCondition<>("implementar la interfaz con el mismo nombre sin el sufijo Impl") {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                String esperada = clase.getName().substring(0, clase.getName().length() - "Impl".length());
                boolean implementa = clase.getAllRawInterfaces().stream()
                        .anyMatch(interfaz -> interfaz.getName().equals(esperada));
                if (!implementa) {
                    eventos.add(SimpleConditionEvent.violated(clase,
                            clase.getName() + " no implementa " + esperada));
                }
            }
        };
    }
}
