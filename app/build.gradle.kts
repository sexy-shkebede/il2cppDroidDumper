plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.il2cpp.dumper.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.il2cpp.dumper.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // A bundled self-signed key so `assembleRelease` yields an installable APK out of the box.
            // Replace with your own keystore for a real release.
            val ks = file("dumper-release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "dumper123"
                keyAlias = "dumper"
                keyPassword = "dumper123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (file("dumper-release.jks").exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/scripts/LICENSE-Il2CppDumper.txt")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
