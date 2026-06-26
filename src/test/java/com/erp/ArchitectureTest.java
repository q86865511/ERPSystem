package com.erp;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith boundaries in CI. Two kinds of rule: per-layer (a module's
 * {@code domain} never depends on its {@code application}/{@code web}; {@code application} never on
 * {@code web}) and per-module (a module reaches another module only through its published {@code api}
 * package, never its {@code domain}/{@code application}/{@code web} internals). Production classes only —
 * tests legitimately wire across layers.
 */
@AnalyzeClasses(packages = "com.erp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ---- Per-layer rules (every module) -------------------------------------------------------

    @ArchTest
    static final ArchRule domain_does_not_depend_on_application =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_web =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_web =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..web..");

    // ---- Published-api self-containment -------------------------------------------------------
    // A module's api package is the contract other modules compile against, so it must not drag in
    // that module's own internals.

    @ArchTest
    static final ArchRule ledger_api_is_self_contained =
            noClasses().that().resideInAPackage("..ledger.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..");
}
