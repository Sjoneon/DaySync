// /app/src/main/java/com/sjoneon/cap/WeatherApiService.java

package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    /**
     * 기상청 단기예보 API
     */
    @GET("VilageFcstInfoService_2.0/getVilageFcst")
    Call<String> getVillageForecast(
            @Query("serviceKey") String serviceKey,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("dataType") String dataType,
            @Query("base_date") String baseDate,
            @Query("base_time") String baseTime,
            @Query("nx") int nx,
            @Query("ny") int ny
    );

    /**
     * 기상청 중기기온예보 API
     */
    @GET("MidFcstInfoService/getMidTa")
    Call<String> getMidTermTemperature(
            @Query("serviceKey") String serviceKey,
            @Query("regId") String regId, // 지역 코드
            @Query("tmFc") String tmFc,   // 발표 시각
            @Query("dataType") String dataType
    );

    @GET("MidFcstInfoService/getMidLandFcst")
    Call<String> getMidLandForecast(
            @Query("serviceKey") String serviceKey,
            @Query("regId") String regId, // 지역 코드
            @Query("tmFc") String tmFc,   // 발표 시각
            @Query("dataType") String dataType
    );
}