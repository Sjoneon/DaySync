package com.sjoneon.cap;

import android.app.Application;
import android.util.Log;

import com.naver.maps.map.NaverMapSdk;

/**
 * 애플리케이션 클래스
 * 앱 시작 시 필요한 초기화 작업을 수행합니다.
 */
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    // 네이버 지도 클라이언트 ID (콘솔에서 발급받은 ID로 수정 필요)
    private static final String NAVER_MAP_CLIENT_ID = "l4dae8ewvg";

    @Override
    public void onCreate() {
        super.onCreate();

        // 네이버 지도 SDK 초기화 및 클라이언트 ID 설정
        try {
            // NaverMapSdk를 초기화하고 클라이언트 ID 설정
            NaverMapSdk.getInstance(this).setClient(
                    new NaverMapSdk.NaverCloudPlatformClient(NAVER_MAP_CLIENT_ID));

            // 인증 실패 리스너 등록
            NaverMapSdk.getInstance(this).setOnAuthFailedListener(exception -> {
                Log.e(TAG, "네이버 지도 인증 실패: " + exception.getMessage());

                // 에러 메시지 처리
                String errorMessage;
                String errorCode = exception.getMessage(); // 오류 메시지에서 직접 코드 확인

                // 오류 메시지에 특정 코드 포함 여부로 확인
                if (errorCode.contains("401")) {
                    errorMessage = "인증 실패(401): 클라이언트 ID가 잘못되었거나 패키지 이름이 등록되지 않았습니다.";
                    errorMessage += "\n네이버 클라우드 플랫폼 콘솔에서 다음을 확인하세요:";
                    errorMessage += "\n1. 올바른 클라이언트 ID를 사용 중인지";
                    errorMessage += "\n2. 애플리케이션 패키지 이름(com.sjoneon.cap)이 등록되어 있는지";
                } else if (errorCode.contains("429")) {
                    errorMessage = "인증 실패(429): 콘솔에서 Maps 서비스를 선택하지 않았거나 사용 한도가 초과되었습니다.";
                } else if (errorCode.contains("800")) {
                    errorMessage = "인증 실패(800): 클라이언트 ID가 지정되지 않았습니다.";
                } else {
                    errorMessage = "네이버 지도 인증 실패: " + errorCode;
                }

                // 로그에 에러 메시지 기록
                Log.e(TAG, errorMessage);
            });

            Log.d(TAG, "네이버 지도 SDK 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "네이버 지도 SDK 초기화 오류: " + e.getMessage(), e);
        }
    }
}