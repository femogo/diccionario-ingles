plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Módulo Kotlin puro a propósito: no depende del SDK de Android, así que su
// lógica se compila y se prueba en cualquier máquina, incluida la integración
// continua, sin emulador ni dispositivo.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
