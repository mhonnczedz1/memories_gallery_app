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
    }
}

rootProject.name = "memories"

include(
    ":core",
    ":data",
    ":ml",
    ":engine",
    ":widget",
    ":vault",
    ":app",
    ":models_pack",
    ":testing",
    ":microbenchmark",
    ":benchmark"
)
