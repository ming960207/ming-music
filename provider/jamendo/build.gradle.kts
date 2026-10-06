plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.android.kotlin.multiplatform.library) }
kotlin {
 android { namespace = "org.feeluown.mobile.provider.jamendo"; compileSdk = libs.versions.androidCompileSdk.get().toInt(); minSdk = libs.versions.androidMinSdk.get().toInt(); withHostTest {}; compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
 iosArm64(); iosSimulatorArm64()
 sourceSets { commonMain.dependencies { api(project(":provider:runtime")); implementation(libs.kotlinx.serialization.json); implementation(libs.ktor.client.core) }; commonTest.dependencies { implementation(kotlin("test")); implementation(libs.kotlinx.coroutines.test) } }
}