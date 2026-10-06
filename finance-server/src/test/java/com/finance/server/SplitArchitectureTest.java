package com.finance.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.finance.server",
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class SplitArchitectureTest {
  @ArchTest
  static final ArchRule serverHasNoBrowser =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.microsoft.playwright..", "com.finance.importer..");
}
