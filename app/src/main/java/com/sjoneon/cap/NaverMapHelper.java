package com.sjoneon.cap;

import android.content.Context;
import android.util.Log;

import com.naver.maps.map.NaverMap;
import com.naver.maps.map.NaverMapSdk;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.util.FusedLocationSource;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 네이버 지도 인증 및 설정을 관리하는 헬퍼 클래스
 */
public class NaverMapHelper {

    private static final String TAG = "NaverMapHelper";

    // 네이버 맵 API 키 (값은 문자열 리소스로 이동해야 함)
    private static final String CLIENT_ID = "l4dae8ewvg";
    private static final String CLIENT_SECRET = "teM3IEaDFmhkSyYRpm3rU655tnaLXiaOFBMLB83X";

    private static NaverMapHelper instance;
    private OkHttpClient httpClient;
    private Context context;

    /**
     * 싱글톤 인스턴스를 가져오는 메서드
     * @param context 앱 컨텍스트
     * @return NaverMapHelper 인스턴스
     */
    public static synchronized NaverMapHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NaverMapHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 생성자
     * @param context 앱 컨텍스트
     */
    private NaverMapHelper(Context context) {
        this.context = context;
        initHttpClient();
        initNaverMapSdk();
    }

    /**
     * HTTP 클라이언트 초기화
     * 네이버 API 요청 시 필요한 인증 헤더를 자동으로 추가
     */
    private void initHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request originalRequest = chain.request();

                        // 네이버 맵 API 인증 헤더 추가
                        Request.Builder requestBuilder = originalRequest.newBuilder()
                                .header("X-NCP-APIGW-API-KEY-ID", CLIENT_ID)
                                .header("X-NCP-APIGW-API-KEY", CLIENT_SECRET)
                                .method(originalRequest.method(), originalRequest.body());

                        Request request = requestBuilder.build();
                        return chain.proceed(request);
                    }
                })
                .build();
    }

    /**
     * 네이버 맵 SDK 초기화
     */
    private void initNaverMapSdk() {
        try {
            // 네이버 맵 SDK 초기화 - 클라이언트 ID 등록
            NaverMapSdk.getInstance(context).setClient(
                    new NaverMapSdk.NaverCloudPlatformClient(CLIENT_ID));

            // 인증 실패 리스너 설정
            NaverMapSdk.getInstance(context).setOnAuthFailedListener(e -> {
                Log.e(TAG, "네이버 맵 인증 실패: " + e.getMessage());
                // 인증 실패 시 재시도 또는 다른 조치를 취할 수 있음
            });

            Log.d(TAG, "네이버 맵 SDK 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "네이버 맵 SDK 초기화 오류: " + e.getMessage(), e);
        }
    }

    /**
     * HTTP 클라이언트 제공 메서드
     * @return 인증 정보가 설정된 OkHttpClient 인스턴스
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }
}