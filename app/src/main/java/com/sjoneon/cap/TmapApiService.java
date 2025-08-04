package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * TMAP 보행자 경로 API 인터페이스 (최종 수정)
 */
public interface TmapApiService {

    /**
     * 보행자 경로 정보를 요청합니다.
     * [수정] 공식 문서에 따라 POST + FormUrlEncoded 방식으로 요청하도록 수정합니다.
     * 파라미터를 @Query 대신 @Field로 변경하여 Request Body에 담아 전송합니다.
     */
    @FormUrlEncoded
    @POST("tmap/routes/pedestrian?version=1")
    Call<TmapPedestrianResponse> getPedestrianRoute(
            @Header("appKey") String appKey,
            @Field("startX") String startX,
            @Field("startY") String startY,
            @Field("endX") String endX,
            @Field("endY") String endY,
            @Field("startName") String startName,
            @Field("endName") String endName
    );
}