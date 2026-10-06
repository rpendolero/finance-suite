package com.finance.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = {"com.finance.server", "com.finance.domain"},
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule persistenceUsesSpringDataRepositories =
      noClasses().that().resideInAPackage("..infrastructure.adapter.out.persistence..")
          .should().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager");

  @ArchTest
  static final ArchRule springDataRepositoriesStayInInfrastructure =
      classes().that().areAssignableTo(org.springframework.data.jpa.repository.JpaRepository.class)
          .should().resideInAPackage("..infrastructure.adapter.out.persistence.repository..");

  @ArchTest
  static final ArchRule domainIsIndependent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..infrastructure..",
              "..application..",
              "org.springframework..",
              "jakarta.persistence..",
              "org.hibernate..",
              "com.microsoft.playwright..");

  @ArchTest
  static final ArchRule applicationDependsOnlyOnPorts =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..infrastructure..", "org.springframework..", "com.microsoft.playwright..");

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
              "jakarta.persistence..",
              "org.hibernate..",
              "java.net.http..");

  @ArchTest
  static final ArchRule layersHaveNoCycles =
      slices().matching("com.finance.server.(*)..").should().beFreeOfCycles();
}
