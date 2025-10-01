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
public class ApiClient {
    private static final String TAG = "ApiClient";

    // 에뮬레이터용
    private static final String BASE_URL_EMULATOR = "http://10.0.2.2:8000/";

    // 실제 기기용 (개발 PC의 로컬 IP로 변경 필요)
    private static final String BASE_URL_DEVICE = "http://192.168.0.10:8000/";

    // 현재 사용할 BASE_URL
    private static final String BASE_URL = BASE_URL_EMULATOR;

    private static ApiClient instance;
    private DaySyncApiService apiService;

    private ApiClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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

    public String getBaseUrl() {
        return BASE_URL;
    }
}