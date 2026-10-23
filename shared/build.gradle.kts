import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.jatta.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":study"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.genai.prompt)
            implementation(libs.androidx.work.runtime)
        }
    }
}

// Compose resources = this module's own (fonts, licenses) plus a copy of the course from content/
// at the repository root, merged into one directory.
val syncResources = tasks.register<Sync>("syncResources") {
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    from(rootProject.layout.projectDirectory.dir("content")) { into("files/content") }
    into(layout.buildDirectory.dir("appResources"))
}

compose.resources {
    packageOfResClass = "com.jatta.shared.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = layout.dir(syncResources.map { it.destinationDir }),
    )
}
