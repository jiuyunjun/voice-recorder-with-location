import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.voicerecorderlocation"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.voicerecorderlocation"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        val localProperties = Properties().apply {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use(::load)
            }
        }
        val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
            .orElse(localProperties.getProperty("MAPS_API_KEY", ""))
            .get()
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.play.services.location)
    implementation(libs.google.play.services.maps)
    implementation(libs.google.maps.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
