// Car Mech, the garage game, is a Gradle build of its own rather than a module of MixMaster's: a
// mistake in it can never hold back the MixMaster that the crew installs every day, and CI builds it
// in a workflow of its own (.github/workflows/build-carmech.yml) only when something in here changes.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CarMech"
