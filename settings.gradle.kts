// Configuración de los repositorios y gestores de plugins para compilar el proyecto.
pluginManagement {
    repositories {
        // Repositorio de Google para descargar los SDKs y plugins oficiales
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // Repositorio central de dependencias Maven
        gradlePluginPortal() // Repositorio para plugins comunitarios de Gradle
    }
}

plugins {
    // Configura convenciones para la resolución y descarga automática del entorno JDK
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Configuración de la resolución y descarga de librerías para todos los módulos
dependencyResolutionManagement {
    // Exige que todas las dependencias se declaren centralizadamente aquí (evita configuraciones ad-hoc en módulos)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()       // Repositorio oficial de Google para librerías de Android
        mavenCentral() // Repositorio central de dependencias de terceros
    }
}

// Nombre del proyecto raíz en Gradle
rootProject.name = "DeliveryTrack"

// Registro y vinculación de cada módulo que compone el proyecto
include(":mobile") // Módulo de la aplicación móvil para teléfono
include(":wear")   // Módulo de la aplicación para reloj inteligente WearOS
include(":shared") // Módulo de librerías y utilidades compartidas
include(":tv")     // Módulo del panel logístico para Smart TV