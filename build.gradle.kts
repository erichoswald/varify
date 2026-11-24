import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    val kotlinVersion = "2.4.21"
    val versionsVersion = "0.67.0"

    kotlin("jvm") version kotlinVersion
    `maven-publish`
    id("io.github.ben-manes.versions") version versionsVersion
}

repositories {
    mavenCentral()
}

group = "io.github.erichoswald"

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "Varify"
                description = "Property-based testing for Kotlin that materializes varied values right where tests need them"
                url = "https://github.com/erichoswald/varify"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        id = "erichoswald"
                        name = "Erich Oswald"
                    }
                }
                scm {
                    url = "https://github.com/erichoswald/varify"
                    connection = "scm:git:https://github.com/erichoswald/varify.git"
                }
            }
        }
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

    withType<DependencyUpdatesTask> {
        // Only suggest stable versions, no milestones or release candidates.
        rejectVersionIf { !candidate.version.matches(Regex("[0-9.]+")) }
        gradleReleaseChannel = "current"
    }
}

dependencies {
    val coroutinesVersion = "1.11.0"
    val junitVersion = "6.1.3"
    val striktVersion = "0.35.1"

    // Kotlin
    implementation(kotlin("reflect"))

    // Kotlin Coroutines (only needed by users with suspending test methods)
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")

    // JUnit (API exposed by the extension, engine for tests)
    api(platform("org.junit:junit-bom:$junitVersion"))
    api("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // strikt
    testImplementation("io.strikt:strikt-core:$striktVersion")
}
