import java.security.MessageDigest

plugins {
    `java-library`
    `maven-publish`
    signing
    kotlin("jvm") version "2.3.0"
}

group = "com.swadedev.micup4j"
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

tasks.test {
    useJUnitPlatform()
    // -PupdateApi accepts an intended public API change by rewriting api/Micup4J.api; see ApiSurfaceTest.
    systemProperty("api.update", providers.gradleProperty("updateApi").isPresent.toString())
}
tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        // Every supported type and member must be documented; internals are exempt.
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Xdoclint/package:-com.swadedev.micup4j.internal", true)
        addStringOption("Xmaxwarns", "10000")
        addBooleanOption("Werror", true)
    }
}
tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("micup4j.properties") { expand("version" to project.version.toString()) }
}
// Ship the license inside every JAR, as Maven Central consumers expect.
tasks.withType<Jar>().configureEach { from(rootProject.file("LICENSE")) { into("META-INF") } }
tasks.jar {
    manifest.attributes("Implementation-Title" to "Micup4J", "Implementation-Version" to project.version.toString())
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
// Release metadata. Micup4J is licensed under the Apache License 2.0 (see LICENSE).
val pomLicenseName = "The Apache License, Version 2.0"
val pomLicenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
publishing {
    publications {
        create<MavenPublication>("micup4j") {
            from(components["java"])
            pom {
                name.set("Micup4J")
                description.set("An asynchronous Java library for Micup bots")
                url.set("https://github.com/guneyilmaz0/Micup4J")
                scm {
                    url.set("https://github.com/guneyilmaz0/Micup4J")
                    connection.set("scm:git:https://github.com/guneyilmaz0/Micup4J.git")
                    developerConnection.set("scm:git:ssh://git@github.com/guneyilmaz0/Micup4J.git")
                }
                developers { developer { id.set("guneyilmaz0"); url.set("https://github.com/guneyilmaz0") } }
                issueManagement { system.set("GitHub"); url.set("https://github.com/guneyilmaz0/Micup4J/issues") }
                licenses {
                    license { name.set(pomLicenseName); url.set(pomLicenseUrl); distribution.set("repo") }
                }
            }
        }
    }
    // A local Maven layout that the Central Portal bundle is built from; nothing is uploaded by Gradle.
    repositories { maven { name = "staging"; url = uri(layout.buildDirectory.dir("staging-deploy")) } }
}

// Signs only when an ASCII-armored key is supplied, for example through ORG_GRADLE_PROJECT_signingKey in CI.
signing {
    val key = providers.gradleProperty("signingKey").orNull
    if (key != null) {
        useInMemoryPgpKeys(key, providers.gradleProperty("signingPassword").orNull)
        sign(publishing.publications["micup4j"])
    }
}

val releaseArtifacts = listOf(tasks.jar, tasks.named<Jar>("sourcesJar"), tasks.named<Jar>("javadocJar"))
tasks.register("releaseChecksums") {
    group = "release"
    description = "Writes SHA-256 files next to the release JARs"
    dependsOn(releaseArtifacts)
    doLast {
        releaseArtifacts.map { it.get().archiveFile.get().asFile }.forEach { file ->
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            file.resolveSibling(file.name + ".sha256").writeText(digest.joinToString("") { "%02x".format(it) } + "  " + file.name + "\n")
        }
    }
}

tasks.register<Zip>("centralBundle") {
    group = "release"
    description = "Builds the Maven Central Portal upload bundle from the staging repository"
    dependsOn("publishAllPublicationsToStagingRepository")
    from(layout.buildDirectory.dir("staging-deploy"))
    exclude("**/maven-metadata*")
    archiveFileName.set("central-bundle-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

tasks.register("verifyRelease") {
    group = "release"
    description = "Checks release prerequisites: tests, API snapshot, docs, license metadata and checksums"
    dependsOn("check", "javadoc", "releaseChecksums")
    val version = project.version.toString()
    val changelog = file("CHANGELOG.md")
    val notes = file("docs/releases/README-$version.md")
    val license = file("LICENSE")
    doLast {
        val problems = mutableListOf<String>()
        if (version.endsWith("SNAPSHOT")) problems += "Version $version is a snapshot"
        if (!changelog.readText().contains("## [$version]")) problems += "CHANGELOG.md has no [$version] section"
        if (!notes.exists()) problems += "Missing release notes ${notes.path}"
        if (!license.exists()) problems += "Missing LICENSE file"
        check(problems.isEmpty()) { "Release is not ready:\n - " + problems.joinToString("\n - ") }
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
    mainClass.set("com.swadedev.micup4j.examples.PingBot")
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

// Repeatable local load harness against an in-process mock Micup instance; see docs/PERFORMANCE.md.
val load = sourceSets.create("load") {
    java.srcDir("src/load/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[load.implementationConfigurationName].extendsFrom(configurations.implementation.get())
dependencies { "loadImplementation"("com.squareup.okhttp3:mockwebserver:4.12.0") }
tasks.register<JavaExec>("loadTest") {
    group = "verification"
    description = "Runs the load harness; tune with -Pload.events, -Pload.pacedRate, -Pload.guilds, -Pload.requests, -Pload.shards, -Pload.soakSeconds"
    classpath = load.runtimeClasspath
    mainClass.set("com.swadedev.micup4j.load.LoadHarness")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("-Xms512m", "-Xmx512m", "-XX:+UseG1GC")
    val output = layout.buildDirectory.file("reports/load/results.json")
    outputs.file(output)
    outputs.upToDateWhen { false }
    listOf("events", "pacedRate", "guilds", "requests", "shards", "soakSeconds").forEach { key ->
        providers.gradleProperty("load.$key").orNull?.let { systemProperty("load.$key", it) }
    }
    systemProperty("load.output", output.get().asFile.absolutePath)
}
tasks.named("check") { dependsOn(tasks.named(load.classesTaskName)) }
