import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// local.properties 파일 로드
val properties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.sjoneon.cap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sjoneon.cap"
        minSdk = 21 // 네이버 지도 SDK 최소 21 (Android 5.0) 이상 필요
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ★★★ 이 부분을 삭제합니다. ★★★
        // manifestPlaceholders["com.naver.maps.map.CLIENT_ID"] = "l4dae8ewvg"
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

    // CardView 추가 (날씨 UI에서 사용)
    implementation("androidx.cardview:cardview:1.0.0")

    // RecyclerView 추가 (이미 포함되어 있을 수 있지만 명시적으로 추가)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Gson 라이브러리 (JSON 직렬화/역직렬화용)
    implementation("com.google.code.gson:gson:2.10.1")

    // 네이버 지도 SDK (최신 버전)
    implementation("com.naver.maps:map-sdk:3.21.0")

    // 네이버 지도 위치 추적 기능
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // OkHttp (네트워크 요청)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}