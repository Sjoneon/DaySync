package com.sjoneon.cap;

import android.content.Context;
import android.util.Log;

import com.naver.maps.map.NaverMapSdk;

/**
 * 네이버 지도 인증 및 설정을 관리하는 헬퍼 클래스 (수정된 버전)
 * Client-Side 지도 표시에 필요한 기능에 집중합니다.
 */
public class NaverMapHelper {

    private static final String TAG = "NaverMapHelper";

    // 네이버 클라우드 플랫폼에서 발급받은 Client ID
    // build.gradle 또는 local.properties 등에서 관리하는 것이 더 안전합니다.
    private static final String NAVER_MAPS_CLIENT_ID = "l4dae8ewvg";

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
     * 앱 시작 시 한 번만 호출되어야 합니다.
     */
    private void initNaverMapSdk() {
        try {
            // 네이버 맵 SDK 클라이언트 ID 설정
            NaverMapSdk.getInstance(context).setClient(
                    new NaverMapSdk.NaverCloudPlatformClient(NAVER_MAPS_CLIENT_ID));

            // 인증 실패 리스너 설정
            NaverMapSdk.getInstance(context).setOnAuthFailedListener(e ->
                    Log.e(TAG, "Naver Maps Auth Failed: " + e.getMessage(), e)
            );

            Log.d(TAG, "NaverMapSdk 초기화 완료. Client ID: " + NAVER_MAPS_CLIENT_ID);

        } catch (Exception e) {
            Log.e(TAG, "NaverMapSdk 초기화 중 예외 발생: " + e.getMessage(), e);
        }
    }
}