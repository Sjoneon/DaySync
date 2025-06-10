plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sjoneon.cap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sjoneon.cap"
        minSdk = 24 // 친구와 동일하게 24로 상향 조정
        targetSdk = 35
        versionCode = 1
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Gson 라이브러리 (JSON 직렬화/역직렬화용)
    implementation("com.google.code.gson:gson:2.10.1")

    // 네이버 지도 SDK (친구와 동일한 버전)
    implementation("com.naver.maps:map-sdk:3.21.0")

    // 네이버 지도 위치 추적 기능 (친구와 동일한 버전으로 업데이트)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OkHttp (네트워크 요청)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}