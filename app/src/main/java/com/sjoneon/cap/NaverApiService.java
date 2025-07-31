package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

// 자동차 길찾기 API를 호출하도록 수정
public interface NaverApiService {
    @GET("map-direction-15/v1/driving")
    Call<NaverDirectionsResponse> getDrivingDirections(
            @Header("X-NCP-APIGW-API-KEY-ID") String clientId,
            @Header("X-NCP-APIGW-API-KEY") String clientSecret,
            @Query("start") String start,
            @Query("goal") String goal
    );
}