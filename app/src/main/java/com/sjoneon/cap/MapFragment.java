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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 네이버 지도 API를 이용한 지도 및 경로 표시 프래그먼트
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 네이버 API 관련 상수
    private static final String CLIENT_ID = "l4dae8ewvg";
    private static final String CLIENT_SECRET = "teM3IEaDFmhkSyYRpm3rU655tnaLXiaOFBMLB83X";
    private static final String GEOCODE_URL = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode";
    private static final String DIRECTION_URL = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving";
    private static final String REVERSE_GEOCODE_URL = "https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc";

    // 위치 권한
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

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

    // OkHttp 클라이언트
    private OkHttpClient okHttpClient;

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

        // OkHttp 클라이언트 초기화
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

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
                Log.d(TAG, "위치 변경: " + location.getLatitude() + ", " + location.getLongitude());
                // 위치가 변경되면 현재 위치 업데이트
                updateCurrentLocation(location);
            });

            // 현재 위치 표시 활성화
            naverMap.getUiSettings().setLocationButtonEnabled(true);

            // 한국 기준으로 초기 카메라 위치 설정 (서울)
            LatLng defaultLocation = new LatLng(37.5666, 126.9782); // 서울 시청
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

            // 역지오코딩으로 주소 가져오기
            reverseGeocode(startLatLng, true);
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

                                // 시작 마커 설정
                                startMarker.setPosition(startLatLng);
                                startMarker.setMap(naverMap);

                                // 역지오코딩으로 주소 가져오기
                                reverseGeocode(currentLatLng, true);
                            } else {
                                Log.w(TAG, "위치 정보를 가져올 수 없습니다");
                                // 기본 위치로 설정 (서울시청)
                                LatLng defaultLocation = new LatLng(37.5666, 126.9782);
                                CameraPosition cameraPosition = new CameraPosition(defaultLocation, 15);
                                naverMap.setCameraPosition(cameraPosition);

                                // 기본 위치 메시지
                                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없어 기본 위치(서울)로 설정됩니다", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "위치 정보 가져오기 실패: " + e.getMessage(), e);
                        Toast.makeText(requireContext(), "위치 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();

                        // 기본 위치 설정 (서울시청)
                        LatLng defaultLocation = new LatLng(37.5666, 126.9782);
                        CameraPosition cameraPosition = new CameraPosition(defaultLocation, 15);
                        naverMap.setCameraPosition(cameraPosition);
                    });
        } catch (Exception e) {
            Log.e(TAG, "위치 정보 가져오기 오류: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "위치 정보 가져오기 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 주소로 좌표를 검색하는 지오코딩 메서드
     * @param address 검색할 주소
     * @param isStart 시작 지점 여부
     */
    private void geocode(String address, boolean isStart) {
        HttpUrl httpUrl = HttpUrl.parse(GEOCODE_URL)
                .newBuilder()
                .addQueryParameter("query", address)
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("X-NCP-APIGW-API-KEY-ID", CLIENT_ID)
                .addHeader("X-NCP-APIGW-API-KEY", CLIENT_SECRET)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "지오코딩 요청 실패: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "주소 검색 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonData = response.body().string();
                        Log.d(TAG, "지오코딩 응답: " + jsonData);

                        JSONObject jsonObject = new JSONObject(jsonData);
                        JSONArray addresses = jsonObject.getJSONArray("addresses");

                        if (addresses.length() > 0) {
                            JSONObject addressObj = addresses.getJSONObject(0);
                            double latitude = Double.parseDouble(addressObj.getString("y"));
                            double longitude = Double.parseDouble(addressObj.getString("x"));
                            Log.d(TAG, "검색 결과 좌표: " + latitude + ", " + longitude);

                            requireActivity().runOnUiThread(() -> {
                                LatLng latLng = new LatLng(latitude, longitude);

                                if (isStart) {
                                    startLatLng = latLng;
                                    startMarker.setPosition(latLng);
                                    startMarker.setMap(naverMap);

                                    // 카메라 이동
                                    CameraPosition cameraPosition = new CameraPosition(latLng, 15);
                                    naverMap.setCameraPosition(cameraPosition);
                                } else {
                                    endLatLng = latLng;
                                    endMarker.setPosition(latLng);
                                    endMarker.setMap(naverMap);
                                }

                                // 두 지점이 모두 설정된 경우 경로 가져오기
                                if (startLatLng != null && endLatLng != null) {
                                    getDirections(startLatLng, endLatLng);
                                }
                            });
                        } else {
                            Log.w(TAG, "검색 결과가 없습니다: " + address);
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "검색 결과가 없습니다: " + address, Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON 파싱 오류: " + e.getMessage(), e);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "응답 파싱 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "응답 본문 없음";
                    Log.e(TAG, "지오코딩 요청 실패: " + response.code() + " - " + errorBody);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "요청 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /**
     * 좌표로 주소를 검색하는 역지오코딩 메서드
     * @param latLng 검색할 좌표
     * @param isStart 시작 지점 여부
     */
    private void reverseGeocode(LatLng latLng, boolean isStart) {
        HttpUrl httpUrl = HttpUrl.parse(REVERSE_GEOCODE_URL)
                .newBuilder()
                .addQueryParameter("coords", latLng.longitude + "," + latLng.latitude)
                .addQueryParameter("output", "json")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("X-NCP-APIGW-API-KEY-ID", CLIENT_ID)
                .addHeader("X-NCP-APIGW-API-KEY", CLIENT_SECRET)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "역지오코딩 요청 실패: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "주소 검색 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonData = response.body().string();
                        Log.d(TAG, "역지오코딩 응답: " + jsonData);

                        JSONObject jsonObject = new JSONObject(jsonData);

                        JSONArray results = jsonObject.getJSONArray("results");
                        if (results.length() > 0) {
                            JSONObject result = results.getJSONObject(0);
                            String address = result.getJSONObject("region").getJSONObject("area1").getString("name") + " " +
                                    result.getJSONObject("region").getJSONObject("area2").getString("name") + " " +
                                    result.getJSONObject("region").getJSONObject("area3").getString("name");
                            Log.d(TAG, "역지오코딩 주소 결과: " + address);

                            requireActivity().runOnUiThread(() -> {
                                if (isStart) {
                                    editStartLocation.setText(address);
                                    startMarker.setPosition(latLng);
                                    startMarker.setCaptionText("출발");
                                    startMarker.setMap(naverMap);
                                } else {
                                    editDestination.setText(address);
                                    endMarker.setPosition(latLng);
                                    endMarker.setCaptionText("도착");
                                    endMarker.setMap(naverMap);
                                }
                            });
                        } else {
                            Log.w(TAG, "역지오코딩 결과가 없습니다");
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "주소 검색 결과가 없습니다", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "역지오코딩 JSON 파싱 오류: " + e.getMessage(), e);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "응답 파싱 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "응답 본문 없음";
                    Log.e(TAG, "역지오코딩 요청 실패: " + response.code() + " - " + errorBody);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "요청 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /**
     * 경로 검색 메서드
     */
    private void searchRoute() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editDestination.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.empty_location_error), Toast.LENGTH_SHORT).show();
            return;
        }

        // 시작 위치 지오코딩
        geocode(startAddress, true);

        // 도착 위치 지오코딩
        geocode(endAddress, false);
    }

    /**
     * 경로 가져오기 메서드
     * @param start 시작 좌표
     * @param end 목적지 좌표
     */
    private void getDirections(LatLng start, LatLng end) {
        HttpUrl httpUrl = HttpUrl.parse(DIRECTION_URL)
                .newBuilder()
                .addQueryParameter("start", start.longitude + "," + start.latitude)
                .addQueryParameter("goal", end.longitude + "," + end.latitude)
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("X-NCP-APIGW-API-KEY-ID", CLIENT_ID)
                .addHeader("X-NCP-APIGW-API-KEY", CLIENT_SECRET)
                .build();

        Log.d(TAG, "경로 요청 URL: " + httpUrl);

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "경로 요청 실패: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "경로 검색 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonData = response.body().string();
                        Log.d(TAG, "경로 응답: " + jsonData);

                        JSONObject jsonObject = new JSONObject(jsonData);

                        // 경로 그리기
                        if (jsonObject.has("route")) {
                            JSONObject route = jsonObject.getJSONObject("route");
                            JSONArray traoptimal = route.getJSONArray("traoptimal");

                            if (traoptimal.length() > 0) {
                                JSONObject path = traoptimal.getJSONObject(0);
                                JSONArray pathCoords = path.getJSONArray("path");

                                List<LatLng> pathLatLngs = new ArrayList<>();
                                for (int i = 0; i < pathCoords.length(); i++) {
                                    JSONArray coord = pathCoords.getJSONArray(i);
                                    double lon = coord.getDouble(0);
                                    double lat = coord.getDouble(1);
                                    pathLatLngs.add(new LatLng(lat, lon));
                                }

                                requireActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // 경로 그리기 (좌표가 2개 이상인지 확인)
                                        if (pathLatLngs.size() >= 2) {
                                            // 기존 경로 제거
                                            pathOverlay.setMap(null);

                                            // 새 경로 설정 및 지도에 표시
                                            pathOverlay.setCoords(pathLatLngs);
                                            pathOverlay.setMap(naverMap);

                                            // 지도 카메라 위치 조정
                                            double minLat = Math.min(start.latitude, end.latitude);
                                            double maxLat = Math.max(start.latitude, end.latitude);
                                            double minLng = Math.min(start.longitude, end.longitude);
                                            double maxLng = Math.max(start.longitude, end.longitude);

                                            LatLng center = new LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);

                                            // 줌 레벨 계산 (값이 클수록 더 축소됨)
                                            double zoomLevel = 13;
                                            if (maxLat - minLat > 0.1 || maxLng - minLng > 0.1) zoomLevel = 11;
                                            if (maxLat - minLat > 0.3 || maxLng - minLng > 0.3) zoomLevel = 9;
                                            if (maxLat - minLat > 0.5 || maxLng - minLng > 0.5) zoomLevel = 8;

                                            CameraPosition cameraPosition = new CameraPosition(center, zoomLevel);
                                            naverMap.setCameraPosition(cameraPosition);

                                            // 경로 정보 표시
                                            try {
                                                if (path.has("summary")) {
                                                    JSONObject summary = path.getJSONObject("summary");
                                                    int duration = summary.getInt("duration") / 1000 / 60; // 분 단위로 변환
                                                    int distance = summary.getInt("distance") / 1000; // km 단위로 변환

                                                    // 인포 윈도우 설정
                                                    infoWindow.setAdapter(new InfoWindow.DefaultTextAdapter(requireContext()) {
                                                        @NonNull
                                                        @Override
                                                        public CharSequence getText(@NonNull InfoWindow infoWindow) {
                                                            return getString(R.string.route_info) + "\n" +
                                                                    getString(R.string.route_distance_duration, distance, duration);
                                                        }
                                                    });
                                                    infoWindow.open(endMarker);
                                                }
                                            } catch (JSONException e) {
                                                Log.e(TAG, "경로 정보 파싱 오류: " + e.getMessage(), e);
                                                Toast.makeText(requireContext(), "경로 정보 파싱 오류", Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            Log.w(TAG, "유효한 경로를 찾을 수 없습니다 (좌표 수: " + pathLatLngs.size() + ")");
                                            Toast.makeText(requireContext(), "유효한 경로를 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            } else {
                                Log.w(TAG, "경로 정보가 없습니다");
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "경로 정보가 없습니다", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            Log.w(TAG, "경로 정보가 없습니다");
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "경로 정보가 없습니다", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "경로 응답 파싱 오류: " + e.getMessage(), e);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "응답 파싱 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "응답 본문 없음";
                    Log.e(TAG, "경로 요청 실패: " + response.code() + " - " + errorBody);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "요청 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
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