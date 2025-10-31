package com.sjoneon.cap.services;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    /**
     * 기상청 API Hub - 초단기실황 API
     */
    @GET("VilageFcstInfoService_2.0/getUltraSrtNcst")
    Call<String> getUltraShortTermLive(
            @Query("authKey") String authKey,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("dataType") String dataType,
            @Query("base_date") String baseDate,
            @Query("base_time") String baseTime,
            @Query("nx") int nx,
            @Query("ny") int ny
    );

    /**
     * 기상청 API Hub - 단기예보(동네예보) API
     */
    @GET("VilageFcstInfoService_2.0/getVilageFcst")
    Call<String> getVillageForecast(
            @Query("authKey") String authKey,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("dataType") String dataType,
            @Query("base_date") String baseDate,
            @Query("base_time") String baseTime,
            @Query("nx") int nx,
            @Query("ny") int ny
    );

    /**
     * 기상청 API Hub - 중기기온예보 API
     */
    @GET("MidFcstInfoService/getMidTa")
    Call<String> getMidTermTemperature(
            @Query("authKey") String authKey,
            @Query("regId") String regId,
            @Query("tmFc") String tmFc,
            @Query("dataType") String dataType
    );

    /**
     * 기상청 API Hub - 중기육상예보 API
     */
    @GET("MidFcstInfoService/getMidLandFcst")
    Call<String> getMidLandForecast(
            @Query("authKey") String authKey,
            @Query("regId") String regId,
            @Query("tmFc") String tmFc,
            @Query("dataType") String dataType
    );
}