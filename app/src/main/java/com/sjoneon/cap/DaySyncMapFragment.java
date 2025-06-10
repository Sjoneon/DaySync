package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment; // 이제 충돌 없음
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;

/**
 * 친구 방식을 참고하여 단순화된 지도 프래그먼트
 * 클래스 이름을 DaySyncMapFragment로 변경하여 네이버의 MapFragment와 충돌 방지
 */
public class DaySyncMapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "DaySyncMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private NaverMap naverMap;
    private FusedLocationProviderClient locationProviderClient;

    // UI 요소
    private EditText editStartLocation;
    private EditText editDestination;
    private Button buttonSearchRoute;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // UI 요소 초기화
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editDestination = view.findViewById(R.id.editDestination);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);

        // 경로 검색 버튼 클릭 리스너
        buttonSearchRoute.setOnClickListener(v -> searchRoute());

        // 위치 서비스 클라이언트 초기화
        locationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // 네이버 MapFragment 설정 (친구 방식)
        MapFragment mapFragment =
                (MapFragment) getChildFragmentManager().findFragmentById(R.id.mapView);

        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.mapView, mapFragment)
                    .commit();
        }

        // 지도 준비
        mapFragment.getMapAsync(this);

        Log.d(TAG, "DaySyncMapFragment 뷰 생성 완료 (친구 방식)");

        return view;
    }

    /**
     * 지도가 준비되었을 때 호출 (친구 방식 기반)
     */
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        Log.d(TAG, "지도 준비 완료!");

        // 위치 권한 확인 및 현재 위치 표시
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // 권한 요청
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // 현재 위치 가져오기 및 표시 (친구 방식)
        getCurrentLocationAndDisplay();
    }

    /**
     * 현재 위치 가져오기 및 표시 (친구 방식 기반)
     */
    private void getCurrentLocationAndDisplay() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "위치 권한이 없습니다");
            setDefaultLocation();
            return;
        }

        locationProviderClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        // 현재 위치 좌표
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                        // 지도 카메라 이동
                        naverMap.moveCamera(CameraUpdate.scrollTo(currentLatLng));

                        // 현재 위치 오버레이 표시 (친구 방식)
                        LocationOverlay overlay = naverMap.getLocationOverlay();
                        overlay.setVisible(true);
                        overlay.setPosition(currentLatLng);

                        // UI에 좌표 표시
                        String locationStr = String.format("위도: %.6f, 경도: %.6f",
                                location.getLatitude(), location.getLongitude());
                        editStartLocation.setText(locationStr);

                        Log.d(TAG, "현재 위치 표시 완료: " + locationStr);

                    } else {
                        Log.w(TAG, "위치 정보가 null입니다. 기본 위치 사용");
                        setDefaultLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "위치 정보 가져오기 실패: " + e.getMessage());
                    Toast.makeText(requireContext(), "위치 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();
                    setDefaultLocation();
                });
    }

    /**
     * 기본 위치 설정 (청주시)
     */
    private void setDefaultLocation() {
        // 청주 좌표
        LatLng defaultLatLng = new LatLng(36.6357, 127.4912);

        if (naverMap != null) {
            // 지도 카메라 이동
            naverMap.moveCamera(CameraUpdate.scrollTo(defaultLatLng));

            // 마커 표시
            Marker marker = new Marker();
            marker.setPosition(defaultLatLng);
            marker.setMap(naverMap);

            // UI에 표시
            editStartLocation.setText("위도: 36.635700, 경도: 127.491200 (기본값)");

            Log.d(TAG, "기본 위치 설정됨: 청주");
            Toast.makeText(requireContext(), "기본 위치(청주)로 설정되었습니다", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 경로 검색 기능
     */
    private void searchRoute() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editDestination.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(requireContext(), "출발지와 도착지를 모두 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "경로 검색 기능이 곧 구현될 예정입니다", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "경로 검색 요청: " + startAddress + " -> " + endAddress);
    }

    /**
     * 권한 요청 결과 처리 (친구 방식)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 권한 허용됨 → 다시 현재 위치 설정 시도
            if (naverMap != null) {
                getCurrentLocationAndDisplay();
            }
        } else {
            // 권한 거부됨 → 기본 위치 사용
            Toast.makeText(requireContext(), "위치 권한이 거부되어 기본 위치를 사용합니다", Toast.LENGTH_LONG).show();
            setDefaultLocation();
        }
    }
}