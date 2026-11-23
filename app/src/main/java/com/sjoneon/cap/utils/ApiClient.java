package com.sjoneon.cap.utils;

import com.sjoneon.cap.BuildConfig;
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

public class ApiClient {
    private static final String TAG = "ApiClient";

    // 서버 주소는 local.properties의 DAYSYNC_BASE_URL에서 주입 (저장소에 IP를 남기지 않음)
    private static final String BASE_URL = BuildConfig.DAYSYNC_BASE_URL;

    private static ApiClient instance;
    private DaySyncApiService apiService;

    private ApiClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        // 출시 빌드에서는 대화/일정/UUID가 담긴 본문을 로그에 남기지 않음
        loggingInterceptor.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

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