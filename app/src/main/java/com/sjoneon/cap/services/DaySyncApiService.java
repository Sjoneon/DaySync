package com.sjoneon.cap.services;

import com.sjoneon.cap.models.api.ChatRequest;
import com.sjoneon.cap.models.api.ChatResponse;
import com.sjoneon.cap.models.api.UserCreateRequest;
import com.sjoneon.cap.models.api.UserCreateResponse;
import com.sjoneon.cap.models.api.UserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * DaySync 백엔드 API 서비스 인터페이스
 * Retrofit을 사용하여 FastAPI 백엔드와 통신
 */
public interface DaySyncApiService {

    /**
     * AI 채팅 메시지 전송
     * POST /api/ai/chat
     */
    @POST("api/ai/chat")
    Call<ChatResponse> sendChatMessage(@Body ChatRequest request);

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
}