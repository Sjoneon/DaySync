package com.sjoneon.cap;

import android.app.Application;
import android.util.Log;

import com.sjoneon.cap.helpers.NotificationHelper;

/**
 * 애플리케이션 클래스
 * 앱 시작 시 필요한 초기화 작업을 수행합니다.
 */

/**
 * 현재 로컬에서 테스트 중이므로 안드로이드 스튜디오 - ApiClient.java에 있는 로컬 ip를 테스트 환경에 맞게 수정해야 정상 작동합니다.
 */

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "애플리케이션 초기화 시작");

        // 기타 서비스 초기화
        initializeServices();

        Log.d(TAG, "애플리케이션 초기화 완료");
    }

    private void initializeServices() {
        // 알림 채널 초기화 등
        new NotificationHelper(this);
    }
}