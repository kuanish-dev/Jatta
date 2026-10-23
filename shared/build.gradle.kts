import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.langengine.shared"
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
            implementation(project(":study"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
        }
    }
}

// The course lives in content/ at the repository root; the app gets a copy as Compose resources.
val syncContent = tasks.register<Sync>("syncContent") {
    from(rootProject.layout.projectDirectory.dir("content"))
    into(layout.buildDirectory.dir("courseResources/files/content"))
}

compose.resources {
    packageOfResClass = "com.langengine.shared.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = layout.dir(syncContent.map { it.destinationDir.parentFile.parentFile }),
    )
}
