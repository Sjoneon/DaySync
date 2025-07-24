// /app/src/main/java/com/sjoneon/cap/WeatherApiService.java

package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    /**
     * [추가] 기상청 초단기실황 API (현재 날씨 정보)
     * 이 API를 통해 현재 시점의 정확한 기온, 강수 상태 등을 가져옵니다.
     */
    @GET("VilageFcstInfoService_2.0/getUltraSrtNcst")
    Call<String> getUltraShortTermLive(
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
     * 기상청 단기예보(동네예보) API (시간별 예보)
     * 앞으로 몇 시간 동안의 날씨 예측 정보를 가져옵니다.
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

    /**
     * 기상청 중기육상예보 API
     */
    @GET("MidFcstInfoService/getMidLandFcst")
    Call<String> getMidLandForecast(
            @Query("serviceKey") String serviceKey,
            @Query("regId") String regId, // 지역 코드
            @Query("tmFc") String tmFc,   // 발표 시각
            @Query("dataType") String dataType
    );
}