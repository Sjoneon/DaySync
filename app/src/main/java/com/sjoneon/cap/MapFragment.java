package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;

/**
 * 네이버 지도를 표시하고 현재 위치 추적, 경로 표시 기능을 제공하는 프래그먼트
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 지도 관련 변수
    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    // 마커 및 경로 관련 변수
    private Marker startMarker;
    private Marker endMarker;
    private LatLng currentLocation;

    // 경로 정보 (RouteFragment에서 전달받을 수 있음)
    private String startLocationName;
    private String endLocationName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 위치 서비스 초기화
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // Bundle에서 경로 정보 받기 (RouteFragment에서 전달된 경우)
        Bundle args = getArguments();
        if (args != null) {
            startLocationName = args.getString("start_location", "");
            endLocationName = args.getString("end_location", "");
            Log.d(TAG, "경로 정보 수신: " + startLocationName + " → " + endLocationName);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // MapView를 생성하고 반환
        mapView = new MapView(requireActivity());
        return mapView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // MapView 초기화
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // 위치 업데이트 콜백 설정
        setupLocationCallback();
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        Log.d(TAG, "지도 준비 완료");

        // 위치 소스 설정
        naverMap.setLocationSource(locationSource);

        // 위치 권한 확인
        if (hasLocationPermission()) {
            enableLocationFeatures();
        } else {
            requestLocationPermission();
        }

        // 지도 UI 설정
        setupMapUI();

        // 경로 정보가 있으면 마커 표시
        if (startLocationName != null && !startLocationName.isEmpty() &&
                endLocationName != null && !endLocationName.isEmpty()) {
            showRouteInfo();
        }
    }

    /**
     * 위치 권한이 있는지 확인하는 메서드
     */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 위치 권한을 요청하는 메서드
     */
    private void requestLocationPermission() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    /**
     * 위치 기능을 활성화하는 메서드
     */
    private void enableLocationFeatures() {
        if (naverMap == null) return;

        // 현재 위치 오버레이 활성화 (수정된 부분)
        try {
            naverMap.getLocationOverlay().setVisible(true);
        } catch (Exception e) {
            Log.e(TAG, "위치 오버레이 활성화 실패", e);
        }

        // 현재 위치 가져오기 및 추적 시작
        getCurrentLocationAndStartTracking();
    }

    /**
     * 현재 위치를 가져오고 위치 추적을 시작하는 메서드
     */
    private void getCurrentLocationAndStartTracking() {
        if (!hasLocationPermission()) return;

        try {
            // 마지막 알려진 위치 가져오기
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                            // 카메라를 현재 위치로 이동 (수정된 부분)
                            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(currentLocation);
                            naverMap.moveCamera(cameraUpdate);

                            Log.d(TAG, "현재 위치: " + location.getLatitude() + ", " + location.getLongitude());

                            // 위치 오버레이 표시
                            LocationOverlay locationOverlay = naverMap.getLocationOverlay();
                            locationOverlay.setVisible(true);
                            locationOverlay.setPosition(currentLocation);
                        } else {
                            Log.w(TAG, "마지막 위치 정보를 가져올 수 없음");
                            // 기본 위치 (청주시)로 설정
                            setDefaultLocation();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "위치 정보 가져오기 실패", e);
                        setDefaultLocation();
                    });

            // 실시간 위치 업데이트 시작
            startLocationUpdates();

        } catch (SecurityException e) {
            Log.e(TAG, "위치 권한 오류", e);
        }
    }

    /**
     * 기본 위치(청주시)를 설정하는 메서드
     */
    private void setDefaultLocation() {
        // 청주시 좌표
        LatLng cheongju = new LatLng(36.6424, 127.4890);
        currentLocation = cheongju;

        // 수정된 부분 - 애니메이션 없이 카메라 이동
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(cheongju);
        naverMap.moveCamera(cameraUpdate);

        Log.d(TAG, "기본 위치(청주시)로 설정됨");
    }

    /**
     * 위치 업데이트 콜백을 설정하는 메서드
     */
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        LatLng newLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        currentLocation = newLocation;

                        // 위치 오버레이 업데이트
                        if (naverMap != null) {
                            LocationOverlay locationOverlay = naverMap.getLocationOverlay();
                            locationOverlay.setPosition(newLocation);
                        }

                        Log.d(TAG, "위치 업데이트: " + location.getLatitude() + ", " + location.getLongitude());
                    }
                }
            }
        };
    }

    /**
     * 실시간 위치 업데이트를 시작하는 메서드
     */
    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(15000)
                .build();

        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                    locationCallback, requireActivity().getMainLooper());
            Log.d(TAG, "위치 업데이트 시작됨");
        } catch (SecurityException e) {
            Log.e(TAG, "위치 업데이트 시작 실패", e);
        }
    }

    /**
     * 위치 업데이트를 중지하는 메서드
     */
    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "위치 업데이트 중지됨");
        }
    }

    /**
     * 지도 UI를 설정하는 메서드
     */
    private void setupMapUI() {
        if (naverMap == null) return;

        // 줌 컨트롤 표시
        naverMap.getUiSettings().setZoomControlEnabled(true);

        // 현재 위치 버튼 표시
        naverMap.getUiSettings().setLocationButtonEnabled(hasLocationPermission());

        // 나침반 표시
        naverMap.getUiSettings().setCompassEnabled(true);

        // 축척 바 표시
        naverMap.getUiSettings().setScaleBarEnabled(true);

        // 지도 타입을 기본으로 설정
        naverMap.setMapType(NaverMap.MapType.Basic);

        Log.d(TAG, "지도 UI 설정 완료");
    }

    /**
     * 경로 정보를 지도에 표시하는 메서드
     */
    private void showRouteInfo() {
        if (naverMap == null || startLocationName == null || endLocationName == null) return;

        // 실제로는 Geocoding API를 사용해서 주소를 좌표로 변환해야 함
        // 여기서는 더미 좌표 사용
        showDummyRouteMarkers();
    }

    /**
     * 더미 경로 마커를 표시하는 메서드 (실제로는 Geocoding 필요)
     */
    private void showDummyRouteMarkers() {
        // 청주 근처의 더미 좌표들
        LatLng startPoint = new LatLng(36.6424, 127.4890); // 청주역 근처
        LatLng endPoint = new LatLng(36.6184, 127.4936);   // 성안길 근처

        // 출발지 마커
        if (startMarker != null) {
            startMarker.setMap(null);
        }
        startMarker = new Marker();
        startMarker.setPosition(startPoint);
        startMarker.setCaptionText("출발: " + startLocationName);
        startMarker.setIconTintColor(0xFF00FF00); // 녹색
        startMarker.setMap(naverMap);

        // 도착지 마커
        if (endMarker != null) {
            endMarker.setMap(null);
        }
        endMarker = new Marker();
        endMarker.setPosition(endPoint);
        endMarker.setCaptionText("도착: " + endLocationName);
        endMarker.setIconTintColor(0xFFFF0000); // 빨간색
        endMarker.setMap(naverMap);

        // 카메라를 출발지와 도착지가 모두 보이도록 조정 (수정된 부분)
        try {
            CameraUpdate cameraUpdate = CameraUpdate.fitBounds(
                    com.naver.maps.geometry.LatLngBounds.from(startPoint, endPoint),
                    100 // 패딩
            );
            naverMap.moveCamera(cameraUpdate);
        } catch (Exception e) {
            Log.e(TAG, "카메라 이동 실패", e);
            // 실패하면 시작점으로 이동
            naverMap.moveCamera(CameraUpdate.scrollTo(startPoint));
        }

        Log.d(TAG, "경로 마커 표시 완료");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                // 권한이 거부된 경우
                Toast.makeText(getContext(), getString(R.string.location_permission_denied),
                        Toast.LENGTH_LONG).show();
                // 위치 추적 모드 해제는 제거 (해당 API가 없는 것 같음)
            } else {
                // 권한이 허용된 경우
                enableLocationFeatures();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // MapView 생명주기 메서드들
    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();

        // 위치 권한이 있으면 위치 업데이트 재시작
        if (hasLocationPermission()) {
            startLocationUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();

        // 위치 업데이트 중지 (배터리 절약)
        stopLocationUpdates();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 위치 업데이트 완전 중지
        stopLocationUpdates();

        // 마커들 정리
        if (startMarker != null) {
            startMarker.setMap(null);
        }
        if (endMarker != null) {
            endMarker.setMap(null);
        }

        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}