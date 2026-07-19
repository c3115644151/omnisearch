pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
    plugins {
        id("net.neoforged.moddev") version "2.0.141"
        id("net.neoforged.moddev.legacyforge") version "2.0.141"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.5"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        version("1.21.1", "1.21.1")
        version("1.20.1", "1.20.1")
    }
    create(rootProject)
}

rootProject.name = "omnisearch"
