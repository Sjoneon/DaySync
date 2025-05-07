package com.sjoneon.cap;

import android.app.Application;
import android.util.Log;

/**
 * 애플리케이션 클래스
 * 앱 시작 시 필요한 초기화 작업을 수행합니다.
 */
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // 초기화 작업 수행
            Log.d(TAG, "애플리케이션 초기화 시작");

            // 위치 서비스, 데이터베이스 등 초기화 작업 (향후 구현)
            initializeServices();

            Log.d(TAG, "애플리케이션 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "애플리케이션 초기화 오류: " + e.getMessage(), e);
        }
    }

    /**
     * 앱에서 사용하는 서비스들을 초기화
     */
    private void initializeServices() {
        // 데이터베이스 초기화 (향후 구현)

        // 네트워크 클라이언트 초기화 (향후 구현)

        // 알림 채널 초기화
        initNotificationChannels();
    }

    /**
     * 알림 채널 초기화
     */
    private void initNotificationChannels() {
        // NotificationHelper를 통해 알림 채널 초기화
        new NotificationHelper(this);
    }
}