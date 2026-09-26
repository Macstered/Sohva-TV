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
include(":core:net")
include(":core:data")
include(":core:sync")
include(":core:player")
include(":ui:design")
include(":feature:home")
include(":feature:settings")
include(":feature:live")
include(":feature:player")
include(":feature:channels")
include(":feature:library")
include(":feature:organize")
include(":feature:search")
include(":feature:profiles")
include(":benchmark")
include(":spike:guidegrid")
include(":measure:fixture")
include(":lint-checks")
