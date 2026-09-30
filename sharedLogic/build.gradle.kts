import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.koinCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.skie)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

val feedInteropTests = providers.gradleProperty("feedInteropTests").map(String::toBoolean).getOrElse(false)

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64(),
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "com.ndynagn.kmp.news.sharedLogic"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        if (feedInteropTests) {
            appleMain {
                kotlin.srcDir("src/appleInteropTest/kotlin")
                dependencies { implementation(libs.ktor.client.mock) }
            }
        }
        commonMain.dependencies {
            api(libs.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlin.testJunit)
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        appleMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64", "kspMacosArm64").forEach {
        add(it, libs.room.compiler)
    }
}

koinCompiler {
    compileSafety = true
    strictSafety = true
    unsafeDslChecks = true
}
