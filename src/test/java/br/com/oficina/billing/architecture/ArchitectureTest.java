package br.com.oficina.billing.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regra de dependencia da Clean Architecture para o Billing Service. */
class ArchitectureTest {

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "io.awspring..", "software.amazon.awssdk..",
            "jakarta.persistence..", "org.hibernate..", "com.fasterxml.jackson.."};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.oficina.billing");
    }

    @Test
    void entitiesSaoLivresDeFramework() {
        noClasses().that().resideInAPackage("..entities..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void usecasesSaoLivresDeFramework() {
        noClasses().that().resideInAPackage("..usecases..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void gatewaysSaoLivresDeFramework() {
        noClasses().that().resideInAPackage("..gateways..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void entitiesNaoDependemDeCamadasExternas() {
        noClasses().that().resideInAPackage("..entities..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..usecases..", "..gateways..", "..controllers..",
                        "..presenters..", "..infrastructure..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void usecasesNaoDependemDeAdapters() {
        noClasses().that().resideInAPackage("..usecases..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..", "..controllers..", "..presenters..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void gatewaysNaoDependemDeAdapters() {
        noClasses().that().resideInAPackage("..gateways..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..", "..controllers..", "..presenters..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
