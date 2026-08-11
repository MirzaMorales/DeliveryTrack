// Archivo de configuración global de Gradle para el proyecto raíz DeliveryTrack.
// En una arquitectura multi-módulo, este archivo define y carga los plugins de compilación
// para que estén disponibles en el classpath de todos los módulos del proyecto (mobile, wear, tv, shared),
// pero sin aplicarlos directamente sobre el proyecto raíz ('apply false').

plugins {
    // Plugin para compilar aplicaciones Android (módulos mobile, wear y tv)
    alias(libs.plugins.android.application) apply false
    
    // Plugin para compilar bibliotecas comunes de Android (módulo shared)
    alias(libs.plugins.android.library) apply false
    
    // Plugin para habilitar e integrar Jetpack Compose con el compilador Kotlin
    alias(libs.plugins.kotlin.compose) apply false
}
