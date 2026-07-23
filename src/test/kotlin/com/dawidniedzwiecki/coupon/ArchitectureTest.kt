package com.dawidniedzwiecki.coupon

import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/** Enforces the layering the README describes, so a violation fails the build rather than only a review. */
@AnalyzeClasses(packages = ["com.dawidniedzwiecki.coupon"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

	// expect — the REST edge reaches the core only through its published API
	@ArchTest
	val restReachesTheCoreOnlyThroughTheApi =
		noClasses().that().resideInAPackage("..rest..")
			.should().dependOnClassesThat(resideInAPackage("..core..").and(resideOutsideOfPackage("..core.api..")))

	// expect — core.api is a framework-free contract (no Spring, JPA, servlet, or other layer)
	@ArchTest
	val theApiIsAFrameworkFreeContract =
		noClasses().that().resideInAPackage("..core.api..")
			.should().dependOnClassesThat().resideInAnyPackage(
				"..rest..",
				"..config..",
				"..core.domain..",
				"..core.infrastructure..",
				"org.springframework..",
				"jakarta.persistence..",
				"jakarta.servlet..",
			)

	// expect — the core never depends on the web edge or Spring wiring
	@ArchTest
	val theCoreDoesNotDependOnTheWebEdgeOrWiring =
		noClasses().that().resideInAPackage("..core..")
			.should().dependOnClassesThat().resideInAnyPackage("..rest..", "..config..")

	// expect — top-level packages have no dependency cycles
	@ArchTest
	val topLevelPackagesAreFreeOfCycles =
		slices().matching("com.dawidniedzwiecki.coupon.(*)..").should().beFreeOfCycles()
}
