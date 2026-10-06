package com.finance.importer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.finance.importer",
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule applicationIsIndependent =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..infrastructure..",
              "org.springframework..",
              "java.net.http..",
              "com.microsoft.playwright..");

  @ArchTest
  static final ArchRule importerHasNoDatabase =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.jdbc..",
              "com.mysql..",
              "com.finance.server.infrastructure.adapter..");

  @ArchTest
  static final ArchRule adaptersBelongToInfrastructure =
      classes()
          .that()
          .haveSimpleNameEndingWith("Adapter")
          .should()
          .resideInAPackage("..infrastructure.adapter..");

  @ArchTest
  static final ArchRule servicesHaveDedicatedPackage =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .should()
          .resideInAPackage("..application.service..");

  @ArchTest
  static final ArchRule configurationBelongsToInfrastructure =
      classes()
          .that()
          .haveSimpleNameEndingWith("Config")
          .should()
          .resideInAPackage("..infrastructure.config..");

  @ArchTest
  static final ArchRule applicationHasNoTechnologyDependency =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.fasterxml.jackson..",
              "org.apache.poi..",
              "org.apache.commons.csv..",
              "java.sql..",
              "java.net.http..");

  @ArchTest
  static final ArchRule layersHaveNoCycles =
      slices().matching("com.finance.importer.(*)..").should().beFreeOfCycles();
}
