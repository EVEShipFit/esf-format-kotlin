import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "fit.eveship"
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-SNAPSHOT")

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
        freeCompilerArgs.add("-Xjdk-release=17")
        allWarningsAsErrors = true
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val esfCatalog = layout.projectDirectory.file("esf-format/tests/catalog.json")

tasks.test {
    useJUnitPlatform()
    environment("ESF_CATALOG", providers.environmentVariable("ESF_CATALOG").getOrElse(esfCatalog.asFile.path))
}

val program by tasks.registering {
    description = "Writes build/program, the command the esf-format test runner calls."
    val classpath = sourceSets.test.get().runtimeClasspath
    val script = layout.buildDirectory.file("program")
    inputs.files(classpath)
    outputs.file(script)
    doLast {
        val file = script.get().asFile
        file.writeText("#!/bin/sh\nexec java -cp '${classpath.asPath}' fit.eveship.esf.ProgramKt \"$@\"\n")
        file.setExecutable(true)
    }
}

kover {
    reports {
        verify {
            rule {
                minBound(100)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates(group.toString(), "esf-format", version.toString())
    pom {
        name = "esf-format"
        description = "Read and write esf/1, the plain-text format for EVE Online ship fits"
        url = "https://github.com/EVEShipFit/esf-format-kotlin"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/license/mit"
            }
        }
        developers {
            developer {
                name = "EVEShipFit Team"
                url = "https://github.com/EVEShipFit"
            }
        }
        scm {
            url = "https://github.com/EVEShipFit/esf-format-kotlin"
        }
    }
}

ktlint {
    version = "1.8.0"
}
