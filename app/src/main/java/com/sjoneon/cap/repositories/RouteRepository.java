package com.sjoneon.cap.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sjoneon.cap.fragments.RouteFragment;
import com.sjoneon.cap.models.api.RouteResponse;
import com.sjoneon.cap.models.api.RouteSearchRequest;
import com.sjoneon.cap.models.api.RouteSearchResponse;
import com.sjoneon.cap.models.api.RouteSaveRequest;
import com.sjoneon.cap.services.DaySyncApiService;
import com.sjoneon.cap.utils.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 경로 데이터 관리를 위한 Repository
 *
 * 책임:
 * 1. 서버 API와 통신
 * 2. 로컬 캐싱 (SharedPreferences 또는 Room)
 * 3. 데이터 동기화
 * 4. 오프라인 지원
 */
public class RouteRepository {

    private static final String TAG = "RouteRepository";
    private static RouteRepository instance;

    private final DaySyncApiService apiService;
    private final ExecutorService executorService;
    private final Context context;

    private RouteRepository(Context context) {
        this.context = context.getApplicationContext();
        this.apiService = ApiClient.getDaySyncApiService();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public static synchronized RouteRepository getInstance(Context context) {
        if (instance == null) {
            instance = new RouteRepository(context);
        }
        return instance;
    }

    /**
     * 경로 검색 결과를 서버에 저장
     */
    public void saveRouteToServer(
            double startLat, double startLng,
            double endLat, double endLng,
            List<RouteFragment.RouteInfo> routes,
            String userUuid,
            @NonNull SaveRouteCallback callback
    ) {
        executorService.execute(() -> {
            try {
                // RouteInfo를 API 형식으로 변환
                List<RouteSaveRequest.RouteDataItem> routeDataItems = new ArrayList<>();
                for (RouteFragment.RouteInfo route : routes) {
                    routeDataItems.add(RouteSaveRequest.fromRouteInfo(route));
                }

                RouteSaveRequest request = new RouteSaveRequest(
                        startLat, startLng, endLat, endLng,
                        routeDataItems, userUuid
                );

                Call<RouteResponse> call = apiService.saveRoute(request);
                call.enqueue(new Callback<RouteResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<RouteResponse> call,
                                           @NonNull Response<RouteResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.i(TAG, "경로 저장 성공: ID=" + response.body().getId());
                            callback.onSuccess(response.body());
                        } else {
                            String error = "서버 응답 오류: " + response.code();
                            Log.e(TAG, error);
                            callback.onFailure(error);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<RouteResponse> call, @NonNull Throwable t) {
                        String error = "네트워크 오류: " + t.getMessage();
                        Log.e(TAG, error, t);
                        callback.onFailure(error);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "경로 저장 중 예외 발생", e);
                callback.onFailure("경로 저장 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 서버에서 캐시된 경로 검색
     */
    public void searchRouteFromServer(
            double startLat, double startLng,
            double endLat, double endLng,
            @NonNull SearchRouteCallback callback
    ) {
        RouteSearchRequest request = new RouteSearchRequest(
                startLat, startLng, endLat, endLng
        );

        Call<RouteSearchResponse> call = apiService.searchRoute(request);
        call.enqueue(new Callback<RouteSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<RouteSearchResponse> call,
                                   @NonNull Response<RouteSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RouteSearchResponse searchResponse = response.body();
                    if (searchResponse.isFound()) {
                        Log.i(TAG, "캐시된 경로 발견");
                        callback.onRouteFound(searchResponse.getRoute());
                    } else {
                        Log.i(TAG, "캐시된 경로 없음");
                        callback.onRouteNotFound();
                    }
                } else {
                    Log.e(TAG, "경로 검색 실패: " + response.code());
                    callback.onFailure("서버 응답 오류: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<RouteSearchResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "경로 검색 네트워크 오류", t);
                callback.onFailure("네트워크 오류: " + t.getMessage());
            }
        });
    }

    /**
     * 로컬 SharedPreferences에 경로 저장 (백업)
     */
    public void saveRouteToLocal(
            String key,
            double startLat, double startLng,
            double endLat, double endLng
    ) {
        SharedPreferences prefs = context.getSharedPreferences("RouteCache", Context.MODE_PRIVATE);
        prefs.edit()
                .putFloat(key + "_start_lat", (float) startLat)
                .putFloat(key + "_start_lng", (float) startLng)
                .putFloat(key + "_end_lat", (float) endLat)
                .putFloat(key + "_end_lng", (float) endLng)
                .apply();
    }

    /**
     * 로컬에서 최근 검색 좌표 가져오기
     */
    public double[] getLastSearchCoordinates(String key) {
        SharedPreferences prefs = context.getSharedPreferences("RouteCache", Context.MODE_PRIVATE);
        float startLat = prefs.getFloat(key + "_start_lat", 0);
        float startLng = prefs.getFloat(key + "_start_lng", 0);
        float endLat = prefs.getFloat(key + "_end_lat", 0);
        float endLng = prefs.getFloat(key + "_end_lng", 0);

        if (startLat == 0 && startLng == 0 && endLat == 0 && endLng == 0) {
            return null;
        }

        return new double[]{startLat, startLng, endLat, endLng};
    }

    // 콜백 인터페이스들
    public interface SaveRouteCallback {
        void onSuccess(RouteResponse response);
        void onFailure(String error);
    }

    public interface SearchRouteCallback {
        void onRouteFound(RouteResponse route);
        void onRouteNotFound();
        void onFailure(String error);
    }
}