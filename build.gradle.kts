plugins {
    id("java-library")
    id("net.neoforged.moddev")
    id("maven-publish")
}

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName = property("mod_id") as String
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

sourceSets.main {
    resources {
        srcDir("src/generated/resources")
        exclude("**/*.bbmodel")
        exclude("src/generated/**/.cache")
    }
}

repositories {
    // Additional repositories if needed
}

neoForge {
    version = property("neo_version") as String

    parchment {
        mappingsVersion = property("parchment_mappings_version") as String
        minecraftVersion = property("parchment_minecraft_version") as String
    }

    runs {
        client {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        server {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        data {
            data()
            programArguments.addAll(
                "--mod", property("mod_id") as String,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(property("mod_id") as String) {
            sourceSet(sourceSets.main)
        }
    }
}

configurations {
    create("localRuntime")
    named("runtimeClasspath").extendsFrom(named("localRuntime").get())
}

dependencies {
    // Add mod dependencies here
}

val generateModMetadata = tasks.register("generateModMetadata", ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to property("minecraft_version"),
        "minecraft_version_range" to property("minecraft_version_range"),
        "neo_version" to property("neo_version"),
        "loader_version_range" to property("loader_version_range"),
        "mod_id" to property("mod_id"),
        "mod_name" to property("mod_name"),
        "mod_license" to property("mod_license"),
        "mod_version" to property("mod_version"),
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}

neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("file://${project.projectDir}/repo")
        }
    }
}
