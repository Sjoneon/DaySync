package com.sjoneon.cap.utils;

import com.sjoneon.cap.services.DaySyncApiService;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

/**
 * API 클라이언트 싱글톤
 * Retrofit 인스턴스를 관리하고 DaySyncApiService를 제공
 */

/**
 * 현재 로컬에서 테스트 중이므로 안드로이드 스튜디오 - ApiClient.java에 있는 로컬 ip를 테스트 환경에 맞게 수정해야 정상 작동합니다.
 */
public class ApiClient {
    private static final String TAG = "ApiClient";

    // 에뮬레이터용
    private static final String BASE_URL_EMULATOR = "http://10.0.2.2:8000/";

    // 실제 기기용 (개발 PC의 로컬 IP로 변경 필요)
    private static final String BASE_URL_DEVICE = "http://192.168.0.24:8000/";

    // 클라우드 배포용
    private static final String BASE_URL_CLOUD = "http://15.165.205.156:8000/";

    // 현재 사용할 BASE_URL
    private static final String BASE_URL = BASE_URL_CLOUD;


    private static ApiClient instance;
    private DaySyncApiService apiService;

    private ApiClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 타임아웃을 각 상황(API)에 맞게 처리 (사용자 경험 개선)
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS) // 연결 타임아웃: 10초
                .readTimeout(30, TimeUnit.SECONDS) // 읽기 타임아웃: 30초
                .writeTimeout(30, TimeUnit.SECONDS) // 쓰기 타임아웃: 30초
                .addInterceptor(loggingInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(DaySyncApiService.class);
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public DaySyncApiService getApiService() {
        return apiService;
    }

    public static DaySyncApiService getDaySyncApiService() {
        return getInstance().getApiService();
    }

    public String getBaseUrl() {
        return BASE_URL;
    }
}