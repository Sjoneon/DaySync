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
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 빌드 설정에 API 키 추가 (선택사항)
        buildConfigField("String", "NAVER_CLIENT_ID", "\"zda5po4kty\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"1i0v9qeEjUd9y9RdsXx8XFjUeQKk4GlY1aaC46Fj\"")
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

    buildFeatures {
        buildConfig = true
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

    // RecyclerView 추가
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