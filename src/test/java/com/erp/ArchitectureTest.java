package com.erp;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith boundaries in CI. As more modules land, add per-module rules that no
 * module reaches into another module's internals (only its published {@code api} package).
 */
@AnalyzeClasses(packages = "com.erp")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_does_not_depend_on_application =
            noClasses().that().resideInAPackage("..ledger.domain..")
                    .should().dependOnClassesThat().resideInAPackage("..ledger.application..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_web =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_web =
            noClasses().that().resideInAPackage("..ledger.application..")
                    .should().dependOnClassesThat().resideInAPackage("..ledger.web..");
}
