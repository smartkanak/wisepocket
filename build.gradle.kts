import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "dev.detekt")

    extensions.configure<DetektExtension> {
        // Our config only records where we *differ* from detekt's defaults, so upstream's rules keep
        // applying as they evolve instead of being frozen at whatever we copied once.
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // Report paths relative to the repo root, so findings stay readable in CI logs.
        basePath = rootProject.layout.projectDirectory
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        // The type-resolution tasks analyse a whole compilation, which drags in Compose's generated `Res`
        // class. We don't write that code and can't fix it, so linting it is pure noise.
        //
        // Matched on the absolute path on purpose: an `exclude("**/build/**")` pattern is resolved against
        // each file's path *relative to its source root*, and the generated root already sits inside
        // build/, so the pattern never sees a "build" segment to match.
        exclude { it.file.absolutePath.contains("${File.separator}build${File.separator}") }

        reports {
            html.required = true
            // SARIF is the format GitHub code scanning ingests, should we ever add CI.
            sarif.required = true
            markdown.required = false
            checkstyle.required = false
        }
    }
}
