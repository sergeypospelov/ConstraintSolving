import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    `maven-publish`
    application
}

group = "org.utbot.cs"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/releases/org/unittestbot")
}

dependencies {
    testImplementation(kotlin("test"))


    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")

    // ksmt
    implementation("org.ksmt:ksmt-core:0.2.1")
    implementation("org.ksmt:ksmt-z3:0.2.1")

    // soot
    implementation("org.unittestbot.soot:soot-utbot-fork:4.4.0-FORK-2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}

publishing {
    publications {
        create<MavenPublication>("jar") {
            from(components["java"])
            groupId = "org.utbot.cs"
            artifactId = project.name
        }
    }
}