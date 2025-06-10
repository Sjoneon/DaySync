package com.sjoneon.cap;

import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "애플리케이션 초기화 시작");

        // 네이버 맵 SDK 초기화
        NaverMapHelper.init(this);

        // 기타 서비스 초기화
        initializeServices();

        Log.d(TAG, "애플리케이션 초기화 완료");
    }

    private void initializeServices() {
        // 알림 채널 초기화 등
        new NotificationHelper(this);
    }
}