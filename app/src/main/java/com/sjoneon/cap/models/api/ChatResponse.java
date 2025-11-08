package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * AI 채팅 응답 모델
 * FastAPI 백엔드의 ChatResponse와 매핑됩니다.
 */
public class ChatResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("ai_response")
    private String aiResponse;

    @SerializedName("session_id")
    private Integer sessionId;

    @SerializedName("message_id")
    private Integer messageId;

    @SerializedName("error")
    private String error;

    @SerializedName("function_called")
    private String functionCalled;

    // 경로 탐색 관련 필드
    @SerializedName("route_search_requested")
    private Boolean routeSearchRequested;

    @SerializedName("start_location")
    private String startLocation;

    @SerializedName("destination")
    private String destination;

    // 날씨 조회 관련 필드
    @SerializedName("weather_requested")
    private Boolean weatherRequested;

    @SerializedName("weather_target_date")
    private String weatherTargetDate;

    // 생성자
    public ChatResponse(boolean success, String aiResponse, Integer sessionId, Integer messageId) {
        this.success = success;
        this.aiResponse = aiResponse;
        this.sessionId = sessionId;
        this.messageId = messageId;
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getAiResponse() {
        return aiResponse;
    }

    public Integer getSessionId() {
        return sessionId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public String getError() {
        return error;
    }

    public String getFunctionCalled() {
        return functionCalled;
    }

    public Boolean getRouteSearchRequested() {
        return routeSearchRequested;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public String getDestination() {
        return destination;
    }

    public Boolean getWeatherRequested() {
        return weatherRequested;
    }

    public String getWeatherTargetDate() {
        return weatherTargetDate;
    }

    // Setters
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setAiResponse(String aiResponse) {
        this.aiResponse = aiResponse;
    }

    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setFunctionCalled(String functionCalled) {
        this.functionCalled = functionCalled;
    }

    public void setRouteSearchRequested(Boolean routeSearchRequested) {
        this.routeSearchRequested = routeSearchRequested;
    }

    public void setStartLocation(String startLocation) {
        this.startLocation = startLocation;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setWeatherRequested(Boolean weatherRequested) {
        this.weatherRequested = weatherRequested;
    }

    public void setWeatherTargetDate(String weatherTargetDate) {
        this.weatherTargetDate = weatherTargetDate;
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "success=" + success +
                ", aiResponse='" + aiResponse + '\'' +
                ", sessionId=" + sessionId +
                ", messageId=" + messageId +
                ", error='" + error + '\'' +
                ", functionCalled='" + functionCalled + '\'' +
                ", routeSearchRequested=" + routeSearchRequested +
                ", startLocation='" + startLocation + '\'' +
                ", destination='" + destination + '\'' +
                ", weatherRequested=" + weatherRequested +
                ", weatherTargetDate='" + weatherTargetDate + '\'' +
                '}';
    }
}