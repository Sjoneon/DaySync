package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;


import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RouteFragment extends Fragment {

    private static final String TAG = "RouteFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    // [수정] 베이스 URL을 명확하게 네이버 클라우드 플랫폼 주소로 변경합니다.
    private static final String NAVER_API_BASE_URL = "https://naveropenapi.apigw.ntruss.com/";

    // 클래스 멤버 변수 선언부
    private EditText editStartLocation, editEndLocation;
    private Button buttonSearchRoute, buttonMapView;
    private TextView textNoRoutes;
    private RecyclerView recyclerViewRoutes;
    private FusedLocationProviderClient fusedLocationClient;
    private List<RouteInfo> routeList = new ArrayList<>();
    private RouteAdapter routeAdapter;
    private Geocoder geocoder;
    private NaverApiService naverApiService;
    private List<List<Double>> currentPath;
    private ExecutorService executorService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route, container, false);
        initializeViews(view);
        initializeServices();
        setupRecyclerView();
        setupClickListeners();
        loadCurrentLocation();
        return view;
    }

    private void initializeViews(View view) {
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editEndLocation = view.findViewById(R.id.editEndLocation);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);
        buttonMapView = view.findViewById(R.id.buttonMapView);
        textNoRoutes = view.findViewById(R.id.textNoRoutes);
        recyclerViewRoutes = view.findViewById(R.id.recyclerViewRoutes);
    }

    private void initializeServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(requireContext(), Locale.KOREAN);
        executorService = Executors.newSingleThreadExecutor();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(NAVER_API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        naverApiService = retrofit.create(NaverApiService.class);
    }

    private void setupRecyclerView() {
        recyclerViewRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
        routeAdapter = new RouteAdapter(routeList);
        recyclerViewRoutes.setAdapter(routeAdapter);
    }

    private void setupClickListeners() {
        buttonSearchRoute.setOnClickListener(v -> searchRoutes());
        buttonMapView.setOnClickListener(v -> openMapView());
    }

    private void loadCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        executorService.execute(() -> {
                            String address = getAddressFromLocation(location);
                            new Handler(Looper.getMainLooper()).post(() -> editStartLocation.setText(address));
                        });
                    }
                });
    }

    private String getAddressFromLocation(Location location) {
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            Log.e(TAG, "주소 변환 실패", e);
        }
        return "";
    }

    private void searchRoutes() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editEndLocation.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.empty_location_error), Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            Location startLocation = getCoordinatesFromAddress(startAddress);
            Location endLocation = getCoordinatesFromAddress(endAddress);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (startLocation == null || endLocation == null) {
                    Toast.makeText(getContext(), getString(R.string.geocode_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                fetchDrivingDirections(startLocation, endLocation);
            });
        });
    }

    private Location getCoordinatesFromAddress(String addressString) {
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressString, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                Location location = new Location("");
                location.setLatitude(address.getLatitude());
                location.setLongitude(address.getLongitude());
                return location;
            }
        } catch (IOException e) {
            Log.e(TAG, "주소 -> 좌표 변환 실패: " + addressString, e);
        }
        return null;
    }

    /**
     * [수정] 네이버 Directions API를 호출하여 운전 경로를 가져옵니다.
     * 에러 응답 처리를 강화하여 API 호출 실패 시 원인을 명확히 파악할 수 있도록 수정했습니다.
     */
    private void fetchDrivingDirections(Location start, Location end) {
        String startCoords = String.format(Locale.US, "%f,%f", start.getLongitude(), start.getLatitude());
        String endCoords = String.format(Locale.US, "%f,%f", end.getLongitude(), end.getLatitude());

        // API 키가 BuildConfig를 통해 정상적으로 로드되는지 로그로 확인
        Log.d(TAG, "Naver Client ID: " + BuildConfig.NAVER_CLIENT_ID);

        naverApiService.getDrivingDirections(
                        BuildConfig.NAVER_CLIENT_ID,
                        BuildConfig.NAVER_CLIENT_SECRET,
                        startCoords,
                        endCoords)
                .enqueue(new Callback<NaverDirectionsResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<NaverDirectionsResponse> call, @NonNull Response<NaverDirectionsResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            NaverDirectionsResponse directionsResponse = response.body();
                            // [수정] API 응답 구조에 맞게 경로 데이터 확인
                            if (directionsResponse.getRoute() != null && directionsResponse.getRoute().getTraoptimal() != null && !directionsResponse.getRoute().getTraoptimal().isEmpty()) {
                                processDrivingRouteData(directionsResponse);
                            } else {
                                String responseBody = new Gson().toJson(response.body());
                                Log.w(TAG, "API 응답 성공, 그러나 경로 데이터 없음: " + responseBody);
                                Toast.makeText(getContext(), "탐색 가능한 경로가 없습니다.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            // [수정] API 에러 응답을 더 상세히 로그로 남김
                            try {
                                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                                Log.e(TAG, "API Error: " + response.code() + " - " + errorBody);
                                Toast.makeText(getContext(), "경로 탐색 오류 " + response.code() + ": " + errorBody, Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Log.e(TAG, "에러 응답 처리 중 예외 발생", e);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<NaverDirectionsResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Directions API 네트워크 호출 실패", t);
                        Toast.makeText(getContext(), "네트워크 오류가 발생했습니다: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void processDrivingRouteData(NaverDirectionsResponse data) {
        routeList.clear();
        NaverDirectionsResponse.Traoptimal optimalRoute = data.getRoute().getTraoptimal().get(0);
        NaverDirectionsResponse.Summary summary = optimalRoute.getSummary();

        int durationMinutes = summary.getDuration() / 1000 / 60;
        double distanceKm = summary.getDistance() / 1000.0;

        String summaryText = String.format("총 이동 시간: %d분, 거리: %.1fkm", durationMinutes, distanceKm);
        String detailText = String.format("예상 택시비: %,d원", summary.getTaxiFare());

        routeList.add(new RouteInfo("자동차", summaryText, "지금 출발", detailText, durationMinutes, true));
        currentPath = optimalRoute.getPath();

        routeAdapter.notifyDataSetChanged();
        updateRouteListVisibility();
    }

    private void updateRouteListVisibility() {
        textNoRoutes.setVisibility(routeList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerViewRoutes.setVisibility(routeList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openMapView() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();

            MapFragment mapFragment = new MapFragment();
            Bundle args = new Bundle();
            args.putString("start_location", editStartLocation.getText().toString());
            args.putString("end_location", editEndLocation.getText().toString());
            if (currentPath != null) {
                ArrayList<double[]> serializablePath = new ArrayList<>();
                for(List<Double> point : currentPath) {
                    serializablePath.add(new double[]{point.get(0), point.get(1)});
                }
                args.putSerializable("route_path_coords", serializablePath);
            }
            mapFragment.setArguments(args);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mapFragment)
                    .addToBackStack(null)
                    .commit();

            if (mainActivity.getSupportActionBar() != null) {
                mainActivity.getSupportActionBar().setTitle("지도");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCurrentLocation();
            } else {
                Toast.makeText(getContext(), getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    public static class RouteInfo {
        private String routeType, routeSummary, departureTime, routeDetail;
        private int duration;
        private boolean isRecommended;

        public RouteInfo(String type, String summary, String departure, String detail, int duration, boolean recommended) {
            this.routeType = type; this.routeSummary = summary; this.departureTime = departure;
            this.routeDetail = detail; this.duration = duration; this.isRecommended = recommended;
        }

        public String getRouteType() { return routeType; }
        public String getRouteSummary() { return routeSummary; }
        public String getDepartureTime() { return departureTime; }
        public String getRouteDetail() { return routeDetail; }
        public boolean isRecommended() { return isRecommended; }
    }

    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {
        private List<RouteInfo> routes;
        public RouteAdapter(List<RouteInfo> routes) { this.routes = routes; }

        @NonNull @Override
        public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RouteViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
            RouteInfo route = routes.get(position);
            holder.textRouteType.setText(route.getRouteType());
            holder.textRouteSummary.setText(route.getRouteSummary());
            holder.textDepartureTime.setText(route.getDepartureTime());
        }

        @Override public int getItemCount() { return routes.size(); }

        class RouteViewHolder extends RecyclerView.ViewHolder {
            TextView textRouteType, textRouteSummary, textDepartureTime;
            Button buttonExpandRoute, buttonStartNavigation;

            RouteViewHolder(View itemView) {
                super(itemView);
                textRouteType = itemView.findViewById(R.id.textRouteType);
                textRouteSummary = itemView.findViewById(R.id.textRouteSummary);
                textDepartureTime = itemView.findViewById(R.id.textDepartureTime);
                buttonExpandRoute = itemView.findViewById(R.id.buttonExpandRoute);
                buttonStartNavigation = itemView.findViewById(R.id.buttonStartNavigation);
            }
        }
    }
}