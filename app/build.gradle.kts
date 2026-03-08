import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties
import java.io.FileInputStream

val signingPropsFile = rootProject.file("C:/Users/avan/Documents/android/signing.properties")
val signingProps = Properties()

if (signingPropsFile.exists()) {
    signingProps.load(FileInputStream(signingPropsFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    kotlin("kapt")
}

android {
    namespace = "com.zirohill.lister"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zirohill.lister"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = signingProps["storeFile"]?.let { file(it.toString()) }
            storePassword = signingProps["storePassword"]?.toString()
            keyAlias = signingProps["keyAlias"]?.toString()
            keyPassword = signingProps["keyPassword"]?.toString()
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as BaseVariantOutputImpl
            val appName = "Eagle"
            val buildType = buildType.name

            val newApkName = "${appName}_${buildType}.apk"

            outputImpl.outputFileName = newApkName
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

            androidComponents {
                onVariants(selector().withBuildType("release")) { variant ->
                    variant.outputs.forEach { output ->
                        val outputImpl = output as? BaseVariantOutputImpl
                        outputImpl?.let {
                            val appName = "Eagle"
                            val version = "1.0"
                            it.outputFileName = "${appName}_v${version}.apk"
                        }
                    }
                }
            }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.animation:animation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.compose.runtime:runtime-saveable:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.7.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.7")
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    implementation("androidx.compose.foundation:foundation:1.5.0")
    implementation("androidx.compose.material:material:1.5.0")
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    implementation("androidx.compose.animation:animation:1.5.0")
    implementation("androidx.activity:activity-compose:1.10.0")
}
