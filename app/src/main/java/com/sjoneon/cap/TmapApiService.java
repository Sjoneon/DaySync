package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * TMAP 보행자 경로 API 인터페이스
 */
public interface TmapApiService {
    @POST("tmap/routes/pedestrian?version=1")
    Call<TmapPedestrianResponse> getPedestrianRoute(
            @Header("appKey") String appKey,
            @Query("startX") String startX,
            @Query("startY") String startY,
            @Query("endX") String endX,
            @Query("endY") String endY,
            @Query("startName") String startName,
            @Query("endName") String endName
    );
}