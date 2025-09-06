package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;

    private String startLocationName, endLocationName;
    private List<List<Double>> routePathCoords;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        Bundle args = getArguments();
        if (args != null) {
            startLocationName = args.getString("start_location", "출발지");
            endLocationName = args.getString("end_location", "도착지");

            // 직렬화된 경로 좌표 데이터를 안전하게 가져오기
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    routePathCoords = (List<List<Double>>) args.getSerializable("route_path_coords", Serializable.class);
                } else {
                    routePathCoords = (List<List<Double>>) args.getSerializable("route_path_coords");
                }
            } catch (ClassCastException e){
                routePathCoords = null;
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mapView = new MapView(requireActivity());
        return mapView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource); // 현재 위치 소스 설정

        // 위치 권한 확인 및 요청
        if (hasLocationPermission()) {
            enableLocationFeatures();
        } else {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        drawRoute();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 위치 권한이 있을 경우, GPS 현위치 추적 및 버튼을 활성화합니다.
     */
    private void enableLocationFeatures() {
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow); // 위치 추적 모드 활성화
        naverMap.getUiSettings().setLocationButtonEnabled(true); // 현위치 버튼 표시
    }

    /**
     * 전달받은 경로 좌표를 지도에 그리고, 출발/도착 마커를 표시합니다.
     */
    private void drawRoute() {
        if (routePathCoords == null || routePathCoords.isEmpty()) {
            Toast.makeText(getContext(), "표시할 경로 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<LatLng> coords = new ArrayList<>();
        for (List<Double> point : routePathCoords) {
            if (point.size() >= 2) {
                // TMAP API는 [경도, 위도] 순서로 좌표를 제공합니다.
                coords.add(new LatLng(point.get(1), point.get(0)));
            }
        }

        if (coords.size() < 2) return;

        // 경로(파란선) 그리기
        PolylineOverlay polyline = new PolylineOverlay();
        polyline.setCoords(coords);
        polyline.setWidth(10);
        polyline.setColor(Color.BLUE);
        polyline.setMap(naverMap);

        // 출발/도착 마커 생성
        Marker startMarker = new Marker(coords.get(0));
        startMarker.setCaptionText(startLocationName);
        startMarker.setMap(naverMap);

        Marker endMarker = new Marker(coords.get(coords.size() - 1));
        endMarker.setCaptionText(endLocationName);
        endMarker.setMap(naverMap);

        // 모든 경로와 마커가 보이도록 카메라 위치 조정
        LatLngBounds bounds = new LatLngBounds(coords.get(0), coords.get(coords.size() - 1));
        naverMap.moveCamera(CameraUpdate.fitBounds(bounds, 100)); // 100px의 패딩
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            } else {
                enableLocationFeatures();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // MapView 생명주기 관리
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onDestroyView() { super.onDestroyView(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}