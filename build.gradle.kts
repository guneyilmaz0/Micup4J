plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.3.0"
}

group = "net.guneyilmaz0.micup4j"
version = providers.gradleProperty("version").get()

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test { useJUnitPlatform() }
tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}
tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("micup4j.properties") { expand("version" to project.version.toString()) }
}
tasks.jar {
    manifest.attributes("Implementation-Title" to "Micup4J", "Implementation-Version" to project.version.toString())
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
publishing {
    publications {
        create<MavenPublication>("micup4j") {
            from(components["java"])
            pom {
                name.set("Micup4J")
                description.set("An asynchronous Java library for Micup bots")
            }
        }
    }
}

val example = sourceSets.create("example") {
    java.srcDir("src/examples/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[example.implementationConfigurationName].extendsFrom(configurations.implementation.get())
tasks.register<JavaExec>("runExample") {
    group = "application"
    description = "Runs the example bot using MICUP_TOKEN from the environment"
    classpath = example.runtimeClasspath
    mainClass.set("net.guneyilmaz0.micup4j.examples.PingBot")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.named("check") { dependsOn(tasks.named(example.classesTaskName)) }

// Compile-only check that JDA-style Kotlin sources still compile against the public API.
val compatibility = sourceSets.create("compatibility") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[compatibility.implementationConfigurationName].extendsFrom(configurations.api.get())
dependencies {
    // The Kotlin stdlib is added only to the compatibility source set; main stays Kotlin-free.
    "compatibilityImplementation"(kotlin("stdlib"))
    "compatibilityImplementation"(kotlin("test-junit5"))
}
kotlin.sourceSets.named("compatibility") { kotlin.srcDir("src/compatibility/kotlin") }
tasks.named("check") { dependsOn(tasks.named("compileCompatibilityKotlin")) }

// Guards the published Java library: no Kotlin artifact may leak into main's classpaths (and thus the POM).
tasks.register("verifyNoKotlinInMain") {
    group = "verification"
    description = "Fails if main's compile or runtime classpath contains an org.jetbrains.kotlin artifact"
    val classpaths = listOf(configurations.compileClasspath, configurations.runtimeClasspath)
    doLast {
        val offenders = classpaths.flatMap { cfg ->
            cfg.get().incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }
                .filter { it.group.startsWith("org.jetbrains.kotlin") }
                .map { "${cfg.name}: ${it.group}:${it.name}:${it.version}" }
        }
        check(offenders.isEmpty()) { "Kotlin leaked into main classpath: $offenders" }
    }
}
tasks.named("check") { dependsOn(tasks.named("verifyNoKotlinInMain")) }
