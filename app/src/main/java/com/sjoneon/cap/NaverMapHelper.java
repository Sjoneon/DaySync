package com.sjoneon.cap;

import android.content.Context;
import android.util.Log;

import com.naver.maps.map.NaverMapSdk;

/**
 * 네이버 지도 인증 및 설정을 관리하는 헬퍼 클래스
 * 네이버 클라우드 플랫폼 API 사용 버전
 */
public class NaverMapHelper {

    private static final String TAG = "NaverMapHelper";

    private static NaverMapHelper instance;
    private final Context context;

    /**
     * 싱글톤 인스턴스를 가져오는 메서드
     * @param context 애플리케이션 컨텍스트
     * @return NaverMapHelper 인스턴스
     */
    public static synchronized NaverMapHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NaverMapHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 생성자 (private)
     * @param context 애플리케이션 컨텍스트
     */
    private NaverMapHelper(Context context) {
        this.context = context;
        initNaverMapSdk();
    }

    /**
     * 네이버 맵 SDK 초기화
     * 네이버 클라우드 플랫폼 API 방식 사용
     */
    private void initNaverMapSdk() {
        try {
            // 네이버 클라우드 플랫폼 API 키 설정
            // AndroidManifest.xml에 설정된 CLIENT_ID를 사용하지만
            // 실제로는 X-NCP-APIGW-API-KEY-ID 값을 넣어야 함

            // 인증 실패 리스너 설정
            NaverMapSdk.getInstance(context).setOnAuthFailedListener(e -> {
                Log.e(TAG, "Naver Maps Auth Failed: " + e.getMessage(), e);
                // 401 에러의 경우 API 키 문제일 가능성이 높음
                if (e.getMessage() != null && e.getMessage().contains("401")) {
                    Log.e(TAG, "API 키가 올바르지 않거나 서비스 환경 설정이 잘못되었습니다.");
                    Log.e(TAG, "네이버 클라우드 플랫폼에서 다음을 확인하세요:");
                    Log.e(TAG, "1. Application 이름이 'DaySync'로 되어 있는지");
                    Log.e(TAG, "2. Android 패키지명이 'com.sjoneon.cap'으로 등록되어 있는지");
                    Log.e(TAG, "3. Web 서비스 URL도 등록되어 있는지 (개발용으로 *)");
                }
            });

            Log.d(TAG, "NaverMapSdk 초기화 완료");
            Log.d(TAG, "사용 중인 Client ID: " + context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(),
                            android.content.pm.PackageManager.GET_META_DATA)
                    .metaData.getString("com.naver.maps.map.CLIENT_ID"));

        } catch (Exception e) {
            Log.e(TAG, "NaverMapSdk 초기화 중 예외 발생: " + e.getMessage(), e);
        }
    }
}