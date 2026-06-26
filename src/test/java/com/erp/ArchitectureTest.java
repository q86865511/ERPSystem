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

    @ArchTest
    static final ArchRule masterdata_api_is_self_contained =
            noClasses().that().resideInAPackage("..masterdata.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..masterdata.domain..", "..masterdata.application..",
                            "..masterdata.web..");

    @ArchTest
    static final ArchRule inventory_api_is_self_contained =
            noClasses().that().resideInAPackage("..inventory.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory.domain..", "..inventory.application..",
                            "..inventory.web..");

    @ArchTest
    static final ArchRule purchasing_api_is_self_contained =
            noClasses().that().resideInAPackage("..purchasing.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..purchasing.domain..", "..purchasing.application..",
                            "..purchasing.web..");

    @ArchTest
    static final ArchRule sales_api_is_self_contained =
            noClasses().that().resideInAPackage("..sales.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..sales.domain..", "..sales.application..", "..sales.web..");

    @ArchTest
    static final ArchRule manufacturing_api_is_self_contained =
            noClasses().that().resideInAPackage("..manufacturing.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..manufacturing.domain..", "..manufacturing.application..",
                            "..manufacturing.web..")
                    // manufacturing has no published api yet; tolerate it being empty.
                    .allowEmptyShould(true);

    // ---- Cross-module isolation ---------------------------------------------------------------
    // Each module reaches another only through its published api package, never its internals.

    @ArchTest
    static final ArchRule masterdata_does_not_depend_on_other_modules =
            noClasses().that().resideInAPackage("..masterdata..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger..", "..inventory..", "..purchasing..", "..payments..",
                            "..sales..", "..manufacturing..", "..reporting..");

    @ArchTest
    static final ArchRule ledger_does_not_depend_on_other_modules =
            noClasses().that().resideInAPackage("..ledger..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..masterdata..", "..inventory..", "..purchasing..", "..payments..",
                            "..sales..", "..manufacturing..", "..reporting..");

    @ArchTest
    static final ArchRule inventory_uses_only_published_ports =
            noClasses().that().resideInAPackage("..inventory..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..",
                            "..masterdata.domain..", "..masterdata.application..", "..masterdata.web..",
                            "..purchasing..", "..payments..", "..sales..", "..manufacturing..",
                            "..reporting..");

    @ArchTest
    static final ArchRule purchasing_uses_only_published_ports =
            noClasses().that().resideInAPackage("..purchasing..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..",
                            "..inventory.domain..", "..inventory.application..", "..inventory.web..",
                            "..masterdata.domain..", "..masterdata.application..", "..masterdata.web..",
                            "..payments..", "..sales..", "..manufacturing..", "..reporting..");

    @ArchTest
    static final ArchRule sales_uses_only_published_ports =
            noClasses().that().resideInAPackage("..sales..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..",
                            "..inventory.domain..", "..inventory.application..", "..inventory.web..",
                            "..masterdata.domain..", "..masterdata.application..", "..masterdata.web..",
                            "..purchasing..", "..payments..", "..manufacturing..", "..reporting..");

    @ArchTest
    static final ArchRule manufacturing_uses_only_published_ports =
            noClasses().that().resideInAPackage("..manufacturing..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..",
                            "..inventory.domain..", "..inventory.application..", "..inventory.web..",
                            "..masterdata.domain..", "..masterdata.application..", "..masterdata.web..",
                            "..purchasing..", "..payments..", "..sales..", "..reporting..");

    // The reporting module is a read-side leaf: it composes other modules' published api ports only.
    @ArchTest
    static final ArchRule reporting_uses_only_published_ports =
            noClasses().that().resideInAPackage("..reporting..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..",
                            "..inventory.domain..", "..inventory.application..", "..inventory.web..",
                            "..masterdata.domain..", "..masterdata.application..", "..masterdata.web..",
                            "..purchasing.domain..", "..purchasing.application..", "..purchasing.web..",
                            "..payments.domain..", "..payments.application..", "..payments.web..",
                            "..sales.domain..", "..sales.application..", "..sales.web..",
                            "..manufacturing.domain..", "..manufacturing.application..",
                            "..manufacturing.web..");

    @ArchTest
    static final ArchRule payments_uses_only_published_ports =
            noClasses().that().resideInAPackage("..payments..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..ledger.domain..", "..ledger.application..", "..ledger.web..",
                            "..purchasing.domain..", "..purchasing.application..", "..purchasing.web..",
                            "..sales.domain..", "..sales.application..", "..sales.web..",
                            "..inventory..", "..masterdata..", "..manufacturing..", "..reporting..");
}
