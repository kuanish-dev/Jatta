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
        jvmTest.dependencies {
            implementation(libs.litertlm.jvm)
        }
    }
}

val contentDir: String = rootProject.layout.projectDirectory.dir("content").asFile.absolutePath
val sourcesDir: String = rootProject.layout.projectDirectory.dir("sources").asFile.absolutePath
tasks.named<Test>("jvmTest") {
    systemProperty("contentDir", contentDir)
    systemProperty("sourcesDir", sourcesDir)
    inputs.dir(contentDir)
    inputs.dir(sourcesDir)
    for (name in listOf("ollamaModel", "litertModel")) {
        val value = providers.gradleProperty(name).orElse("")
        systemProperty(name, value.get().let { if (it.isEmpty() || it.startsWith("/")) it else rootProject.file(it).path })
        inputs.property(name, value)
    }
}
