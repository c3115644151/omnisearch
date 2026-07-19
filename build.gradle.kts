plugins {
    id("java-library")
    id("maven-publish")
}

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName = property("mod_id") as String
}

sourceSets.main {
    resources {
        srcDir("src/generated/resources")
        exclude("**/*.bbmodel")
        exclude("src/generated/**/.cache")
    }
}

repositories {
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.2")
    implementation("org.jsoup:jsoup:1.19.1")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jsoup:jsoup:1.19.1")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
}

// Make Minecraft classes available to test source set for Mockito-based rendering tests
sourceSets {
    test {
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}

val ver = stonecutter.current.version

val generateModMetadata = tasks.register("generateModMetadata", Sync::class) {
    val replaceProperties = buildMap {
        put("minecraft_version", project.property("minecraft_version"))
        put("minecraft_version_range", project.property("minecraft_version_range"))
        put("loader_version_range", project.property("loader_version_range"))
        put("mod_id", project.property("mod_id"))
        put("mod_name", project.property("mod_name"))
        put("mod_license", project.property("mod_license"))
        put("mod_version", project.property("mod_version"))
        if (ver == "1.20.1") {
            put("forge_version", project.property("forge_version"))
        } else {
            put("neo_version", project.property("neo_version"))
        }
    }
    inputs.properties(replaceProperties)
    if (ver == "1.20.1") {
        from("versions/1.20.1/templates")
    } else {
        from("src/main/templates")
    }
    into(layout.buildDirectory.dir("generated/resources/modMetadata"))
    expand(replaceProperties)
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/resources/modMetadata"))
}

tasks.named("processResources") {
    dependsOn(generateModMetadata)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("repo")
        }
    }
}

// Load version-specific plugin, Java toolchain, and NeoForge/LegacyForge config
// MUST be last — requires generateModMetadata task to be registered first
apply(from = rootProject.file("versions/${ver}/build.gradle"))
