package com.sjoneon.cap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;

/**
 * 네이버 지도 관련 기능을 중앙에서 관리하는 클래스 (오류 수정)
 */
public class MapManager implements OnMapReadyCallback {

    private static final String TAG = "MapManager";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private final Fragment fragment;
    private final Context context;
    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;

    /**
     * 생성자
     * @param fragment MapManager를 사용하는 프래그먼트
     * @param mapView  프래그먼트의 MapView 객체
     */
    public MapManager(Fragment fragment, MapView mapView) {
        this.fragment = fragment;
        this.context = fragment.requireContext();
        this.mapView = mapView;
        this.locationSource = new FusedLocationSource(fragment, LOCATION_PERMISSION_REQUEST_CODE);
    }

    /**
     * MapManager 초기화
     * @param savedInstanceState Bundle 객체
     */
    public void onCreate(Bundle savedInstanceState) {
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        Log.d(TAG, "onMapReady: 네이버 맵이 준비되었습니다.");

        setupMapUi();
        naverMap.setLocationSource(locationSource);
        checkAndRequestLocationPermission();
    }

    /**
     * 지도 UI 설정
     */
    private void setupMapUi() {
        if (naverMap == null) return;
        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
        naverMap.getUiSettings().setLocationButtonEnabled(true);
    }

    /**
     * 위치 권한 확인 및 요청
     */
    public void checkAndRequestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fragment.requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            startLocationTracking();
        }
    }

    /**
     * 위치 추적 시작
     */
    public void startLocationTracking() {
        if (naverMap == null) return;
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
    }

    /**
     * 권한 요청 결과 처리
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (naverMap != null && locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
            } else {
                Toast.makeText(context, "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 지도에 마커 추가
     * @param latLng 마커를 추가할 위치
     * @param title  마커에 표시할 캡션 텍스트
     */
    public void addMarker(LatLng latLng, String title) {
        if (naverMap == null) return;
        Marker marker = new Marker();
        marker.setPosition(latLng);
        marker.setCaptionText(title); // .setCaption(title) -> .setCaptionText(title) 로 수정
        marker.setMap(naverMap);
    }

    /**
     * 특정 위치로 카메라 이동
     * @param latLng 이동할 위치
     */
    public void moveCamera(LatLng latLng) {
        if (naverMap == null) return;
        naverMap.moveCamera(CameraUpdate.scrollTo(latLng));
    }


    // MapView 생명주기 메서드
    public void onStart() {
        if (mapView != null) mapView.onStart();
    }

    public void onResume() {
        if (mapView != null) mapView.onResume();
    }

    public void onPause() {
        if (mapView != null) mapView.onPause();
    }

    public void onStop() {
        if (mapView != null) mapView.onStop();
    }

    public void onDestroyView() {
        if (mapView != null) mapView.onDestroy();
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    public void onLowMemory() {
        if (mapView != null) mapView.onLowMemory();
    }
}