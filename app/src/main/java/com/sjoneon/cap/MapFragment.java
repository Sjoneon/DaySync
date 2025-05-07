package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.InfoWindow;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 네이버 지도 API를 이용한 지도 및 경로 표시 프래그먼트
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 위치 권한
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    // 위치 변경 감지 최소 거리 (미터 단위)
    private static final float MIN_LOCATION_DISTANCE = 3.0f; // 3미터

    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;
    private FusedLocationProviderClient fusedLocationClient;

    // UI 요소
    private EditText editStartLocation;
    private EditText editDestination;
    private Button buttonSearchRoute;

    // 경로 표시용 객체
    private Marker startMarker;
    private Marker endMarker;
    private PathOverlay pathOverlay;
    private InfoWindow infoWindow;

    // 좌표 정보
    private LatLng startLatLng;
    private LatLng endLatLng;

    // 마지막 위치 저장용 (변경 감지에 사용)
    private Location lastLocation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 레이아웃 인플레이션
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // UI 요소 초기화
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editDestination = view.findViewById(R.id.editDestination);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);

        // 지도 뷰 초기화
        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // 위치 소스 초기화 (액티비티 대신 프래그먼트 전달)
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // 경로 검색 버튼 클릭 리스너 설정
        buttonSearchRoute.setOnClickListener(v -> searchRoute());

        // 마커 초기화
        startMarker = new Marker();
        endMarker = new Marker();
        pathOverlay = new PathOverlay();
        infoWindow = new InfoWindow();

        return view;
    }

    /**
     * 지도가 준비되었을 때 호출되는 콜백 메서드
     */
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        // 지도 초기 설정
        naverMap.setLocationSource(locationSource);

        try {
            // 위치 추적 모드 설정 (예외 가능성이 있으므로 try-catch로 감싸기)
            naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

            // 위치 변경 리스너 설정
            naverMap.addOnLocationChangeListener(location -> {
                // 이전 위치가 없거나 최소 거리 이상 변경된 경우에만 업데이트
                if (isSignificantLocationChange(location)) {
                    Log.d(TAG, "위치 변경: " + location.getLatitude() + ", " + location.getLongitude());
                    // 위치가 변경되면 현재 위치 업데이트
                    updateCurrentLocation(location);
                    // 현재 위치를 마지막 위치로 저장
                    lastLocation = location;
                }
            });

            // 현재 위치 표시 활성화
            naverMap.getUiSettings().setLocationButtonEnabled(true);

            // 한국 기준으로 초기 카메라 위치 설정 (청주)
            LatLng defaultLocation = new LatLng(36.6357, 127.4912); // 청주 시청
            naverMap.moveCamera(CameraUpdate.scrollTo(defaultLocation));

            // UI 설정
            naverMap.getUiSettings().setZoomControlEnabled(true);
            naverMap.getUiSettings().setCompassEnabled(true);
            naverMap.getUiSettings().setScaleBarEnabled(true);

            // 경로 오버레이 스타일 설정
            pathOverlay.setColor(Color.BLUE);
            pathOverlay.setWidth(15);

            Log.d(TAG, "네이버 지도 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "지도 설정 오류: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "지도 설정 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        // 현재 위치 가져오기
        getCurrentLocation();
    }

    /**
     * 위치 변경이 유의미한지 확인 (3m 이상 변경되었는지)
     * @param newLocation 새로운 위치
     * @return 유의미한 변경인지 여부
     */
    private boolean isSignificantLocationChange(Location newLocation) {
        // 첫 위치인 경우
        if (lastLocation == null) {
            return true;
        }

        // 이전 위치와 새 위치 간의 거리 계산 (미터 단위)
        float distance = lastLocation.distanceTo(newLocation);

        // 설정된 최소 거리 이상 변경된 경우에만 true 반환
        return distance >= MIN_LOCATION_DISTANCE;
    }

    /**
     * 현재 위치 정보 업데이트
     */
    private void updateCurrentLocation(Location location) {
        if (location != null) {
            // 현재 위치를 시작 위치로 설정
            startLatLng = new LatLng(location.getLatitude(), location.getLongitude());

            // 시작 마커 설정
            if (startMarker != null) {
                startMarker.setPosition(startLatLng);
                startMarker.setMap(naverMap);
            }

            // 지도를 현재 위치로 이동
            naverMap.moveCamera(CameraUpdate.scrollTo(startLatLng));

            // 위치 정보 로깅
            Log.d(TAG, "현재 위치 업데이트: " + startLatLng.latitude + ", " + startLatLng.longitude);

            // 좌표 표시
            String locationStr = String.format("위도: %.6f, 경도: %.6f",
                    startLatLng.latitude, startLatLng.longitude);
            editStartLocation.setText(locationStr);

            // 마커 설정
            startMarker.setPosition(startLatLng);
            startMarker.setCaptionText("출발");
            startMarker.setMap(naverMap);
        }
    }

    /**
     * 현재 위치 가져오기
     */
    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                // 현재 위치로 카메라 이동
                                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                Log.d(TAG, "현재 위치: " + currentLatLng.latitude + ", " + currentLatLng.longitude);

                                // 카메라 이동
                                CameraPosition cameraPosition = new CameraPosition(currentLatLng, 15);
                                naverMap.setCameraPosition(cameraPosition);

                                // 시작 위치 설정
                                startLatLng = currentLatLng;
                                lastLocation = location; // 마지막 위치 설정

                                // 시작 마커 설정
                                startMarker.setPosition(startLatLng);
                                startMarker.setMap(naverMap);

                                // 좌표 표시
                                String locationStr = String.format("위도: %.6f, 경도: %.6f",
                                        currentLatLng.latitude, currentLatLng.longitude);
                                editStartLocation.setText(locationStr);

                                // 마커 설정
                                startMarker.setPosition(startLatLng);
                                startMarker.setCaptionText("출발");
                                startMarker.setMap(naverMap);
                            } else {
                                Log.w(TAG, "위치 정보를 가져올 수 없습니다");
                                // 기본 위치로 설정 (청주)
                                LatLng defaultLocation = new LatLng(36.6357, 127.4912); // 청주 시청
                                CameraPosition cameraPosition = new CameraPosition(defaultLocation, 15);
                                naverMap.setCameraPosition(cameraPosition);

                                // 기본 위치 메시지
                                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없어 기본 위치(청주)로 설정됩니다", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "위치 정보 가져오기 실패: " + e.getMessage(), e);
                        Toast.makeText(requireContext(), "위치 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();

                        // 기본 위치 설정 (청주)
                        LatLng defaultLocation = new LatLng(36.6357, 127.4912); // 청주 시청
                        CameraPosition cameraPosition = new CameraPosition(defaultLocation, 15);
                        naverMap.setCameraPosition(cameraPosition);
                    });
        } catch (Exception e) {
            Log.e(TAG, "위치 정보 가져오기 오류: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "위치 정보 가져오기 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 경로 검색 메서드 (간소화)
     */
    private void searchRoute() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editDestination.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.empty_location_error), Toast.LENGTH_SHORT).show();
            return;
        }

        // 임시 더미 경로 생성 (API 연동 필요)
        createDummyRoute();
    }

    /**
     * 임시 더미 경로 생성 (API 대신 더미 데이터 사용)
     */
    private void createDummyRoute() {
        // 출발지 표시
        startMarker.setPosition(startLatLng);
        startMarker.setCaptionText("출발");
        startMarker.setMap(naverMap);

        // 목적지 위치 설정 (임시로 출발지 근처 위치 사용)
        endLatLng = new LatLng(startLatLng.latitude + 0.01, startLatLng.longitude + 0.01);
        endMarker.setPosition(endLatLng);
        endMarker.setCaptionText("도착");
        endMarker.setMap(naverMap);

        // 경로 좌표 생성
        List<LatLng> pathLatLngs = new ArrayList<>();
        pathLatLngs.add(startLatLng);

        // 중간 지점 추가
        LatLng midPoint1 = new LatLng(
                startLatLng.latitude + (endLatLng.latitude - startLatLng.latitude) * 0.3,
                startLatLng.longitude + (endLatLng.longitude - startLatLng.longitude) * 0.3);

        LatLng midPoint2 = new LatLng(
                startLatLng.latitude + (endLatLng.latitude - startLatLng.latitude) * 0.7,
                startLatLng.longitude + (endLatLng.longitude - startLatLng.longitude) * 0.7);

        pathLatLngs.add(midPoint1);
        pathLatLngs.add(midPoint2);
        pathLatLngs.add(endLatLng);

        // 경로 그리기
        pathOverlay.setCoords(pathLatLngs);
        pathOverlay.setMap(naverMap);

        // 지도 카메라 위치 조정
        double minLat = Math.min(startLatLng.latitude, endLatLng.latitude);
        double maxLat = Math.max(startLatLng.latitude, endLatLng.latitude);
        double minLng = Math.min(startLatLng.longitude, endLatLng.longitude);
        double maxLng = Math.max(startLatLng.longitude, endLatLng.longitude);

        LatLng center = new LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);

        // 적절한 확대/축소 수준 설정
        CameraPosition cameraPosition = new CameraPosition(center, 14);
        naverMap.setCameraPosition(cameraPosition);

        // 경로 정보 표시
        infoWindow.setAdapter(new InfoWindow.DefaultTextAdapter(requireContext()) {
            @NonNull
            @Override
            public CharSequence getText(@NonNull InfoWindow infoWindow) {
                return getString(R.string.route_info) + "\n" +
                        getString(R.string.route_distance_duration, 3, 15); // 임시 데이터: 3km, 15분
            }
        });
        infoWindow.open(endMarker);

        Toast.makeText(requireContext(), "경로가 생성되었습니다(더미 데이터)", Toast.LENGTH_SHORT).show();
    }

    /**
     * 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
                Toast.makeText(requireContext(), getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show();
                return;
            }
            naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // 생명주기 메서드 재정의
    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}