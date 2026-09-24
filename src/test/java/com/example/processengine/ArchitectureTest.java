package com.example.processengine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architectural rules enforced by ArchUnit -- this module is the only place all three artifacts
 * (process-engine-core, process-engine-admin, process-engine-demo) sit on one classpath, so it's
 * the only place these cross-module rules are checkable at all. They restate, as an enforced
 * build gate, the single dependency direction already documented in this module's own {@code
 * package-info.java}: {@code demo -> admin -> core}, and {@code demo -> core} directly -- never
 * the reverse.
 *
 * <p>Each rule fails the build (via {@code mvn verify}) with a clear violation message, naming
 * the offending class and the rule that was broken.
 */
@AnalyzeClasses(
        packages = "com.example.processengine",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String CORE_PACKAGES =
            "com.example.processengine.(definition|runtime|engine|dto|api|config)..";
    private static final String ADMIN_PACKAGE = "com.example.processengine.admin..";
    private static final String DEMO_PACKAGES = "com.example.processengine.demo..";

    @ArchTest
    static final ArchRule core_does_not_depend_on_admin = noClasses()
            .that()
            .resideInAnyPackage(CORE_PACKAGES)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(ADMIN_PACKAGE)
            .because("the engine library must not know about the admin console built on top of it");

    @ArchTest
    static final ArchRule core_does_not_depend_on_demo = noClasses()
            .that()
            .resideInAnyPackage(CORE_PACKAGES)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(DEMO_PACKAGES)
            .because("the engine library must not know about any sample app built on top of it");

    @ArchTest
    static final ArchRule admin_does_not_depend_on_demo = noClasses()
            .that()
            .resideInAnyPackage(ADMIN_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(DEMO_PACKAGES)
            .because(
                    "the generic admin console must stay process-agnostic -- it must not know about the order-fulfillment sample");

    @ArchTest
    static final ArchRule production_code_does_not_print_to_standard_streams =
            NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
                    "logging goes through SLF4J -- System.out / System.err bypass the configured appenders");

    @ArchTest
    static final ArchRule production_code_does_not_use_java_util_logging =
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.because(
                    "logging goes through SLF4J -- java.util.logging would split the log stream");
}
