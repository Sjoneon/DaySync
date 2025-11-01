package com.sjoneon.cap.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.sjoneon.cap.BuildConfig;
import com.sjoneon.cap.R;
import com.sjoneon.cap.models.api.TmapPedestrianResponse;
import com.sjoneon.cap.services.TmapApiService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NavigationFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "NavigationFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private static final int ARRIVAL_THRESHOLD_METERS = 50;

    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private TextView textNavigationStep;
    private TextView textDistance;
    private TextView textEstimatedTime;
    private Button buttonExitNavigation;
    private LinearLayout layoutNavigationInfo;

    private TmapApiService tmapApiService;
    private Handler mainHandler;

    private String startStopName;
    private String endStopName;
    private String busNumber;
    private String directionInfo;
    private double startStopLat;
    private double startStopLng;
    private double endStopLat;
    private double endStopLng;
    private double destinationLat;
    private double destinationLng;

    private int currentNavigationStep = 0;
    private PolylineOverlay currentRouteOverlay;
    private Marker currentStepMarker;
    private Location currentLocation;
    private boolean isNavigating = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        mainHandler = new Handler(Looper.getMainLooper());

        tmapApiService = new Retrofit.Builder()
                .baseUrl("https://apis.openapi.sk.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmapApiService.class);

        Bundle args = getArguments();
        if (args != null) {
            startStopName = args.getString("start_stop_name");
            endStopName = args.getString("end_stop_name");
            busNumber = args.getString("bus_number");
            directionInfo = args.getString("direction_info");
            startStopLat = args.getDouble("start_stop_lat");
            startStopLng = args.getDouble("start_stop_lng");
            endStopLat = args.getDouble("end_stop_lat");
            endStopLng = args.getDouble("end_stop_lng");
            destinationLat = args.getDouble("destination_lat");
            destinationLng = args.getDouble("destination_lng");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_navigation, container, false);

        mapView = view.findViewById(R.id.mapView);
        textNavigationStep = view.findViewById(R.id.textNavigationStep);
        textDistance = view.findViewById(R.id.textDistance);
        textEstimatedTime = view.findViewById(R.id.textEstimatedTime);
        buttonExitNavigation = view.findViewById(R.id.buttonExitNavigation);
        layoutNavigationInfo = view.findViewById(R.id.layoutNavigationInfo);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        buttonExitNavigation.setOnClickListener(v -> exitNavigation());

        return view;
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource);

        if (checkLocationPermission()) {
            startNavigation();
        } else {
            requestLocationPermission();
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void startNavigation() {
        if (!checkLocationPermission()) {
            return;
        }

        isNavigating = true;
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!isNavigating) return;

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentLocation = location;
                    updateNavigationState(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback,
                Looper.getMainLooper());

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                currentNavigationStep = 0;
                updateNavigationStep();
            }
        });
    }

    private void updateNavigationState(Location location) {
        switch (currentNavigationStep) {
            case 0:
                double distanceToStartStop = calculateDistance(
                        location.getLatitude(), location.getLongitude(),
                        startStopLat, startStopLng);

                if (distanceToStartStop <= ARRIVAL_THRESHOLD_METERS) {
                    currentNavigationStep = 1;
                    updateNavigationStep();
                } else {
                    updateStepInfo(distanceToStartStop, "도보로 출발 정류장까지 이동 중");
                }
                break;

            case 1:
                double distanceToEndStop = calculateDistance(
                        location.getLatitude(), location.getLongitude(),
                        endStopLat, endStopLng);

                if (distanceToEndStop <= ARRIVAL_THRESHOLD_METERS) {
                    currentNavigationStep = 2;
                    updateNavigationStep();
                }
                break;

            case 2:
                double distanceToDestination = calculateDistance(
                        location.getLatitude(), location.getLongitude(),
                        destinationLat, destinationLng);

                if (distanceToDestination <= ARRIVAL_THRESHOLD_METERS) {
                    arriveAtDestination();
                } else {
                    updateStepInfo(distanceToDestination, "도보로 목적지까지 이동 중");
                }
                break;
        }
    }

    private void updateNavigationStep() {
        if (currentLocation == null) return;

        if (currentStepMarker != null) {
            currentStepMarker.setMap(null);
        }
        if (currentRouteOverlay != null) {
            currentRouteOverlay.setMap(null);
        }

        switch (currentNavigationStep) {
            case 0:
                textNavigationStep.setText("1단계: 출발 정류장으로 이동");
                drawRouteToLocation(currentLocation.getLatitude(), currentLocation.getLongitude(),
                        startStopLat, startStopLng);

                currentStepMarker = new Marker();
                currentStepMarker.setPosition(new LatLng(startStopLat, startStopLng));
                currentStepMarker.setCaptionText(startStopName);
                currentStepMarker.setMap(naverMap);
                break;

            case 1:
                textNavigationStep.setText(String.format("2단계: %s번 버스 탑승 (%s)",
                        busNumber, directionInfo));
                textDistance.setText(String.format("%s 정류장에서 하차", endStopName));
                textEstimatedTime.setText("버스를 탑승하세요");

                currentStepMarker = new Marker();
                currentStepMarker.setPosition(new LatLng(endStopLat, endStopLng));
                currentStepMarker.setCaptionText(endStopName);
                currentStepMarker.setMap(naverMap);

                naverMap.moveCamera(CameraUpdate.scrollTo(
                        new LatLng(endStopLat, endStopLng)));
                break;

            case 2:
                textNavigationStep.setText("3단계: 목적지까지 도보 이동");
                drawRouteToLocation(currentLocation.getLatitude(), currentLocation.getLongitude(),
                        destinationLat, destinationLng);

                currentStepMarker = new Marker();
                currentStepMarker.setPosition(new LatLng(destinationLat, destinationLng));
                currentStepMarker.setCaptionText("목적지");
                currentStepMarker.setMap(naverMap);
                break;
        }
    }

    private void drawRouteToLocation(double fromLat, double fromLng, double toLat, double toLng) {
        // TMAP API를 사용하여 도보 경로 조회
        Call<TmapPedestrianResponse> call = tmapApiService.getPedestrianRoute(
                BuildConfig.TMAP_API_KEY,
                String.valueOf(fromLng),
                String.valueOf(fromLat),
                String.valueOf(toLng),
                String.valueOf(toLat),
                "출발지",
                "도착지"
        );

        call.enqueue(new Callback<TmapPedestrianResponse>() {
            @Override
            public void onResponse(@NonNull Call<TmapPedestrianResponse> call,
                                   @NonNull Response<TmapPedestrianResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<LatLng> pathCoords = parseRouteCoordinates(response.body());
                    if (!pathCoords.isEmpty()) {
                        drawRouteOnMap(pathCoords);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<TmapPedestrianResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "경로 조회 실패", t);
            }
        });
    }

    private List<LatLng> parseRouteCoordinates(TmapPedestrianResponse response) {
        List<LatLng> coords = new ArrayList<>();

        if (response.getFeatures() != null) {
            for (TmapPedestrianResponse.Feature feature : response.getFeatures()) {
                if (feature.getGeometry() != null &&
                        "LineString".equals(feature.getGeometry().getType())) {

                    // getCoordinatesAsList() 메서드 사용
                    List<List<Double>> coordsList = feature.getGeometry().getCoordinatesAsList();
                    if (coordsList != null) {
                        for (List<Double> coord : coordsList) {
                            if (coord.size() >= 2) {
                                // 경도, 위도 순서
                                coords.add(new LatLng(coord.get(1), coord.get(0)));
                            }
                        }
                    }
                }
            }
        }

        return coords;
    }

    private void drawRouteOnMap(List<LatLng> coords) {
        if (currentRouteOverlay != null) {
            currentRouteOverlay.setMap(null);
        }

        currentRouteOverlay = new PolylineOverlay();
        currentRouteOverlay.setCoords(coords);
        currentRouteOverlay.setWidth(15);
        currentRouteOverlay.setColor(Color.parseColor("#0066FF"));
        currentRouteOverlay.setMap(naverMap);
    }

    private void updateStepInfo(double distance, String message) {
        int meters = (int) distance;
        textDistance.setText(String.format("남은 거리: %dm", meters));

        int estimatedMinutes = (int) Math.ceil(distance / 80.0);
        textEstimatedTime.setText(String.format("예상 시간: 약 %d분", estimatedMinutes));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void arriveAtDestination() {
        isNavigating = false;
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        textNavigationStep.setText("목적지에 도착했습니다!");
        textDistance.setText("");
        textEstimatedTime.setText("");

        Toast.makeText(requireContext(), "목적지에 도착했습니다!", Toast.LENGTH_LONG).show();
    }

    private void exitNavigation() {
        isNavigating = false;
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        requireActivity().onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                Toast.makeText(requireContext(), "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            } else {
                startNavigation();
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override public void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause() { super.onPause(); if (mapView != null) mapView.onPause(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
    @Override public void onStop() { super.onStop(); if (mapView != null) mapView.onStop(); }
    @Override public void onDestroyView() {
        super.onDestroyView();
        if (locationCallback != null && fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (mapView != null) mapView.onDestroy();
    }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
}