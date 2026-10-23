// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 использует встроенный Kotlin. Чтобы он компилировал той же версией, что и
// зависимости (litertlm собрана Kotlin 2.4.0), явно задаём KGP.
// Версию держите равной `kotlin` в gradle/libs.versions.toml.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
