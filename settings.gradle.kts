pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "SohvaTV"

include(":app")
include(":core:model")
include(":core:data")
include(":ui:design")
include(":feature:home")
include(":benchmark")
include(":spike:guidegrid")
include(":lint-checks")
