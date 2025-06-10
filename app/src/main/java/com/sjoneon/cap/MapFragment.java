package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout; // FrameLayout import 추가
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;

/**
 * 지도 기능을 제공하는 프래그먼트 (수정된 최종 버전)
 * MapView를 동적으로 생성하여 FrameLayout에 추가하는 방식으로 변경했습니다.
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FusedLocationProviderClient fusedLocationClient;
    private FusedLocationSource locationSource;
    private MapView mapView; // MapView 객체는 그대로 유지
    private NaverMap naverMap;

    private EditText editStartLocation;
    private EditText editDestination;
    private Button buttonSearchRoute;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // XML에서 FrameLayout 컨테이너를 찾습니다.
        FrameLayout mapContainer = view.findViewById(R.id.map_container);

        // UI 요소 초기화
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editDestination = view.findViewById(R.id.editDestination);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);
        buttonSearchRoute.setOnClickListener(v -> searchRoute());

        // MapView를 동적으로 생성합니다.
        mapView = new MapView(requireContext());
        mapView.setId(View.generateViewId()); // 동적으로 ID 부여 (필요 시)

        // 생성된 MapView를 FrameLayout에 추가합니다.
        mapContainer.addView(mapView);

        // MapView 생명주기 관리
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        return view;
    }

    /**
     * NaverMap 객체가 준비되면 호출되는 콜백
     */
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        Log.d(TAG, "onMapReady: 네이버 맵이 준비되었습니다.");

        setupMapUi(naverMap);
        naverMap.setLocationSource(locationSource);
        checkLocationPermission();
    }

    // ... (이하 코드는 이전 답변과 동일하게 유지) ...

    private void setupMapUi(@NonNull NaverMap map) {
        map.getUiSettings().setZoomControlEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setLocationButtonEnabled(true);
    }

    private void checkLocationPermission() {
        if (hasLocationPermission()) {
            startTracking();
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void startTracking() {
        if (naverMap == null) return;
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        fetchCurrentLocation();
    }

    private void fetchCurrentLocation() {
        if (!hasLocationPermission()) return;
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            updateStartLocation(currentLatLng, "현재 위치");
                            moveMapToLocation(currentLatLng);
                        } else {
                            setDefaultLocation();
                        }
                    })
                    .addOnFailureListener(requireActivity(), e -> setDefaultLocation());
        } catch (SecurityException e) {
            Log.e(TAG, "위치 권한 관련 보안 예외가 발생했습니다.", e);
        }
    }

    private void updateStartLocation(LatLng latLng, String name) {
        editStartLocation.setText(String.format("%s (%.6f, %.6f)", name, latLng.latitude, latLng.longitude));
    }

    private void moveMapToLocation(LatLng latLng) {
        if (naverMap == null) return;
        naverMap.moveCamera(CameraUpdate.scrollTo(latLng));
        Marker marker = new Marker();
        marker.setPosition(latLng);
        marker.setMap(naverMap);
    }

    private void setDefaultLocation() {
        LatLng cheongjuCityHall = new LatLng(36.6424, 127.4891);
        updateStartLocation(cheongjuCityHall, "기본 위치 (청주시청)");
        moveMapToLocation(cheongjuCityHall);
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (naverMap != null && locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
            } else {
                Toast.makeText(getContext(), "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void searchRoute() {
        // ... (이전과 동일) ...
        Toast.makeText(getContext(), "경로 검색 기능은 구현 예정입니다.", Toast.LENGTH_SHORT).show();
    }

    // region MapView Lifecycle (필수)
    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
    // endregion
}