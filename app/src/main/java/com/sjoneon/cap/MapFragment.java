package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private MapView mapView;
    private NaverMap naverMap;
    private FusedLocationSource locationSource;
    private FusedLocationProviderClient fusedLocationProviderClient;

    private Marker startMarker, endMarker;
    private PolylineOverlay polylineOverlay;

    private String startLocationName, endLocationName;
    private ArrayList<double[]> routePathCoords;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        Bundle args = getArguments();
        if (args != null) {
            startLocationName = args.getString("start_location", "");
            endLocationName = args.getString("end_location", "");
            routePathCoords = (ArrayList<double[]>) args.getSerializable("route_path_coords");
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
        naverMap.setLocationSource(locationSource);

        if (hasLocationPermission()) {
            enableLocationFeatures();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        setupMapUI();
        drawRoute();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocationFeatures() {
        naverMap.setLocationTrackingMode(com.naver.maps.map.LocationTrackingMode.Follow);
        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    private void setupMapUI() {
        naverMap.getUiSettings().setLocationButtonEnabled(true);
    }

    private void drawRoute() {
        if (naverMap == null || routePathCoords == null || routePathCoords.isEmpty()) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    naverMap.moveCamera(CameraUpdate.scrollTo(new LatLng(location.getLatitude(), location.getLongitude())));
                }
            });
            return;
        }

        List<LatLng> coords = new ArrayList<>();
        for (double[] point : routePathCoords) {
            coords.add(new LatLng(point[1], point[0])); // 위도(y), 경도(x) 순서
        }

        if (coords.isEmpty()) return;

        LatLng startPoint = coords.get(0);
        LatLng endPoint = coords.get(coords.size() - 1);

        if (startMarker != null) startMarker.setMap(null);
        if (endMarker != null) endMarker.setMap(null);
        if (polylineOverlay != null) polylineOverlay.setMap(null);

        startMarker = new Marker(startPoint);
        startMarker.setCaptionText("출발: " + startLocationName);
        startMarker.setMap(naverMap);

        endMarker = new Marker(endPoint);
        endMarker.setCaptionText("도착: " + endLocationName);
        endMarker.setMap(naverMap);

        polylineOverlay = new PolylineOverlay();
        polylineOverlay.setCoords(coords);
        polylineOverlay.setWidth(10);
        polylineOverlay.setColor(Color.BLUE); // 대중교통은 파란색으로 표시
        polylineOverlay.setMap(naverMap);

        naverMap.moveCamera(CameraUpdate.fitBounds(new LatLngBounds(startPoint, endPoint), 100));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (locationSource.isActivated()) {
                enableLocationFeatures();
            } else {
                Toast.makeText(getContext(), getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onDestroyView() { super.onDestroyView(); mapView.onDestroy(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}