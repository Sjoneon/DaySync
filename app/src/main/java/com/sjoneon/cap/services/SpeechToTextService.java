package com.sjoneon.cap.services;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 음성 인식(STT) 서비스
 * Android SpeechRecognizer API를 사용하여 음성을 텍스트로 변환
 */
public class SpeechToTextService {
    private static final String TAG = "SpeechToTextService";

    private Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private SpeechRecognitionListener listener;
    private boolean isListening = false;

    public interface SpeechRecognitionListener {
        void onSpeechResult(String text);
        void onSpeechError(String error);
        void onSpeechStarted();
        void onSpeechEnded();
    }

    public SpeechToTextService(Context context) {
        this.context = context;
        initializeSpeechRecognizer();
    }

    private void initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "음성 인식을 사용할 수 없습니다");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "음성 인식 준비 완료");
                isListening = true;
                if (listener != null) listener.onSpeechStarted();
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "사용자가 말하기 시작함");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "사용자가 말하기 종료함");
                isListening = false;
                if (listener != null) listener.onSpeechEnded();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                String errorMessage = getErrorMessage(error);
                Log.e(TAG, "음성 인식 오류: " + errorMessage);
                if (listener != null) listener.onSpeechError(errorMessage);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.d(TAG, "인식된 텍스트: " + recognizedText);
                    if (listener != null) listener.onSpeechResult(recognizedText);
                } else {
                    Log.w(TAG, "인식된 텍스트가 없습니다");
                    if (listener != null) listener.onSpeechError("음성을 인식하지 못했습니다");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        // 한국어 설정
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                context.getPackageName());

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        // 부분 결과 활성화
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // 무음 감지 타임아웃 설정 (밀리초 단위)
        // 완전 무음으로 판단하는 시간: 5초 (기본값: 2-3초)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);

        // 말이 끝났을 가능성이 있다고 판단하는 시간: 3초 (기본값: 1-2초)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);

        // 최소 음성 입력 시간: 500ms (너무 짧은 소리 무시)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 250L);

        Log.d(TAG, "음성 인식 초기화 완료 - 언어: 한국어 (ko-KR), 무음 타임아웃: 5초");
    }

    public void startListening() {
        if (speechRecognizer == null) initializeSpeechRecognizer();
        if (isListening) {
            Log.w(TAG, "이미 음성 인식 중입니다");
            return;
        }
        Log.d(TAG, "음성 인식 시작 - 한국어 모드");
        speechRecognizer.startListening(recognizerIntent);
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            Log.d(TAG, "음성 인식 중지");
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    public void setListener(SpeechRecognitionListener listener) {
        this.listener = listener;
    }

    public boolean isListening() {
        return isListening;
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        listener = null;
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "오디오 녹음 오류";
            case SpeechRecognizer.ERROR_CLIENT: return "클라이언트 오류";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "권한이 부족합니다";
            case SpeechRecognizer.ERROR_NETWORK: return "네트워크 오류";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "네트워크 시간 초과";
            case SpeechRecognizer.ERROR_NO_MATCH: return "음성을 인식하지 못했습니다";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "음성 인식 사용 중입니다";
            case SpeechRecognizer.ERROR_SERVER: return "서버 오류";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "음성 입력 시간 초과";
            default: return "알 수 없는 오류";
        }
    }
}