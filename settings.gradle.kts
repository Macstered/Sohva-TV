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
include(":ui:design")
include(":feature:home")
include(":feature:settings")
include(":feature:live")
include(":benchmark")
include(":spike:guidegrid")
include(":lint-checks")
