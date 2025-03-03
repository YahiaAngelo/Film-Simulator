import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose) version "1.7.1"
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.serialization")  version "2.0.21"
    id("app.cash.sqldelight") version "2.0.1"
}


sqldelight {
    databases {
        create("Database") {
            packageName.set("io.github.yahiaangelo.filmsimulator")
        }
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }
        pod("ffmpeg-kit-ios-min") {
            moduleName = "ffmpegkit"
            version = "6.0"
            extraOpts = listOf("-compiler-option", "-fmodules")
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
            languageSettings.optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.lifecycle.viewmodel.ktx)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.koin.android)
            implementation(libs.android.driver)
            implementation (libs.ffmpeg.kit.min)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.logging)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenModel)
            implementation(libs.voyager.tabNavigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)
            implementation(libs.koin.core)
            implementation(libs.koin.test)
            implementation(libs.koin.compose)
            implementation(libs.stately.common)
            implementation(libs.okio)
            implementation(libs.coroutines.extensions)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.make.observable)
            api(libs.image.loader)
            implementation(libs.coil.compose)
            implementation(libs.zoomimage.compose.coil3)
            implementation(libs.filekit.core)
            implementation(libs.filekit.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.autolinktext)
            implementation(libs.skiko)
            //api(libs.image.loader.extension.blur)
        }

        nativeMain.dependencies {
            implementation(libs.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    task("testClasses")
}

android {
    namespace = "io.github.yahiaangelo.filmsimulator"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}