plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cu.lenier.nextchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "cu.lenier.nextchat"
        minSdk = 24
        targetSdk = 35
        versionCode = 51
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources.excludes.add("META-INF/LICENSE.md")
        resources.excludes.add("META-INF/NOTICE.md")
    }

}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    implementation(libs.lifecycle.extensions)
    implementation(libs.work.runtime)
    implementation(libs.core)
    implementation(libs.legacy.support.core.utils)
    implementation(libs.gson)
    implementation (libs.update.checker)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.github.alxrm:audiowave-progressbar:0.9.2")

}