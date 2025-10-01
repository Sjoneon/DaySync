package com.sjoneon.cap.services;

import com.sjoneon.cap.models.api.NaverDirectionsResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/**
 * 네이버 Directions 5 API 호출을 위한 최종 인터페이스
 * GET 방식을 사용하며, 올바른 엔드포인트와 파라미터를 지정합니다.
 */
public interface NaverApiService {
    @GET("https://maps.apigw.ntruss.com/map-direction/v1/driving")
    Call<NaverDirectionsResponse> getDrivingDirections(
            @Header("X-NCP-APIGW-API-KEY-ID") String clientId,
            @Header("X-NCP-APIGW-API-KEY") String clientSecret,
            @Query("start") String start,
            @Query("goal") String goal
    );
}