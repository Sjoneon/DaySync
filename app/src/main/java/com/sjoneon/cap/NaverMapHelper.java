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
     * AndroidManifest.xml에 설정된 클라이언트 ID를 자동으로 읽어 초기화합니다.
     */
    private void initNaverMapSdk() {
        try {
            // setClient를 직접 호출할 필요 없이,
            // AndroidManifest에 설정된 값을 SDK가 자동으로 읽습니다.
            // 따라서 해당 부분을 제거합니다.

            // 인증 실패 리스너 설정
            NaverMapSdk.getInstance(context).setOnAuthFailedListener(e ->
                    Log.e(TAG, "Naver Maps Auth Failed: " + e.getMessage(), e)
            );

            Log.d(TAG, "NaverMapSdk 초기화 완료. (AndroidManifest.xml의 CLIENT_ID 사용)");

        } catch (Exception e) {
            Log.e(TAG, "NaverMapSdk 초기화 중 예외 발생: " + e.getMessage(), e);
        }
    }
}