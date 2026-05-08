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

rootProject.name = "ZUtils-android"
// 应用与共享壳：与 `app` 同级，见 ZUtils-android/APPLICATION_MODULES.md
include(":application-shell")
include(":office-app")
include(":app")
include(":engine-core")
include(":permissions")
include(":mcp-manager")
include(":ui-automation")
include(":plugin-manager")
include(":llm-manager")
// include(":functions:qrcode")  // migrated to server MCP tool: qrcode_generate
// include(":functions:calculate")  // migrated to DEX plugin: calculator
//include(":functions:uuid")
//include(":functions:time")
//include(":functions:weather")
