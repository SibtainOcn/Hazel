pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// No foojay-resolver here, deliberately.
//
// It comes with the Android Studio template, and it resolves a Java toolchain by fetching a
// JDK from the Foojay API partway through the build. Nothing in this project asks for a
// toolchain, so it never did anything but sit there, and F-Droid's build scanner refuses the
// whole build over its presence: a build that downloads its own compiler from a third party
// is not one anybody else can reproduce.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Hazel"
include(":app")
