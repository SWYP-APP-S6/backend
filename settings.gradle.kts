plugins {
	// Lets Gradle auto-download the Java 25 toolchain when no matching JDK is installed
	// locally (foojay Disco API). Without it, IntelliJ/Gradle fails with
	// "Cannot find a Java installation matching languageVersion=25".
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
