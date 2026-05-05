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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "amanOS"
include(":core")
// Future modules added here as they are built:
// include(":contacts")
// include(":messaging")
// include(":notes")
// include(":tasks")
// include(":device")
// include(":calendar")
// include(":location")
// include(":activity")
// include(":health")
// include(":finance")
// include(":camera")
// include(":voice")
// include(":browser")
// include(":smarthome")
// include(":automation")
// include(":router")
