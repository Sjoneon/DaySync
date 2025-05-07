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

import java.util.ArrayList;
import java.util.List;

/**
 * 지도 기능을 제공하는 프래그먼트
 * 현재 네이버 지도 SDK 의존성 문제로 임시 구현
 */
public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 위치 권한
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    // 위치 변경 감지 최소 거리 (미터 단위)
    private static final float MIN_LOCATION_DISTANCE = 3.0f; // 3미터

    private FusedLocationProviderClient fusedLocationClient;

    // UI 요소
    private EditText editStartLocation;
    private EditText editDestination;
    private Button buttonSearchRoute;

    // 좌표 정보
    private double startLatitude = 0;
    private double startLongitude = 0;
    private double endLatitude = 0;
    private double endLongitude = 0;

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

        // 경로 검색 버튼 클릭 리스너 설정
        buttonSearchRoute.setOnClickListener(v -> searchRoute());

        // 위치 서비스 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // 현재 위치 가져오기
        getCurrentLocation();

        // 임시 메시지 표시
        Toast.makeText(requireContext(), "지도 기능은 현재 개발 중입니다.", Toast.LENGTH_SHORT).show();

        return view;
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
                                // 현재 위치 정보 저장
                                startLatitude = location.getLatitude();
                                startLongitude = location.getLongitude();
                                lastLocation = location;

                                // 좌표 표시
                                String locationStr = String.format("위도: %.6f, 경도: %.6f",
                                        startLatitude, startLongitude);
                                editStartLocation.setText(locationStr);

                                Log.d(TAG, "현재 위치: " + locationStr);
                            } else {
                                Log.w(TAG, "위치 정보를 가져올 수 없습니다");
                                // 기본 위치로 설정 (청주)
                                startLatitude = 36.6357;
                                startLongitude = 127.4912;

                                // 기본 위치 표시
                                String defaultLocation = String.format("위도: %.6f, 경도: %.6f (기본값)",
                                        startLatitude, startLongitude);
                                editStartLocation.setText(defaultLocation);

                                // 기본 위치 메시지
                                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없어 기본 위치(청주)로 설정됩니다", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "위치 정보 가져오기 실패: " + e.getMessage(), e);
                        Toast.makeText(requireContext(), "위치 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();

                        // 기본 위치 설정 (청주)
                        startLatitude = 36.6357;
                        startLongitude = 127.4912;

                        // 기본 위치 표시
                        String defaultLocation = String.format("위도: %.6f, 경도: %.6f (기본값)",
                                startLatitude, startLongitude);
                        editStartLocation.setText(defaultLocation);
                    });
        } catch (Exception e) {
            Log.e(TAG, "위치 정보 가져오기 오류: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "위치 정보 가져오기 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 경로 검색 메서드 (임시 구현)
     */
    private void searchRoute() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editDestination.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.empty_location_error), Toast.LENGTH_SHORT).show();
            return;
        }

        // 임시 메시지 표시
        Toast.makeText(requireContext(), "경로 검색 기능이 곧 구현될 예정입니다.", Toast.LENGTH_SHORT).show();

        // 임의의 목적지 좌표 설정 (실제로는 지오코딩 API 필요)
        endLatitude = startLatitude + 0.01;
        endLongitude = startLongitude + 0.01;

        // 경로 정보 표시 (임시)
        String routeInfo = getString(R.string.route_info) + "\n" +
                getString(R.string.route_distance_duration, 3, 15); // 임시 데이터: 3km, 15분

        Toast.makeText(requireContext(), routeInfo, Toast.LENGTH_LONG).show();
    }

    /**
     * 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 허용되면 현재 위치 가져오기
                getCurrentLocation();
            } else {
                Toast.makeText(requireContext(), getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}