// settings.gradle.kts (Kotlin DSL) is preferred, but this is the Groovy equivalent.

pluginManagement {
    repositories {
        // Use the 'id' method for the standard plugin portal
        gradlePluginPortal()

        // Use the 'google()' shorthand for Google's Maven repository
        google()

        // Use the 'mavenCentral()' shorthand for Maven Central
        mavenCentral()

        // The 'content' block is useful for restricting repositories, 
        // but often the implicit 'google()' and 'gradlePluginPortal()' 
        // cover most plugin needs, making the explicit 'content' redundant 
        // unless you are aggressively enforcing security. 
        // I've commented out the redundant content block for simplicity 
        // but kept the repositories.
        /*
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        */
    }
}
dependencyResolutionManagement {
    // This setting is correct and highly recommended for security and build stability.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Go Shop"
include(":app")