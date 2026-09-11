pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()

        maven("https://maven.fabricmc.net/")

        maven("https://maven.kikugie.dev/releases") {
            name = "KikuGie Releases"
        }

        maven("https://maven.kikugie.dev/snapshots") {
            name = "KikuGie Snapshots"
        }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"

    // Automatically selects the correct Fabric Loom variant
    // for old obfuscated versions and Minecraft 26.1+.
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        versions(
            "1.16.5",
            "1.20.1"
        )

        // Internal Stonecutter target name -> actual Minecraft version.
        version("26.2.x", "26.2")

        // Version IDEA will use as the active development target.
        vcsVersion = "26.2.x"
    }
}

rootProject.name = "SeedChecker"