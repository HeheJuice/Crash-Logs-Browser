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
        // ✅ Rikka's Maven repository for Shizuku
        maven { url = uri("https://maven.rikka.dev/repository/maven-public/") }
    }
}
rootProject.name = "Crash-Logs-Browser"
include(":app")