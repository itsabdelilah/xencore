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
        // JitPack - for XenCore library
        maven { url = uri("https://jitpack.io") }
        // Meta Audience Network
        maven { url = uri("https://maven.facebook.com/maven") }
        // AppLovin
        maven { url = uri("https://artifacts.applovin.com/android") }
    }
}

rootProject.name = "AdsLibrary"
include(":app")
include(":adscore")  // Library module
