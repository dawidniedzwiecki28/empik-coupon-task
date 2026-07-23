package com.dawidniedzwiecki.coupon

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/** Enforces the layering the README describes, so a violation fails the build rather than only a review. */
@AnalyzeClasses(packages = ["com.dawidniedzwiecki.coupon"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

	@ArchTest
	val restReachesTheCoreOnlyThroughTheApi =
		noClasses().that().resideInAPackage("..rest..")
			.should().dependOnClassesThat().resideInAnyPackage("..core.domain..", "..core.infrastructure..")

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
			)

	@ArchTest
	val theCoreDoesNotDependOnTheWebEdgeOrWiring =
		noClasses().that().resideInAPackage("..core..")
			.should().dependOnClassesThat().resideInAnyPackage("..rest..", "..config..")

	@ArchTest
	val topLevelPackagesAreFreeOfCycles =
		slices().matching("com.dawidniedzwiecki.coupon.(*)..").should().beFreeOfCycles()
}
