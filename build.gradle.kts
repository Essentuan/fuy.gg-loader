import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

val wynntils_version: String by project

plugins {
    kotlin("jvm") version "2.0.20"
    id("fabric-loom") version "1.7.1"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)

    withSourcesJar()
}


repositories {
    maven("https://jitpack.io")

    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    //Wynntils
    modCompileOnly(wynntils())

    //ESL
    implementation("org.reactivestreams:reactive-streams:${project.property("rx_streams_version")}")
    implementation("org.reflections:reflections:${project.property("reflections_version")}")
    implementation("org.javassist:javassist:3.29.2-GA") // Required for reflections
    implementation("com.github.sisyphsu:dateparser:${project.property("dateparser_version")}")
    implementation("com.github.essentuan:esl:v${project.property("esl_version")}")

    modImplementation("com.github.essentuan:fuy.gg:v${project.property("fuy_gg_version")}")

    modImplementation(files("libs/acf/acf-fabric-${project.property("acf_fabric_version")}.jar"))
    implementation(files("libs/acf/ACF-${project.property("acf_version")}.jar"))
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
            "kotlin_loader_version" to project.property("kotlin_loader_version"),
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String

            from(components["java"])
        }
    }
}

fun wynntils(): ConfigurableFileCollection {
    val url = URI.create(
        "https://github.com/Wynntils/Artemis/releases/download/v$wynntils_version/wynntils-$wynntils_version-fabric+MC-${project.property("minecraft_version")}.jar"
    ).toURL()

    val name = "wynntils-$wynntils_version"

    val out = projectDir.toPath()
        .resolve("libs")
        .resolve("artemis")
        .resolve("$name.jar")

    if (!out.exists()) {
        out.parent.createDirectories()

        url.openStream().use {
            Files.copy(
                it,
                out,
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        val run = projectDir.toPath()
            .resolve("run")
            .resolve("mods")
            .resolve("wynntils.jar")

        run.parent.createDirectories()

        out.copyTo(
            run,
            overwrite = true
        )
    }

    return files(out)
}