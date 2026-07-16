import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "date.oxi.wisepocket.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.pdfium.android)
            // For the SAF document picker in PdfPicker.android.kt.
            implementation(libs.androidx.activity.compose)
            // Only for androidContext() in the Android entry point's Koin start-up.
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
            implementation(libs.llamatik)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.test)
        }
        // Konsist is a JVM-only library, so the architecture tests live in the one JVM test source set we
        // have. That costs nothing in coverage: Konsist reads .kt files off disk rather than off the
        // classpath, so from here it still sees commonMain, iosMain and androidApp alike.
        getByName("androidHostTest").dependencies {
            implementation(libs.konsist)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

/**
 * Every .kt file in this module, analysed exactly once.
 *
 * Wiring this by hand is not optional: `check` on its own only reaches the bare `detekt` task, which is
 * NO-SOURCE in a KMP project — it would go green having analysed nothing at all.
 *
 * detekt derives a task per KMP source set, and those overlap heavily — `nativeMain`, `appleMain`,
 * `iosArm64Main` and `iosSimulatorArm64Main` would each re-analyse the same iosMain files (they're the
 * only ones holding any). These three are the minimal set with full coverage:
 *
 *  - detektMainAndroid     → commonMain + androidMain, *with* type resolution
 *  - detektHostTestAndroid → commonTest + androidHostTest, with type resolution
 *  - detektIosMainSourceSet → iosMain (detekt has no type resolution for Native targets)
 */
tasks.named("check") {
    dependsOn("detektMainAndroid", "detektHostTestAndroid", "detektIosMainSourceSet")
}