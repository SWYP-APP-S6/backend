package com.swyp.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.swyp.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	@ArchIgnore(reason = "product, notification and hold have no function yet -- enabled by the pull request that converts them")
	static final ArchRule dependencies_flow_controller_to_service_to_function_to_repository =
		layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Controller").definedBy("..controller..")
			.layer("Service").definedBy("..service..")
			.layer("Function").definedBy("..function..")
			.layer("Repository").definedBy("..repository..")
			.whereLayer("Controller").mayNotBeAccessedByAnyLayer()
			.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
			.whereLayer("Function").mayOnlyBeAccessedByLayers("Service")
			.whereLayer("Repository").mayOnlyBeAccessedByLayers("Function");

	@ArchTest
	@ArchIgnore(reason = "product, notification and hold have no function yet -- enabled by the pull request that converts them")
	static final ArchRule services_of_different_features_do_not_call_each_other =
		slices().matching("com.swyp.backend.(*).service..")
			.should().notDependOnEachOther()
			.because("a feature reaches another feature through its function, not its service -- "
				+ "service-to-service calls are what let cycles form");

	@ArchTest
	static final ArchRule functions_of_different_features_do_not_call_each_other =
		slices().matching("com.swyp.backend.(*).function..")
			.should().notDependOnEachOther()
			.because("functions stay leaves: composing two features is the service's job");

	@ArchTest
	static final ArchRule function_does_not_call_back_into_service = noClasses()
		.that().resideInAPackage("..function..")
		.should().dependOnClassesThat().resideInAPackage("..service..")
		.because("a function that calls a service reopens the cycle it exists to prevent");

	@ArchTest
	@ArchIgnore(reason = "product, notification and hold have no function yet -- enabled by the pull request that converts them")
	static final ArchRule repository_is_reached_only_through_function = noClasses()
		.that().resideInAnyPackage("..controller..", "..service..")
		.should().dependOnClassesThat().resideInAPackage("..repository..")
		.because("service reaches persistence through its function layer");

	@ArchTest
	static final ArchRule persistence_api_stays_above_the_repository = noClasses()
		.that().resideInAnyPackage("..controller..", "..service..", "..function..")
		.should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
		.because("only repository and entity touch the JPA persistence API");

	@ArchTest
	static final ArchRule entities_do_not_cross_the_controller_boundary = noClasses()
		.that().resideInAPackage("..controller..")
		.should().dependOnClassesThat().areAnnotatedWith(Entity.class)
		.because("controllers expose DTOs, not @Entity");

	@ArchTest
	static final ArchRule features_are_free_of_cycles =
		slices().matching("com.swyp.backend.(*).(controller|service|function|repository)..")
			.should().beFreeOfCycles()
			.because("cycles form between behaviour, not between rows -- a @ManyToOne across "
				+ "features is the domain saying two tables are related, and no layering "
				+ "removes it");
}
