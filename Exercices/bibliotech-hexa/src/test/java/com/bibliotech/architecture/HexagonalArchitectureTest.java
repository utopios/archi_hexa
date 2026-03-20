package com.bibliotech.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Tests architecturaux avec ArchUnit.
 *
 * Vérifie que les règles de l'architecture hexagonale sont respectées
 * au niveau du bytecode — impossible de tricher.
 *
 * Si un développeur ajoute par erreur une dépendance JPA dans le domaine,
 * ce test échoue immédiatement avec un message clair.
 */
@DisplayName("Architecture hexagonale — règles structurelles (ArchUnit)")
class HexagonalArchitectureTest {

    private static JavaClasses productionClasses;

    private static final String BASE_PACKAGE = "com.bibliotech";
    private static final String DOMAIN_PACKAGE = BASE_PACKAGE + ".domain..";
    private static final String APPLICATION_PACKAGE = BASE_PACKAGE + ".application..";
    private static final String INFRASTRUCTURE_PACKAGE = BASE_PACKAGE + ".infrastructure..";

    private static final String LAYER_DOMAIN = "Domain";
    private static final String LAYER_APPLICATION = "Application";
    private static final String LAYER_INFRASTRUCTURE = "Infrastructure";

    @BeforeAll
    static void importClasses() {
        // DO_NOT_INCLUDE_TESTS exclut les classes compilées dans target/test-classes
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    // -------------------------------------------------------------------------
    // Isolation du domaine
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Le domaine ne doit dépendre de rien d'externe")
    class DomainIsolationTests {

        @Test
        @DisplayName("le domaine ne dépend pas de l'infrastructure")
        void domainShouldNotDependOnInfrastructure() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage(INFRASTRUCTURE_PACKAGE)
                    .because("Le domaine est le cœur du système — " +
                             "il ne doit rien savoir de la technique (JPA, HTTP, email...)");

            rule.check(productionClasses);
        }

        @Test
        @DisplayName("le domaine ne dépend pas de Spring")
        void domainShouldNotDependOnSpring() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework..")
                    .because("Le domaine doit être testable sans démarrer Spring — " +
                             "aucune annotation @Service, @Component, @Autowired dans le domaine");

            rule.check(productionClasses);
        }

        @Test
        @DisplayName("le domaine ne dépend pas de JPA (jakarta.persistence)")
        void domainShouldNotDependOnJpa() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage("jakarta.persistence..")
                    .because("Les entités du domaine ne sont pas des entités JPA — " +
                             "pas d'@Entity, @Column, @Id dans le domaine");

            rule.check(productionClasses);
        }

        @Test
        @DisplayName("le domaine ne dépend pas de la couche application")
        void domainShouldNotDependOnApplication() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage(APPLICATION_PACKAGE)
                    .because("Le domaine est la couche la plus interne — " +
                             "il n'a pas de dépendance vers les couches externes");

            rule.check(productionClasses);
        }
    }

    // -------------------------------------------------------------------------
    // Isolation de la couche application
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("La couche application ne doit pas dépendre de l'infrastructure")
    class ApplicationIsolationTests {

        @Test
        @DisplayName("la couche application ne dépend pas de l'infrastructure")
        void applicationShouldNotDependOnInfrastructure() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(APPLICATION_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage(INFRASTRUCTURE_PACKAGE)
                    .because("La couche application orchestre le domaine via des interfaces (ports) — " +
                             "elle ne connaît pas les adapteurs concrets");

            rule.check(productionClasses);
        }

        @Test
        @DisplayName("la couche application ne dépend pas de JPA")
        void applicationShouldNotDependOnJpa() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(APPLICATION_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage("jakarta.persistence..")
                    .because("Les use cases sont des classes Java pures — " +
                             "pas de dépendance technique dans la couche application");

            rule.check(productionClasses);
        }
    }

    // -------------------------------------------------------------------------
    // Règles sur l'infrastructure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("L'infrastructure peut dépendre du domaine et de l'application")
    class InfrastructureDependencyTests {

        @Test
        @DisplayName("le domaine ne dépend pas de l'infrastructure (vérification symétrique)")
        void domainShouldNotDependOnInfrastructure() {
            // L'infrastructure DOIT connaître le domaine pour implémenter les ports,
            // mais l'inverse est interdit.
            ArchRule rule = noClasses()
                    .that().resideInAPackage(DOMAIN_PACKAGE)
                    .should().dependOnClassesThat()
                    .resideInAPackage(INFRASTRUCTURE_PACKAGE);

            rule.check(productionClasses);
        }
    }

    // -------------------------------------------------------------------------
    // Architecture en couches — règle globale
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Architecture en couches stricte")
    class LayeredArchitectureTests {

        @Test
        @DisplayName("les dépendances respectent la direction domaine ← application ← infrastructure")
        void shouldRespectLayeredArchitecture() {
            // consideringOnlyDependenciesInLayers() : on ne vérifie que les dépendances
            // entre les layers déclarées (on ignore java.lang, Spring, JUnit, etc.)
            ArchRule rule = layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer(LAYER_DOMAIN).definedBy(DOMAIN_PACKAGE)
                    .layer(LAYER_APPLICATION).definedBy(APPLICATION_PACKAGE)
                    .layer(LAYER_INFRASTRUCTURE).definedBy(INFRASTRUCTURE_PACKAGE)

                    .whereLayer(LAYER_DOMAIN).mayNotAccessAnyLayer()
                    .whereLayer(LAYER_APPLICATION).mayOnlyAccessLayers(LAYER_DOMAIN)
                    .whereLayer(LAYER_INFRASTRUCTURE).mayOnlyAccessLayers(LAYER_APPLICATION, LAYER_DOMAIN);

            rule.check(productionClasses);
        }
    }
}
