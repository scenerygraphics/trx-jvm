import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "1.7.10"
    `maven-publish`
    application
    java
}

group = "graphics.scenery"
version = if(project.hasProperty("jitpack")) {
    val commit = getGitCommit()
    println("jitpack.io-like build required, will identify as $group:$name:$commit")
    commit
} else {
    "1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
    implementation("org.joml:joml:1.10.4")
    implementation("net.imglib2:imglib2:5.13.0")

    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
}

tasks.test {
    useJUnitPlatform()
    options {
        val props = System.getProperties()
        val map = props.map { it.key as String to it.value }.toMap()
        systemProperties(map)
        maxHeapSize = "16g"
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}


fun getGitCommit(): String {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
    /* 'g' doesn't belong to the commit id and stands for 'git'
       v0.1.9-1-g3a259e0 -> v0.1.9-1-3a259e0
       if you like this to be removed then */
    //.replace("-g", "-")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "graphics.scenery"
            artifactId = rootProject.name
            version = if(project.hasProperty("jitpack")) {
                val commit = getGitCommit()
                println("jitpack.io-like build required, will identify as $groupId:$artifactId:$commit")
                commit
            } else {
                rootProject.version.toString()
            }

            from(components["java"])

            pom {
                name.set(rootProject.name)
                description.set(rootProject.description)
            }
        }
    }
}