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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;

/**
 * 지도 기능을 제공하는 프래그먼트
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
    // 3미터

    private FusedLocationProviderClient fusedLocationClient;
    private FusedLocationSource locationSource;
    private MapView mapView;
    private NaverMap naverMap;

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
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // 네이버 맵 초기화
        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        // 맵 비동기 로드
        mapView.getMapAsync(this);

        return view;
    }

    /**
     * 네트워크 연결 테스트를 위한 메서드
     */
    private void testNetworkConnection() {
        new Thread(() -> {
            try {
                Log.d(TAG, "네트워크 연결 테스트 시작...");

                // naver.com에 연결 테스트
                java.net.InetAddress address = java.net.InetAddress.getByName("naveropenapi.apigw.ntruss.com");
                boolean reachable = address.isReachable(5000); // 5초 타임아웃

                requireActivity().runOnUiThread(() -> {
                    if (reachable) {
                        Log.d(TAG, "네이버 API 서버에 연결 가능!");
                        Toast.makeText(requireContext(), "네이버 API 서버에 연결 가능", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "네이버 API 서버에 연결할 수 없습니다");
                        Toast.makeText(requireContext(), "네이버 API 서버에 연결할 수 없습니다", Toast.LENGTH_SHORT).show();
                    }
                });

                // DNS 조회 테스트
                String hostAddress = address.getHostAddress();
                requireActivity().runOnUiThread(() -> {
                    Log.d(TAG, "DNS 조회 결과: " + hostAddress);
                    Toast.makeText(requireContext(), "DNS 조회 결과: " + hostAddress, Toast.LENGTH_SHORT).show();
                });

            } catch (java.net.UnknownHostException e) {
                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "호스트를 찾을 수 없음: " + e.getMessage());
                    Toast.makeText(requireContext(), "호스트를 찾을 수 없음: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "네트워크 테스트 오류: " + e.getMessage());
                    Toast.makeText(requireContext(), "네트워크 테스트 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }


    /**
     * 맵이 준비되었을 때 호출되는 콜백 메서드
     */
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        try {
            Log.d(TAG, "지도 준비 완료! NaverMap 인스턴스 정상 생성");

            // 지도 유형 설정 (기본값은 Basic)
            naverMap.setMapType(NaverMap.MapType.Basic);

            // 위치 소스 설정
            naverMap.setLocationSource(locationSource);

            // 로깅 추가 - 지도 초기화 성공 확인
            Log.d(TAG, "지도 설정 완료: 유형=" + naverMap.getMapType() + ", 위치 소스 설정됨");

            // UI 설정
            naverMap.getUiSettings().setZoomControlEnabled(true);
            naverMap.getUiSettings().setCompassEnabled(true);
            naverMap.getUiSettings().setLocationButtonEnabled(true);
            Log.d(TAG, "UI 설정 완료");

            // 현재 위치 비동기 요청
            new Thread(() -> {
                try {
                    // 위치 정보 가져오기 (비동기 작업)
                    if (ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                        // 메인 스레드로 UI 업데이트
                        requireActivity().runOnUiThread(() -> {
                            Log.d(TAG, "위치 권한 확인됨, 현재 위치 표시 활성화");

                            // 위치 추적 모드 설정
                            naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

                            // 현재 위치 가져오기
                            getCurrentLocation();
                        });
                    } else {
                        Log.d(TAG, "위치 권한 없음");
                        // 권한 요청
                        requireActivity().runOnUiThread(() -> {
                            ActivityCompat.requestPermissions(requireActivity(), PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "위치 정보 가져오기 오류: " + e.getMessage());
                }
            }).start();

            Log.d(TAG, "네이버 맵 초기화 성공");

        } catch (Exception e) {
            Log.e(TAG, "네이버 맵 초기화 오류: " + e.getMessage(), e);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "지도 초기화 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 현재 위치 가져오기
     */
    private void getCurrentLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(requireActivity(), PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            // 권한 명시적 확인 추가
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없는 경우, 다시 요청
                ActivityCompat.requestPermissions(requireActivity(), PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);
                return;
            }

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

                                // 맵이 준비되었으면 카메라 이동
                                if (naverMap != null) {
                                    moveMapToLocation(startLatitude, startLongitude);
                                }
                            } else {
                                Log.w(TAG, "위치 정보를 가져올 수 없습니다");
                                // 기본 위치로 설정 (청주)
                                setDefaultLocation();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "위치 정보 가져오기 실패: " + e.getMessage(), e);
                        Toast.makeText(requireContext(), "위치 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();
                        setDefaultLocation();
                    });
        } catch (SecurityException e) {
            // 권한 관련 보안 예외 처리
            Log.e(TAG, "위치 권한 오류: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            setDefaultLocation();
        } catch (Exception e) {
            Log.e(TAG, "위치 정보 가져오기 오류: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "위치 정보 가져오기 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            setDefaultLocation();
        }
    }

    /**
     * 기본 위치 설정 (청주시)
     */
    private void setDefaultLocation() {
        // 기본 위치 설정 (청주)
        startLatitude = 36.6357;
        startLongitude = 127.4912;

        // 기본 위치 표시
        String defaultLocation = String.format("위도: %.6f, 경도: %.6f (기본값)",
                startLatitude, startLongitude);
        editStartLocation.setText(defaultLocation);

        // 맵이 준비되었으면 카메라 이동
        if (naverMap != null) {
            moveMapToLocation(startLatitude, startLongitude);
        }

        // 기본 위치 메시지
        Toast.makeText(requireContext(), "현재 위치를 가져올 수 없어 기본 위치(청주)로 설정됩니다", Toast.LENGTH_SHORT).show();
    }

    /**
     * 지도 카메라를 지정된 위치로 이동
     */
    private void moveMapToLocation(double latitude, double longitude) {
        try {
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(latitude, longitude));
            naverMap.moveCamera(cameraUpdate);

            // 마커 추가
            Marker marker = new Marker();
            marker.setPosition(new LatLng(latitude, longitude));
            marker.setMap(naverMap);
        } catch (Exception e) {
            Log.e(TAG, "지도 카메라 이동 오류: " + e.getMessage(), e);
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

        // 목적지 마커 추가
        if (naverMap != null) {
            try {
                Marker endMarker = new Marker();
                endMarker.setPosition(new LatLng(endLatitude, endLongitude));
                endMarker.setMap(naverMap);

                // 목적지로 카메라 이동
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(endLatitude, endLongitude));
                naverMap.moveCamera(cameraUpdate);

                // 경로 정보 표시 (임시)
                String routeInfo = getString(R.string.route_info) + "\n" +
                        getString(R.string.route_distance_duration, 3, 15); // 임시 데이터: 3km, 15분

                Toast.makeText(requireContext(), routeInfo, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "경로 검색 오류: " + e.getMessage(), e);
                Toast.makeText(requireContext(), "경로 검색 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 위치 권한이 있는지 확인
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                // 권한이 거부된 경우
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
                Toast.makeText(requireContext(), getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show();
            } else {
                // 권한이 허용된 경우
                naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
                getCurrentLocation();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // 생명주기 메서드 - 맵뷰와 동기화
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