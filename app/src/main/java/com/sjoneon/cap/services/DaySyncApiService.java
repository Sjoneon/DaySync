package com.sjoneon.cap.services;

import com.sjoneon.cap.models.api.ApiResponse;
import com.sjoneon.cap.models.api.ChatRequest;
import com.sjoneon.cap.models.api.ChatResponse;
import com.sjoneon.cap.models.api.SessionUpdateRequest;
import com.sjoneon.cap.models.api.UserCreateRequest;
import com.sjoneon.cap.models.api.UserCreateResponse;
import com.sjoneon.cap.models.api.UserResponse;

import com.sjoneon.cap.models.api.CalendarEventRequest;
import com.sjoneon.cap.models.api.CalendarEventResponse;
import com.sjoneon.cap.models.api.CalendarEventUpdateRequest;

import com.sjoneon.cap.models.api.AlarmUpdateRequest;
import com.sjoneon.cap.models.api.AlarmRequest;
import com.sjoneon.cap.models.api.AlarmResponse;

import com.sjoneon.cap.models.api.SessionListResponse;
import com.sjoneon.cap.models.api.MessageListResponse;


import com.sjoneon.cap.models.api.RouteSaveRequest;
import com.sjoneon.cap.models.api.RouteSearchRequest;
import com.sjoneon.cap.models.api.RouteResponse;
import com.sjoneon.cap.models.api.RouteSearchResponse;
import com.sjoneon.cap.models.api.RouteListResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * DaySync 백엔드 API 서비스 인터페이스
 * Retrofit을 사용하여 FastAPI 백엔드와 통신
 */
public interface DaySyncApiService {

    // ========================================
    // AI 채팅 API
    // ========================================

    /**
     * AI 채팅 메시지 전송
     * POST /api/ai/chat
     */
    @POST("api/ai/chat")
    Call<ChatResponse> sendChatMessage(@Body ChatRequest request);

    /**
     * 사용자의 세션 목록 조회 (최근 15개)
     * GET /api/ai/sessions/{user_uuid}
     */
    @GET("api/ai/sessions/{user_uuid}")
    Call<SessionListResponse> getUserSessions(@Path("user_uuid") String userUuid);

    /**
     * 특정 세션의 메시지 목록 조회 (최대 50개)
     * GET /api/ai/sessions/{session_id}/messages
     */
    @GET("api/ai/sessions/{session_id}/messages")
    Call<MessageListResponse> getSessionMessages(@Path("session_id") int sessionId);

    /**
     * 세션 삭제
     * DELETE /api/ai/sessions/{session_id}
     */
    @DELETE("api/ai/sessions/{session_id}")
    Call<ApiResponse> deleteSession(@Path("session_id") int sessionId);

    // ========================================
    // 사용자 관리 API
    // ========================================

    /**
     * 새 사용자 생성
     * POST /api/users/
     */
    @POST("api/users/")
    Call<UserCreateResponse> createUser(@Body UserCreateRequest request);

    /**
     * 사용자 정보 조회
     * GET /api/users/{uuid}
     */
    @GET("api/users/{uuid}")
    Call<UserResponse> getUser(@Path("uuid") String uuid);

    // ========================================
    // 일정 관리 API
    // ========================================

    /**
     * 새 일정 생성
     */
    @POST("/api/schedule/calendar/events")
    Call<CalendarEventResponse> createCalendarEvent(@Body CalendarEventRequest request);

    /**
     * 사용자의 모든 일정 조회
     */
    @GET("/api/schedule/calendar/events/{user_uuid}")
    Call<List<CalendarEventResponse>> getUserEvents(@Path("user_uuid") String userUuid);

    /**
     * 일정 수정
     */
    @PUT("/api/schedule/calendar/events/{event_id}")
    Call<CalendarEventResponse> updateCalendarEvent(
            @Path("event_id") int eventId,
            @Body CalendarEventUpdateRequest request
    );

    /**
     * 일정 삭제
     */
    @DELETE("/api/schedule/calendar/events/{event_id}")
    Call<ApiResponse> deleteCalendarEvent(@Path("event_id") int eventId);

    // ========================================
    // 알람 관리 API
    // ========================================

    /**
     * 새 알람 생성
     */
    @POST("/api/schedule/alarms")
    Call<AlarmResponse> createAlarm(@Body AlarmRequest request);

    /**
     * 사용자의 모든 알람 조회
     */
    @GET("/api/schedule/alarms/{user_uuid}")
    Call<List<AlarmResponse>> getUserAlarms(@Path("user_uuid") String userUuid);

    /**
     * 알람 수정
     */
    @PUT("/api/schedule/alarms/{alarm_id}")
    Call<AlarmResponse> updateAlarm(
            @Path("alarm_id") int alarmId,
            @Body AlarmUpdateRequest request
    );

    /**
     * 알람 삭제
     */
    @DELETE("/api/schedule/alarms/{alarm_id}")
    Call<ApiResponse> deleteAlarm(@Path("alarm_id") int alarmId);

    /**
     * 알람 활성화/비활성화 토글
     */
    @PUT("/api/schedule/alarms/{alarm_id}/toggle")
    Call<AlarmResponse> toggleAlarm(@Path("alarm_id") int alarmId);

    /**
     * 세션 제목 수정
     * PUT /api/ai/sessions/{session_id}
     */
    @PUT("api/ai/sessions/{session_id}")
    Call<ApiResponse> updateSession(
            @Path("session_id") int sessionId,
            @Body SessionUpdateRequest request
    );

    /**
     * 경로 저장
     * POST /api/routes/save
     */
    @POST("api/routes/save")
    Call<RouteResponse> saveRoute(@Body RouteSaveRequest request);

    /**
     * 경로 검색 (좌표 기반)
     * POST /api/routes/search
     */
    @POST("api/routes/search")
    Call<RouteSearchResponse> searchRoute(@Body RouteSearchRequest request);

    /**
     * 최근 경로 목록 조회
     * GET /api/routes/recent?limit={limit}
     */
    @GET("api/routes/recent")
    Call<RouteListResponse> getRecentRoutes(@Query("limit") int limit);

    /**
     * 경로 삭제
     * DELETE /api/routes/{route_id}
     */
    @DELETE("api/routes/{route_id}")
    Call<Void> deleteRoute(@Path("route_id") int routeId);
}