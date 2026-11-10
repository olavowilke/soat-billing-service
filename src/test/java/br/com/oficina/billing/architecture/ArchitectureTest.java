package br.com.oficina.billing.architecture;

import br.com.oficina.billing.architecture.fixtures.entities.EntidadeComCamadaExternaFixture;
import br.com.oficina.billing.architecture.fixtures.entities.EntidadeComFrameworkFixture;
import br.com.oficina.billing.architecture.fixtures.gateways.GatewayComFrameworkFixture;
import br.com.oficina.billing.architecture.fixtures.infrastructure.AdapterFixture;
import br.com.oficina.billing.architecture.fixtures.usecases.UsecaseComFrameworkFixture;
import br.com.oficina.billing.architecture.fixtures.usecases.UsecaseFixture;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static ArchRule livreDeFramework(String pacote) {
        return noClasses().that().resideInAPackage(pacote)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true);
    }

    private static ArchRule semCamadasExternas() {
        return noClasses().that().resideInAPackage("..entities..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..usecases..", "..gateways..", "..controllers..",
                        "..presenters..", "..infrastructure..")
                .allowEmptyShould(true);
    }

    private static ArchRule semAdapters(String pacote) {
        return noClasses().that().resideInAPackage(pacote)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..", "..controllers..", "..presenters..")
                .allowEmptyShould(true);
    }

    private static JavaClasses fixtures(Class<?>... tipos) {
        return new ClassFileImporter().importClasses(tipos);
    }

    @Test
    void entitiesSaoLivresDeFramework() {
        livreDeFramework("..entities..").check(classes);
    }

    @Test
    void usecasesSaoLivresDeFramework() {
        livreDeFramework("..usecases..").check(classes);
    }

    @Test
    void gatewaysSaoLivresDeFramework() {
        livreDeFramework("..gateways..").check(classes);
    }

    @Test
    void entitiesNaoDependemDeCamadasExternas() {
        semCamadasExternas().check(classes);
    }

    @Test
    void usecasesNaoDependemDeAdapters() {
        semAdapters("..usecases..").check(classes);
    }

    @Test
    void gatewaysNaoDependemDeAdapters() {
        semAdapters("..gateways..").check(classes);
    }

    @Test
    void regraDeFrameworkFalhaParaEntity() {
        assertThatThrownBy(() -> livreDeFramework("..entities..")
                .check(fixtures(EntidadeComFrameworkFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void regraDeFrameworkFalhaParaUsecase() {
        assertThatThrownBy(() -> livreDeFramework("..usecases..")
                .check(fixtures(UsecaseComFrameworkFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void regraDeFrameworkFalhaParaGateway() {
        assertThatThrownBy(() -> livreDeFramework("..gateways..")
                .check(fixtures(GatewayComFrameworkFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void regraDeCamadasExternasFalhaParaEntity() {
        assertThatThrownBy(() -> semCamadasExternas()
                .check(fixtures(EntidadeComCamadaExternaFixture.class, UsecaseFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void regraDeAdaptersFalhaParaUsecase() {
        assertThatThrownBy(() -> semAdapters("..usecases..")
                .check(fixtures(UsecaseComFrameworkFixture.class, AdapterFixture.class)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void regraDeAdaptersFalhaParaGateway() {
        assertThatThrownBy(() -> semAdapters("..gateways..")
                .check(fixtures(GatewayComFrameworkFixture.class, AdapterFixture.class)))
                .isInstanceOf(AssertionError.class);
    }
}
