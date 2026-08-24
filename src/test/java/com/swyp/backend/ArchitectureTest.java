package com.swyp.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.swyp.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule dependencies_flow_controller_to_service_to_repository = layeredArchitecture()
		.consideringOnlyDependenciesInLayers()
		.layer("Controller").definedBy("..controller..")
		.layer("Service").definedBy("..service..")
		.layer("Repository").definedBy("..repository..")
		.whereLayer("Controller").mayNotBeAccessedByAnyLayer()
		.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
		.whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

	@ArchTest
	static final ArchRule persistence_api_stays_out_of_controller_and_service = noClasses()
		.that().resideInAnyPackage("..controller..", "..service..")
		.should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
		.because("only repository and entity touch the JPA persistence API");

	@ArchTest
	static final ArchRule entities_do_not_cross_the_controller_boundary = noClasses()
		.that().resideInAPackage("..controller..")
		.should().dependOnClassesThat().areAnnotatedWith(Entity.class)
		.because("controllers expose DTOs, not @Entity");
}
