import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.KotlinClosure2

plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
	id("io.gitlab.arturbosch.detekt") version "1.23.8"
	jacoco
}

group = "com.dawidniedzwiecki"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	// Embedded IP→country lookups from a local .mmdb (no per-request network call/quota), behind the GeoIpResolver port.
	implementation("com.maxmind.geoip2:geoip2:5.2.0")
	// Serves the OpenAPI document and Swagger UI generated from the controllers and DTOs.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

detekt {
	buildUponDefaultConfig = true
	config.setFrom(files("config/detekt/detekt.yml"))
}

// detekt 1.23 ships compiled against Kotlin 2.0.21 and refuses to run on the project's 2.3.21; pin
// its own classpath to the version it was built with (it still analyses the 2.3 sources fine).
configurations.matching { it.name.startsWith("detekt") }.configureEach {
	resolutionStrategy.eachDependency {
		if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("failed")
		exceptionFormat = TestExceptionFormat.FULL
	}
	// One compact line per test, with its duration.
	afterTest(
		KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
			val cls = desc.className?.substringAfterLast('.')
			println("  ${result.resultType} $cls > ${desc.displayName} (${result.endTime - result.startTime} ms)")
		}),
	)
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
	// Coverage reflects tested behaviour, not framework bootstrap or JPA entity data-holders.
	classDirectories.setFrom(
		files(classDirectories.files.map {
			fileTree(it) {
				exclude(
					"**/CouponApplication*",
					"**/CouponEntity*",
					"**/CouponRedemptionEntity*",
					"**/CouponRedemptionId*",
					"**/config/**",
					"**/GeoIpProperties*",
					"**/rest/dto/**",
				)
			}
		}),
	)
}
