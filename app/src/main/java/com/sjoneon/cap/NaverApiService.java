package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

// API 호출 주소를 표준 엔드포인트로 최종 수정
public interface NaverApiService {
    @GET("map-direction/v1/driving")
    Call<NaverDirectionsResponse> getDrivingDirections(
            @Header("X-NCP-APIGW-API-KEY-ID") String clientId,
            @Header("X-NCP-APIGW-API-KEY") String clientSecret,
            @Query("start") String start,
            @Query("goal") String goal
    );
}