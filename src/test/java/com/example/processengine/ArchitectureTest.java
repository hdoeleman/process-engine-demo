package com.example.processengine;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.example.processengine.engine.ProcessAdvisoryListener;
import com.example.processengine.engine.ProcessDataResetService;
import com.example.processengine.engine.ProcessEngine;
import com.example.processengine.engine.StepHandler;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Architectural rules enforced by ArchUnit -- this module is the only place all three artifacts
 * (process-engine-core, process-engine-admin, process-engine-demo) sit on one classpath, so it's
 * the only place the cross-module rules, and the rules about the admin/demo code themselves, are
 * checkable at all. (Rules internal to core live in core's own {@code ArchitectureTest}.) They fall
 * into four groups:
 *
 * <ol>
 *   <li><b>Module direction</b> -- the single dependency direction already documented in this
 *       module's own {@code package-info.java}: {@code demo -> admin -> core}, and {@code demo ->
 *       core} directly -- never the reverse; and no package cycles across the three artifacts.
 *   <li><b>Persistence access</b> -- no controller in any module, and nothing in admin or demo at
 *       all, touches a repository: they go through the {@code ProcessEngine} facade, which owns the
 *       transaction and the pessimistic instance lock. Admin and demo define no
 *       entities or repositories of their own.
 *   <li><b>Facade discipline</b> -- admin and demo controllers depend on the engine's collaborators
 *       not at all, except the one documented exception: {@code AdminController} talks to {@code
 *       ProcessDataResetService} directly.
 *   <li><b>Extension points</b> -- every {@code StepHandler} is a Spring bean in {@code
 *       demo.handlers}, since a {@code handlerRef} resolves to a bean by name; every
 *       {@code ProcessAdvisoryListener} is a bean, since the engine collects them by auto-wiring a
 *       list.
 * </ol>
 *
 * <p>Each rule fails the build (via {@code mvn verify}) with a clear violation message, naming
 * the offending class and the rule that was broken.
 */
@AnalyzeClasses(packages = "com.example.processengine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String CORE_PACKAGES =
            "com.example.processengine.(definition|runtime|engine|dto|api|config)..";
    private static final String ADMIN_PACKAGE = "com.example.processengine.admin..";
    private static final String DEMO_PACKAGES = "com.example.processengine.demo..";
    private static final String ENGINE_PACKAGE = "com.example.processengine.engine..";

    // ------------------------------------------------------------------ 1. module direction

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
    static final ArchRule packages_are_free_of_cycles_across_all_three_artifacts = slices().matching(
                    "com.example.processengine.(*)..")
            .should()
            .beFreeOfCycles()
            .because("a cycle between any two top-level packages, across core/admin/demo, means the one-way "
                    + "demo -> admin -> core direction above is not actually holding");

    // ------------------------------------------------------------------ 2. persistence access

    @ArchTest
    static final ArchRule controllers_in_every_module_do_not_access_repositories = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(Controller.class)
            .or()
            .areAnnotatedWith(RestControllerAdvice.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("controllers are thin HTTP adapters over the ProcessEngine facade; a repository lookup in "
                    + "one bypasses the facade's transaction and locking discipline");

    @ArchTest
    static final ArchRule admin_and_demo_do_not_access_repositories = noClasses()
            .that()
            .resideInAnyPackage(ADMIN_PACKAGE, DEMO_PACKAGES)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("code built on top of the engine uses the ProcessEngine facade, never the engine's own "
                    + "tables -- the facade owns the transaction and the pessimistic instance lock (ADR-004, "
                    + "ADR-013)");

    @ArchTest
    static final ArchRule admin_and_demo_define_no_entities = noClasses()
            .that()
            .resideInAnyPackage(ADMIN_PACKAGE, DEMO_PACKAGES)
            .should()
            .beAnnotatedWith(Entity.class)
            .because("the engine owns its schema (Liquibase ships with core); an app built on it keeps its own "
                    + "state in process variables or its own datasource, not in the engine's tables");

    @ArchTest
    static final ArchRule admin_and_demo_define_no_repositories = noClasses()
            .that()
            .resideInAnyPackage(ADMIN_PACKAGE, DEMO_PACKAGES)
            .should()
            .beAssignableTo(Repository.class)
            .because("a repository in admin/demo would be a second way into the engine's tables");

    // ------------------------------------------------------------------ 3. facade discipline

    /** An engine-package Spring bean other than the facade. */
    private static final DescribedPredicate<JavaClass> ENGINE_BEAN_OTHER_THAN_THE_FACADE = resideInAPackage(
                    ENGINE_PACKAGE)
            .and(annotatedWith(Service.class).or(annotatedWith(Component.class)))
            .and(not(equivalentTo(ProcessEngine.class)))
            .as("an engine collaborator bean other than ProcessEngine");

    @ArchTest
    static final ArchRule controllers_other_than_the_admin_controller_use_the_engine_through_the_facade_only =
            noClasses()
                    .that()
                    .resideInAnyPackage(ADMIN_PACKAGE, DEMO_PACKAGES)
                    .and()
                    .areAnnotatedWith(RestController.class)
                    .and()
                    .doNotHaveSimpleName("AdminController")
                    .should()
                    .dependOnClassesThat(ENGINE_BEAN_OTHER_THAN_THE_FACADE)
                    .because("every controller depends on ProcessEngine and only ProcessEngine");

    @ArchTest
    static final ArchRule the_admin_controller_uses_only_the_facade_and_the_reset_service = noClasses()
            .that()
            .haveSimpleName("AdminController")
            .should()
            .dependOnClassesThat(
                    ENGINE_BEAN_OTHER_THAN_THE_FACADE.and(not(equivalentTo(ProcessDataResetService.class))))
            .because("AdminController's direct use of ProcessDataResetService is the one documented exception "
                    + "; nothing else in the engine is reachable except through ProcessEngine");

    // ------------------------------------------------------------------ 4. extension points

    @ArchTest
    static final ArchRule step_handlers_are_spring_beans = classes()
            .that()
            .implement(StepHandler.class)
            .and()
            .areNotInterfaces()
            .should()
            .beAnnotatedWith(Component.class)
            .because("a definition's handlerRef resolves to a Spring bean by name, so a StepHandler that is not "
                    + "a bean can never be invoked");

    @ArchTest
    static final ArchRule step_handlers_live_in_the_handlers_package_and_are_named_handler = classes()
            .that()
            .implement(StepHandler.class)
            .and()
            .areNotInterfaces()
            .should()
            .resideInAPackage("com.example.processengine.demo.handlers..")
            .andShould()
            .haveSimpleNameEndingWith("Handler")
            .because("one package and one naming convention for the demo's step implementations, so the "
                    + "handlerRef -> class mapping is findable by name");

    @ArchTest
    static final ArchRule advisory_listeners_are_spring_beans = classes()
            .that()
            .implement(ProcessAdvisoryListener.class)
            .and()
            .areNotInterfaces()
            .should()
            .beAnnotatedWith(Component.class)
            .because("the engine collects every listener by auto-wiring a list of beans -- a listener "
                    + "that is not a bean is silently never called");

    // ------------------------------------------------------------------ coding rules

    @ArchTest
    static final ArchRule production_code_uses_constructor_injection_only =
            NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because(
                    "constructor injection keeps collaborators final and the classes testable without Spring");

    @ArchTest
    static final ArchRule production_code_does_not_print_to_standard_streams =
            NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
                    "logging goes through SLF4J -- System.out / System.err bypass the configured appenders");

    @ArchTest
    static final ArchRule production_code_does_not_use_java_util_logging =
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.because(
                    "logging goes through SLF4J -- java.util.logging would split the log stream");
}
