package com.sjoneon.cap;

import android.content.Context;
import android.util.Log;
import com.naver.maps.map.NaverMapSdk;

/**
 * 네이버 지도 SDK 초기화를 관리하는 헬퍼 클래스
 * API 키는 AndroidManifest.xml에서 자동으로 로드됩니다.
 */
public class NaverMapHelper {

    private static final String TAG = "NaverMapHelper";
    private static boolean isInitialized = false;

    /**
     * 네이버 맵 SDK를 초기화합니다.
     * Application 클래스에서 한 번만 호출하면 됩니다.
     * @param context 애플리케이션 컨텍스트
     */
    public static synchronized void init(Context context) {
        if (isInitialized) {
            Log.d(TAG, "네이버 맵 SDK가 이미 초기화되었습니다.");
            return;
        }

        try {
            Log.d(TAG, "네이버 맵 SDK 초기화 시도...");
            // 클라이언트 ID는 AndroidManifest.xml에 설정된 값을 사용합니다.
            // 'com.naver.maps.map.CLIENT_ID' 메타데이터를 찾아서 자동으로 설정합니다.
            NaverMapSdk.getInstance(context).setClient(
                    new NaverMapSdk.NaverCloudPlatformClient(getNaverApiClientId(context))
            );
            isInitialized = true;
            Log.d(TAG, "네이버 맵 SDK 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "네이버 맵 SDK 초기화 중 오류 발생", e);
        }
    }

    /**
     * AndroidManifest.xml에서 네이버 API 클라이언트 ID를 가져옵니다.
     * @param context 컨텍스트
     * @return 클라이언트 ID 문자열
     */
    private static String getNaverApiClientId(Context context) {
        try {
            // AndroidManifest.xml에서 메타데이터를 직접 읽어옵니다.
            android.content.pm.ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
            if (ai.metaData != null) {
                return ai.metaData.getString("com.naver.maps.map.CLIENT_ID");
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            Log.e(TAG, "AndroidManifest.xml에서 클라이언트 ID를 찾을 수 없습니다.", e);
        }
        return null; // 또는 기본값
    }
}