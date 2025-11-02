package com.sjoneon.cap.services;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Gemini Live API를 사용한 실시간 음성 대화 서비스
 */
public class GeminiLiveApiService {
    private static final String TAG = "GeminiLiveApiService";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private Context context;
    private String apiKey;
    private WebSocket webSocket;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private ExecutorService executorService;
    private AtomicBoolean isRecording;
    private AtomicBoolean isConnected;
    private LiveApiListener listener;

    /**
     * Live API 이벤트 리스너
     */
    public interface LiveApiListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onTextResponse(String text);
        void onAudioResponse(byte[] audioData);
        void onFunctionCall(String functionName, JSONObject parameters);
    }

    public GeminiLiveApiService(Context context, String apiKey) {
        this.context = context;
        this.apiKey = apiKey;
        this.executorService = Executors.newFixedThreadPool(3);
        this.isRecording = new AtomicBoolean(false);
        this.isConnected = new AtomicBoolean(false);
    }

    /**
     * 리스너 설정
     */
    public void setListener(LiveApiListener listener) {
        this.listener = listener;
    }

    /**
     * Live API 연결 시작
     */
    public void connect() {
        if (isConnected.get()) {
            Log.w(TAG, "이미 연결되어 있습니다");
            return;
        }

        String url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=" + apiKey;

        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(url).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket 연결 성공");
                isConnected.set(true);
                sendSetupMessage();
                if (listener != null) {
                    listener.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "텍스트 메시지 수신: " + text);
                handleTextMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "바이너리 메시지 수신");
                if (listener != null) {
                    listener.onAudioResponse(bytes.toByteArray());
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket 연결 실패", t);
                isConnected.set(false);
                if (listener != null) {
                    listener.onError("연결 실패: " + t.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket 연결 종료: " + reason);
                isConnected.set(false);
                if (listener != null) {
                    listener.onDisconnected();
                }
            }
        });
    }

    /**
     * 초기 설정 메시지 전송
     */
    private void sendSetupMessage() {
        try {
            JSONObject setup = new JSONObject();
            JSONObject model = new JSONObject();
            model.put("model", "models/gemini-live-2.5-flash-preview");

            JSONObject config = new JSONObject();
            JSONArray responseModalities = new JSONArray();
            responseModalities.put("AUDIO");
            config.put("response_modalities", responseModalities);

            config.put("system_instruction",
                    "당신은 DaySync 앱의 음성 비서입니다. " +
                            "사용자가 화면 이동, 알람 설정, 경로 검색 등을 요청하면 적절한 함수를 호출하세요. " +
                            "한국어로 자연스럽고 친절하게 대답하세요.");

            // Function declarations 추가
            JSONArray tools = new JSONArray();
            JSONObject toolObject = new JSONObject();
            JSONArray functionDeclarations = new JSONArray();

            // navigate_to_screen 함수
            JSONObject navigateFunction = new JSONObject();
            navigateFunction.put("name", "navigate_to_screen");
            navigateFunction.put("description", "앱 내 특정 화면으로 이동합니다");
            JSONObject navParams = new JSONObject();
            navParams.put("type", "OBJECT");
            JSONObject navProperties = new JSONObject();
            JSONObject screenName = new JSONObject();
            screenName.put("type", "STRING");
            screenName.put("description", "이동할 화면 이름");
            JSONArray enumValues = new JSONArray();
            enumValues.put("calendar");
            enumValues.put("alarm");
            enumValues.put("route");
            enumValues.put("map");
            enumValues.put("weather");
            enumValues.put("notifications");
            enumValues.put("settings");
            enumValues.put("help");
            screenName.put("enum", enumValues);
            navProperties.put("screen_name", screenName);
            navParams.put("properties", navProperties);
            JSONArray requiredFields = new JSONArray();
            requiredFields.put("screen_name");
            navParams.put("required", requiredFields);
            navigateFunction.put("parameters", navParams);

            functionDeclarations.put(navigateFunction);
            toolObject.put("function_declarations", functionDeclarations);
            tools.put(toolObject);

            config.put("tools", tools);

            setup.put("setup", model);
            setup.put("config", config);

            String message = setup.toString();
            Log.d(TAG, "설정 메시지 전송: " + message);
            webSocket.send(message);

        } catch (Exception e) {
            Log.e(TAG, "설정 메시지 전송 실패", e);
        }
    }

    /**
     * 텍스트 메시지 처리
     */
    private void handleTextMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            // Function call 체크
            if (json.has("serverContent")) {
                JSONObject serverContent = json.getJSONObject("serverContent");
                if (serverContent.has("modelTurn")) {
                    JSONObject modelTurn = serverContent.getJSONObject("modelTurn");
                    if (modelTurn.has("parts")) {
                        JSONArray parts = modelTurn.getJSONArray("parts");
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);

                            // Function call
                            if (part.has("functionCall")) {
                                JSONObject functionCall = part.getJSONObject("functionCall");
                                String functionName = functionCall.getString("name");
                                JSONObject args = functionCall.optJSONObject("args");

                                Log.d(TAG, "함수 호출: " + functionName);
                                if (listener != null) {
                                    listener.onFunctionCall(functionName, args);
                                }
                            }

                            // Text response
                            if (part.has("text")) {
                                String text = part.getString("text");
                                Log.d(TAG, "텍스트 응답: " + text);
                                if (listener != null) {
                                    listener.onTextResponse(text);
                                }
                            }

                            // Audio data
                            if (part.has("inlineData")) {
                                JSONObject inlineData = part.getJSONObject("inlineData");
                                if (inlineData.has("data")) {
                                    String base64Audio = inlineData.getString("data");
                                    byte[] audioData = android.util.Base64.decode(
                                            base64Audio, android.util.Base64.DEFAULT);
                                    if (listener != null) {
                                        listener.onAudioResponse(audioData);
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "메시지 처리 실패", e);
        }
    }

    /**
     * 오디오 녹음 시작
     */
    public void startRecording() {
        if (isRecording.get()) {
            Log.w(TAG, "이미 녹음 중입니다");
            return;
        }

        if (!isConnected.get()) {
            Log.w(TAG, "연결되지 않았습니다");
            return;
        }

        executorService.execute(() -> {
            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE
                );

                audioRecord.startRecording();
                isRecording.set(true);
                Log.d(TAG, "오디오 녹음 시작");

                byte[] buffer = new byte[BUFFER_SIZE];

                while (isRecording.get()) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        sendAudioData(buffer, bytesRead);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "오디오 녹음 실패", e);
            }
        });
    }

    /**
     * 오디오 데이터 전송
     */
    private void sendAudioData(byte[] buffer, int length) {
        try {
            String base64Audio = android.util.Base64.encodeToString(
                    buffer, 0, length, android.util.Base64.NO_WRAP);

            JSONObject message = new JSONObject();
            JSONObject clientContent = new JSONObject();
            JSONArray turns = new JSONArray();
            JSONObject turn = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            JSONObject inlineData = new JSONObject();

            inlineData.put("mimeType", "audio/pcm;rate=16000");
            inlineData.put("data", base64Audio);
            part.put("inlineData", inlineData);
            parts.put(part);
            turn.put("parts", parts);
            turns.put(turn);
            clientContent.put("turns", turns);
            message.put("clientContent", clientContent);

            if (webSocket != null) {
                webSocket.send(message.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "오디오 데이터 전송 실패", e);
        }
    }

    /**
     * 오디오 녹음 중지
     */
    public void stopRecording() {
        isRecording.set(false);

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.d(TAG, "오디오 녹음 중지");
            } catch (Exception e) {
                Log.e(TAG, "오디오 녹음 중지 실패", e);
            }
        }
    }

    /**
     * 연결 종료
     */
    public void disconnect() {
        stopRecording();

        if (webSocket != null) {
            webSocket.close(1000, "사용자 종료");
            webSocket = null;
        }

        isConnected.set(false);
        Log.d(TAG, "연결 종료");
    }

    /**
     * 리소스 정리
     */
    public void destroy() {
        disconnect();

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * 연결 상태 확인
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    /**
     * 녹음 상태 확인
     */
    public boolean isRecording() {
        return isRecording.get();
    }
}