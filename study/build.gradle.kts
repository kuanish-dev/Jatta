plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":engine"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// JVM tests check the real course files in content/
val contentDir: String = rootProject.layout.projectDirectory.dir("content").asFile.absolutePath
tasks.named<Test>("jvmTest") {
    systemProperty("contentDir", contentDir)
    inputs.dir(contentDir)
}
