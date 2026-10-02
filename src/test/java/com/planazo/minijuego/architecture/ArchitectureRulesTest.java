package com.planazo.minijuego.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Hace cumplir, automáticamente en cada {@code mvn verify}, el principio
 * SOA de bajo acoplamiento descrito en {@code ARQUITECTURA.md}: nadie
 * dentro de {@code domain} puede importar Spring, JPA, Redis ni nada de
 * {@code infrastructure}, y {@code application} tampoco puede importar
 * {@code infrastructure}. Si alguien rompe esto sin querer (un import
 * automático del IDE, por ejemplo), el build falla aquí, no en un code
 * review dos semanas después.
 */
class ArchitectureRulesTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importarClases() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.planazo.minijuego");
    }

    @Test
    void elDominioNoDependeDeInfraestructura() {
        ArchRule regla = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
        regla.check(clases);
    }

    @Test
    void elDominioNoDependeDeLaCapaDeAplicacion() {
        ArchRule regla = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..application..");
        regla.check(clases);
    }

    @Test
    void elDominioNoDependeDeSpringNiDeFrameworksExternos() {
        ArchRule regla = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "org.springframework.data.redis..",
                        "org.springframework.amqp.."
                );
        regla.check(clases);
    }

    @Test
    void laCapaDeAplicacionNoDependeDeInfraestructura() {
        ArchRule regla = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
        regla.check(clases);
    }

    @Test
    void losPuertosSonInterfaces() {
        ArchRule regla = classes().that().resideInAnyPackage("..domain.port.in..", "..domain.port.out..")
                .should().beInterfaces();
        regla.check(clases);
    }

    @Test
    void soloInfraestructuraConoceSpringWeb() {
        ArchRule regla = noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.web..");
        regla.check(clases);
    }
}
