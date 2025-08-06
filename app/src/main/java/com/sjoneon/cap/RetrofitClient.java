package com.sjoneon.cap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String TAGO_API_BASE_URL = "https://apis.data.go.kr/1613000/";
    private static final String TMAP_API_BASE_URL = "https://apis.openapi.sk.com/";

    private static Retrofit tagoInstance = null;
    private static Retrofit tmapInstance = null;

    // [수정] setLenient() 옵션을 추가하여 Gson 객체 생성
    private static Gson gson = new GsonBuilder()
            .setLenient()
            .create();

    public static Retrofit getTagoInstance() {
        if (tagoInstance == null) {
            tagoInstance = new Retrofit.Builder()
                    .baseUrl(TAGO_API_BASE_URL)
                    // 수정된 Gson 객체 사용
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return tagoInstance;
    }

    public static Retrofit getTmapInstance() {
        if (tmapInstance == null) {
            tmapInstance = new Retrofit.Builder()
                    .baseUrl(TMAP_API_BASE_URL)
                    // 수정된 Gson 객체 사용
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return tmapInstance;
    }
}