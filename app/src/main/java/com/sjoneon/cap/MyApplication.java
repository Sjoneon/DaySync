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
        Log.d(TAG, "애플리케이션 초기화 시작");

        // ★★★ NaverMapHelper.getInstance(this) 호출을 제거합니다. ★★★
        // SDK가 AndroidManifest.xml을 통해 자동으로 초기화되도록 합니다.

        // 기타 서비스 초기화
        initializeServices();

        Log.d(TAG, "애플리케이션 초기화 완료");
    }

    private void initializeServices() {
        // 알림 채널 초기화 등
        new NotificationHelper(this);
    }
}