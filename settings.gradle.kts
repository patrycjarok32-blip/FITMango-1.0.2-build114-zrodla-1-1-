pluginManagement {
  // Resolve KSP and Kotlin plugins directly from Maven Central. This avoids intermittent failures
  // resolving its plugin marker through the Gradle Plugin Portal.
  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "com.google.devtools.ksp" -> useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
        "org.jetbrains.kotlin.plugin.compose" -> useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
        "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
      }
    }
  }
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

// GitHub Actions provisions JDK 21 via setup-java. The optional Foojay resolver
// is not needed for CI and can block configuration when its plugin portal is unavailable.

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    // Route only the large Robolectric native runtime through the Maven Central mirror.
    exclusiveContent {
      forRepository {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
      }
      filter { includeModule("org.robolectric", "nativeruntime-dist-compat") }
    }
    mavenCentral()
  }
}

rootProject.name = "MangoFit"

include(":app")
