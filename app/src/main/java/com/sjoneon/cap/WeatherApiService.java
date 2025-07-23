// app/src/main/java/com/sjoneon/cap/WeatherApiService.java

package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    @GET("getVilageFcst") // 단기예보 조회 오퍼레이션
    Call<String> getVillageForecast(
            @Query("serviceKey") String serviceKey, // 인증키
            @Query("numOfRows") int numOfRows,       // 한 페이지 결과 수
            @Query("pageNo") int pageNo,             // 페이지 번호
            @Query("dataType") String dataType,       // 데이터 타입 (JSON)
            @Query("base_date") String baseDate,     // 발표일자
            @Query("base_time") String baseTime,     // 발표시각
            @Query("nx") int nx,                     // X좌표
            @Query("ny") int ny                      // Y좌표
    );
}