package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 국토교통부(TAGO) 버스 정보 API 서비스 인터페이스 (최종 수정)
 */
public interface TagoApiService {

    /**
     * 좌표기반 근접 정류소 목록 조회
     * [수정] BaseURL 변경에 따라, 서비스 경로 전체를 명시합니다.
     */
    @GET("ArvlInfoInqireSvc/getPrxbstList")
    Call<TagoBusStopResponse> getNearbyBusStops(
            @Query(value = "serviceKey", encoded = true) String serviceKey,
            @Query("gpsLati") double gpsLati,
            @Query("gpsLong") double gpsLong,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );

    /**
     * 정류소별 도착예정정보 목록 조회
     * [수정] BaseURL 변경에 따라, 서비스 경로 전체를 명시합니다.
     */
    @GET("ArvlInfoInqireSvc/getSttnAcctoArvlPrearngeInfoList")
    Call<TagoBusArrivalResponse> getBusArrivalInfo(
            @Query(value = "serviceKey", encoded = true) String serviceKey,
            @Query("cityCode") String cityCode,
            @Query("nodeId") String nodeId,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );

    /**
     * 노선별 경유 정류소 목록 조회
     * [수정] BaseURL 변경에 따라, 서비스 경로 전체를 명시합니다.
     */
    @GET("BusRouteInfoInqireSvc/getRouteAcctoThrghSttnList")
    Call<TagoBusRouteStationResponse> getBusRouteStationList(
            @Query(value = "serviceKey", encoded = true) String serviceKey,
            @Query("cityCode") String cityCode,
            @Query("routeId") String routeId,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );
}