package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TagoApiService {
    @GET("ArvlInfoInqireSvc/getPrxbstList")
    Call<TagoBusStopResponse> getNearbyBusStops(
            @Query(value = "serviceKey", encoded = true) String serviceKey,
            @Query("gpsLati") double gpsLati,
            @Query("gpsLong") double gpsLong,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );

    @GET("ArvlInfoInqireSvc/getSttnAcctoArvlPrearngeInfoList")
    Call<TagoBusArrivalResponse> getBusArrivalInfo(
            @Query(value = "serviceKey", encoded = true) String serviceKey,
            @Query("cityCode") String cityCode,
            @Query("nodeId") String nodeId,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );

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