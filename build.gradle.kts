/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("me.champeau.jmh") version "0.6.7"
    id("org.jetbrains.kotlinx.kover") version "0.6.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    mavenLocal()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:2.6.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.20")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("junit:junit:4.13.1")
    jmh("org.jetbrains.kotlin:kotlin-reflect:1.7.10")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.compileKotlin {
    compilerOptions {
        freeCompilerArgs.addAll(listOf("-no-reflect"))
    }
}

kotlin {
    explicitApi()
    jvmToolchain(8)
}

kover {
    verify {
        rule {
            bound {
                minValue = 75
            }
        }
    }
}

val deployVersion = properties.get("DeployVersion")?.toString() ?: version.toString()

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "org.jetbrains.kotlinx"
            artifactId = "kotlinx.reflect.lite"
            version = deployVersion
            configureMavenCentralMetadata()
            mavenCentralArtifacts(project, project.sourceSets.main.get().allSource)
        }

        version = deployVersion
        mavenRepositoryPublishing(project)
        configureMavenCentralMetadata()
    }

    publications.withType(MavenPublication::class).all {
        signPublicationIfKeyPresent(this)
    }
}
