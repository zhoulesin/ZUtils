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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZUtils"
include(":app")
include(":engine-core")
include(":plugin-manager")
include(":llm-manager")
// include(":functions:qrcode")  // migrated to server MCP tool: qrcode_generate
include(":functions:calculate")
include(":functions:uuid")
include(":functions:time")
include(":functions:weather")
