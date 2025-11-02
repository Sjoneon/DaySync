package com.sjoneon.cap.viewmodels;

import android.app.Application;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sjoneon.cap.fragments.RouteFragment;
import com.sjoneon.cap.models.api.RouteResponse;
import com.sjoneon.cap.repositories.RouteRepository;
import com.sjoneon.cap.models.api.RouteSaveRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 경로 정보를 관리하는 ViewModel
 *
 * 책임:
 * 1. Fragment 간 데이터 공유
 * 2. Activity 생명주기 동안 데이터 유지
 * 3. Repository와 UI 연결
 */
public class RouteViewModel extends AndroidViewModel {

    private static final String TAG = "RouteViewModel";

    private final RouteRepository repository;

    // 경로 목록
    private final MutableLiveData<List<RouteFragment.RouteInfo>> routeList =
            new MutableLiveData<>(new ArrayList<>());

    // 위치 정보
    private final MutableLiveData<String> startLocationText = new MutableLiveData<>("");
    private final MutableLiveData<String> endLocationText = new MutableLiveData<>("");
    private Location startLocation;
    private Location endLocation;

    // 로딩 상태
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // 에러 메시지
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // 서버 동기화 상태
    private final MutableLiveData<Boolean> isSyncedWithServer = new MutableLiveData<>(false);

    public RouteViewModel(@NonNull Application application) {
        super(application);
        this.repository = RouteRepository.getInstance(application);
    }

    // LiveData Getters
    public LiveData<List<RouteFragment.RouteInfo>> getRouteList() {
        return routeList;
    }

    public LiveData<String> getStartLocationText() {
        return startLocationText;
    }

    public LiveData<String> getEndLocationText() {
        return endLocationText;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsSyncedWithServer() {
        return isSyncedWithServer;
    }

    /**
     * 경로 목록 업데이트 및 서버 저장
     */
    public void updateRouteList(List<RouteFragment.RouteInfo> routes, String userUuid) {
        routeList.setValue(routes);

        // 백그라운드에서 서버에 저장
        if (startLocation != null && endLocation != null && !routes.isEmpty()) {
            saveRoutesToServer(
                    startLocation.getLatitude(),
                    startLocation.getLongitude(),
                    endLocation.getLatitude(),
                    endLocation.getLongitude(),
                    routes,
                    userUuid
            );
        }
    }

    /**
     * 서버에 경로 저장
     */
    private void saveRoutesToServer(
            double startLat, double startLng,
            double endLat, double endLng,
            List<RouteFragment.RouteInfo> routes,
            String userUuid
    ) {
        repository.saveRouteToServer(
                startLat, startLng, endLat, endLng,
                routes, userUuid,
                new RouteRepository.SaveRouteCallback() {
                    @Override
                    public void onSuccess(RouteResponse response) {
                        Log.i(TAG, "서버 동기화 완료");
                        isSyncedWithServer.postValue(true);
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.w(TAG, "서버 동기화 실패 (계속 진행): " + error);
                        // 서버 저장 실패해도 로컬에서는 정상 작동
                        isSyncedWithServer.postValue(false);
                    }
                }
        );

        // 로컬에도 좌표 저장 (백업)
        repository.saveRouteToLocal("last_search", startLat, startLng, endLat, endLng);
    }

    /**
     * 서버에서 캐시된 경로 검색
     */
    public void searchCachedRoute(double startLat, double startLng,
                                  double endLat, double endLng) {
        isLoading.setValue(true);

        repository.searchRouteFromServer(
                startLat, startLng, endLat, endLng,
                new RouteRepository.SearchRouteCallback() {
                    @Override
                    public void onRouteFound(RouteResponse route) {
                        // 서버에서 받은 경로 데이터를 RouteInfo로 변환
                        List<RouteFragment.RouteInfo> routes = convertToRouteInfoList(route);
                        routeList.postValue(routes);
                        isLoading.postValue(false);
                        Log.i(TAG, "캐시된 경로 로드 완료");
                    }

                    @Override
                    public void onRouteNotFound() {
                        isLoading.postValue(false);
                        Log.i(TAG, "캐시된 경로 없음 - 새로 검색 필요");
                    }

                    @Override
                    public void onFailure(String error) {
                        isLoading.postValue(false);
                        errorMessage.postValue(error);
                        Log.e(TAG, "경로 검색 실패: " + error);
                    }
                }
        );
    }

    /**
     * RouteResponse를 RouteInfo 리스트로 변환
     */
    private List<RouteFragment.RouteInfo> convertToRouteInfoList(RouteResponse response) {
        List<RouteFragment.RouteInfo> routes = new ArrayList<>();

        if (response.getRouteData() != null) {
            for (RouteSaveRequest.RouteDataItem item : response.getRouteData()) {
                RouteFragment.RouteInfo routeInfo = new RouteFragment.RouteInfo(
                        item.getType(),
                        item.getDuration(),
                        item.getBusWaitTime(),
                        item.getBusNumber(),
                        item.getStartStopName(),
                        item.getEndStopName()
                );

                routeInfo.setBusRideTime(item.getBusRideTime());
                routeInfo.setWalkingTimeToStartStop(item.getWalkingTimeToStartStop());
                routeInfo.setWalkingTimeToDestination(item.getWalkingTimeToDestination());
                routeInfo.setDirectionInfo(item.getDirectionInfo());
                routeInfo.setStartStopLat(item.getStartStopLat());
                routeInfo.setStartStopLng(item.getStartStopLng());
                routeInfo.setEndStopLat(item.getEndStopLat());
                routeInfo.setEndStopLng(item.getEndStopLng());
                routeInfo.setDestinationLat(item.getDestinationLat());
                routeInfo.setDestinationLng(item.getDestinationLng());

                routes.add(routeInfo);
            }
        }

        return routes;
    }

    // Setters
    public void setStartLocationText(String text) {
        startLocationText.setValue(text);
    }

    public void setEndLocationText(String text) {
        endLocationText.setValue(text);
    }

    public void setStartLocation(Location location) {
        this.startLocation = location;
    }

    public void setEndLocation(Location location) {
        this.endLocation = location;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public Location getEndLocation() {
        return endLocation;
    }

    /**
     * 모든 데이터 초기화
     */
    public void clearData() {
        routeList.setValue(new ArrayList<>());
        startLocationText.setValue("");
        endLocationText.setValue("");
        startLocation = null;
        endLocation = null;
        isSyncedWithServer.setValue(false);
    }
}