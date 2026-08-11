# Documentación de la Configuración y Dependencias Raíz

Este documento describe la estructura y configuración global de compilación de la plataforma **DeliveryTrack**, la cual se gestiona mediante el build system Gradle utilizando Kotlin DSL y un catálogo centralizado de dependencias (`libs.versions.toml`).

---

## 1. Archivos Raíz de Compilación y Scripts de Gradle

### `build.gradle.kts`
Este es el archivo de configuración global de Gradle para el proyecto raíz. En un proyecto multi-módulo, su función principal es declarar los plugins compartidos que estarán disponibles para todos los submódulos, pero sin aplicarlos directamente en la raíz (`apply false`).

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

### `settings.gradle.kts`
Define la estructura del proyecto multi-módulo. Configura los repositorios de descarga para los plugins y las dependencias (Google y Maven Central), activa el modo de resolución estricto (`FAIL_ON_PROJECT_REPOS`) y declara explícitamente cuáles son los submódulos que componen la aplicación.

```kotlin
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

rootProject.name = "DeliveryTrack"
include(":mobile")
include(":wear")
include(":shared")
include(":tv")
```

### `gradle.properties`
Define parámetros y propiedades de configuración para la máquina virtual de Java (JVM) y el entorno de ejecución de Gradle. Controla la memoria máxima asignada al demonio de Gradle, habilita el uso de librerías de AndroidX, y activa la paralelización de tareas para optimizar los tiempos de compilación.

```properties
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.parallel=true
org.gradle.caching=true
```

---

## 2. Gradle Wrapper

El Gradle Wrapper es la herramienta recomendada para ejecutar compilaciones de Gradle de manera consistente en cualquier entorno, sin necesidad de tener previamente instalado Gradle en el sistema. Consta de los siguientes archivos:

*   **`gradlew`**: Script de ejecución ejecutable para sistemas basados en Unix/Linux/macOS.
*   **`gradlew.bat`**: Script ejecutable de procesamiento por lotes para sistemas Windows.
*   **`gradle/wrapper/gradle-wrapper.jar`**: El código binario del wrapper que realiza la descarga automatizada de la versión adecuada de Gradle.
*   **`gradle/wrapper/gradle-wrapper.properties`**: Archivo de propiedades que especifica la URL de distribución y la versión exacta de Gradle a utilizar (por ejemplo, Gradle 8.x).

Para compilar o sincronizar el proyecto desde la terminal se ejecutan los comandos:
*   En Windows: `gradlew.bat build` o `gradlew.bat assembleDebug`
*   En Unix/macOS: `./gradlew build` o `./gradlew assembleDebug`

---

## 3. Catálogo de Versiones (`libs.versions.toml`)

Ubicado en `gradle/libs.versions.toml`, este archivo proporciona un catálogo centralizado de dependencias y plugins. Permite asegurar que todos los submódulos (`mobile`, `wear`, `tv`, `shared`) utilicen exactamente las mismas versiones de las bibliotecas de terceros, previniendo conflictos de dependencias de manera limpia.

El archivo está dividido en tres secciones fundamentales:

### `[versions]`
Define las constantes de versión para cada plugin y dependencia.
```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.10"
composeBom = "2024.09.00"
composeMaterial3 = "1.5.0-beta01"
composeFoundation = "1.5.0-beta01"
composeUiTooling = "1.5.0-beta01"
wearToolingPreview = "1.0.0"
activityCompose = "1.13.0"
coreSplashscreen = "1.2.0"
playServicesWearable = "20.0.1"
```

### `[libraries]`
Declara las librerías físicas referenciando sus nombres de grupo, artefactos y la versión declarada en `[versions]`. Las librerías de Compose que no especifican versión utilizan el BOM (`compose-bom`) para resolver la versión automáticamente.
```toml
[libraries]
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
ui = { group = "androidx.compose.ui", name = "ui" }
ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-material3 = { group = "androidx.wear.compose", name = "compose-material3", version.ref = "composeMaterial3" }
compose-foundation = { group = "androidx.wear.compose", name = "compose-foundation", version.ref = "composeFoundation" }
compose-ui-tooling = { group = "androidx.wear.compose", name = "compose-ui-tooling", version.ref = "composeUiTooling" }
wear-tooling-preview = { group = "androidx.wear", name = "wear-tooling-preview", version.ref = "wearToolingPreview" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
```

### `[plugins]`
Asocia los IDs de plugins de Gradle (como el compilador de Kotlin o el Gradle Build Tool de Android) con sus respectivas versiones.
```toml
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```
