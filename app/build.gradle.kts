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
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 기존 기상청 API 키
        buildConfigField("String", "KMA_API_KEY", "\"${properties.getProperty("KMA_API_KEY")}\"")

        // [수정] 네이버 API 키 (local.properties의 키 이름과 동일하게 매핑)
        buildConfigField("String", "NAVER_CLIENT_ID", "\"${properties.getProperty("NAVER_CLIENT_ID")}\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"${properties.getProperty("NAVER_CLIENT_SECRET")}\"")
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
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // 네이버 지도 SDK
    implementation("com.naver.maps:map-sdk:3.21.0")

    // Google Play 서비스 위치 라이브러리 (현위치 기능)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Retrofit (네트워크 통신 라이브러리)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // Gson Converter (JSON <-> 객체 변환)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Scalars Converter (응답을 String 등 기본 타입으로 받기 위함)
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}