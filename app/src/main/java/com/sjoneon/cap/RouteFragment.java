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
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import java.util.ArrayList;
import java.util.Comparator;
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

    // API Base URLs
    private static final String NAVER_API_BASE_URL = "https://naveropenapi.apigw.ntruss.com/";
    private static final String TMAP_API_BASE_URL = "https://apis.openapi.sk.com/";
    // [수정] TAGO API의 Base URL을 공통된 최상위 경로로 변경합니다.
    private static final String TAGO_API_BASE_URL = "https://apis.data.go.kr/1613000/ArvlInfoInqireService/";

    // 클래스 멤버 변수 선언부
    private EditText editStartLocation, editEndLocation;
    private Button buttonSearchRoute, buttonMapView;
    private TextView textNoRoutes;
    private RecyclerView recyclerViewRoutes;
    private RadioGroup transportRadioGroup;
    private FusedLocationProviderClient fusedLocationClient;
    private List<RouteInfo> routeList = new ArrayList<>();
    private RouteAdapter routeAdapter;
    private Geocoder geocoder;
    private NaverApiService naverApiService;
    private TmapApiService tmapApiService;
    private TagoApiService tagoApiService;
    private ExecutorService executorService;
    private Location startLocation, endLocation;

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
        executorService = Executors.newFixedThreadPool(3);

        naverApiService = new Retrofit.Builder()
                .baseUrl(NAVER_API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NaverApiService.class);

        tmapApiService = new Retrofit.Builder()
                .baseUrl(TMAP_API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmapApiService.class);

        // [수정] 수정된 TAGO_API_BASE_URL 상수를 사용하여 Retrofit 인스턴스를 생성합니다.
        tagoApiService = new Retrofit.Builder()
                .baseUrl(TAGO_API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .build()
                .create(TagoApiService.class);
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
                        this.startLocation = location;
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
            Toast.makeText(getContext(), "출발지와 도착지를 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            if (this.startLocation == null) {
                this.startLocation = getCoordinatesFromAddress(startAddress);
            }
            this.endLocation = getCoordinatesFromAddress(endAddress);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (startLocation == null || endLocation == null) {
                    Toast.makeText(getContext(), "주소를 좌표로 변환할 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                searchPublicTransitRoutes();
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

    private void searchPublicTransitRoutes() {
        routeList.clear();
        routeAdapter.notifyDataSetChanged();
        updateRouteListVisibility(false, "대중교통 경로를 탐색 중입니다...");

        tagoApiService.getNearbyBusStops(BuildConfig.TAGO_API_KEY_ENCODED, startLocation.getLatitude(), startLocation.getLongitude(), 10, 1, "json")
                .enqueue(new Callback<TagoBusStopResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TagoBusStopResponse> call, @NonNull Response<TagoBusStopResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().response != null && response.body().response.body != null && response.body().response.body.items != null) {
                            List<TagoBusStopResponse.BusStop> nearbyStops = response.body().response.body.items.item;
                            if (nearbyStops == null || nearbyStops.isEmpty()) {
                                updateRouteListVisibility(true, "주변에 버스 정류장이 없습니다.");
                                return;
                            }
                            findAndProcessBusRoutes(nearbyStops);
                        } else {
                            handleApiError("주변 정류장 검색", response);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TagoBusStopResponse> call, @NonNull Throwable t) {
                        handleApiFailure("주변 정류장 검색", t);
                    }
                });
    }

    private void findAndProcessBusRoutes(List<TagoBusStopResponse.BusStop> nearbyStops) {
        List<RouteInfo> potentialRoutes = new ArrayList<>();
        for (TagoBusStopResponse.BusStop stop : nearbyStops) {
            tagoApiService.getBusArrivalInfo(BuildConfig.TAGO_API_KEY_ENCODED, stop.citycode, stop.nodeid, 20, 1, "json")
                    .enqueue(new Callback<TagoBusArrivalResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<TagoBusArrivalResponse> call, @NonNull Response<TagoBusArrivalResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().response.body.items != null && response.body().response.body.items.item != null) {
                                for (TagoBusArrivalResponse.BusArrival bus : response.body().response.body.items.item) {
                                    checkBusRouteAndCreateRouteInfo(bus, stop, potentialRoutes);
                                }
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<TagoBusArrivalResponse> call, @NonNull Throwable t) {
                            Log.w(TAG, "정류장 도착 정보 조회 실패: " + stop.nodenm, t);
                        }
                    });
        }
    }

    private void checkBusRouteAndCreateRouteInfo(TagoBusArrivalResponse.BusArrival bus, TagoBusStopResponse.BusStop startStop, List<RouteInfo> potentialRoutes) {
        tagoApiService.getBusRouteStationList(BuildConfig.TAGO_API_KEY_ENCODED, startStop.citycode, bus.routeid, 100, 1, "json")
                .enqueue(new Callback<TagoBusRouteStationResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TagoBusRouteStationResponse> call, @NonNull Response<TagoBusRouteStationResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().response.body.items != null && response.body().response.body.items.item != null) {
                            List<TagoBusRouteStationResponse.RouteStation> routeStations = response.body().response.body.items.item;
                            TagoBusRouteStationResponse.RouteStation endStop = findClosestStopToEndLocation(routeStations);
                            if (endStop == null) return;
                            calculateWalkingTimesAndFinalizeRoute(startStop, endStop, bus, potentialRoutes);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TagoBusRouteStationResponse> call, @NonNull Throwable t) {
                        Log.w(TAG, "버스 노선 정보 조회 실패: " + bus.routeno, t);
                    }
                });
    }

    private void calculateWalkingTimesAndFinalizeRoute(TagoBusStopResponse.BusStop startStop, TagoBusRouteStationResponse.RouteStation endStop, TagoBusArrivalResponse.BusArrival bus, List<RouteInfo> potentialRoutes) {
        final int[] walkToStartStopSec = new int[1];
        String startX = String.valueOf(startLocation.getLongitude());
        String startY = String.valueOf(startLocation.getLatitude());
        String startStopX = String.valueOf(startStop.gpslong);
        String startStopY = String.valueOf(startStop.gpslati);

        tmapApiService.getPedestrianRoute(BuildConfig.TMAP_API_KEY, startX, startY, startStopX, startStopY, "출발지", startStop.nodenm)
                .enqueue(new Callback<TmapPedestrianResponse>() {
                    @Override
                    public void onResponse(Call<TmapPedestrianResponse> call, Response<TmapPedestrianResponse> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().getFeatures().isEmpty()) {
                            walkToStartStopSec[0] = response.body().getFeatures().get(0).getProperties().getTotalTime();
                            int walkToEndStopMin = 5;
                            int waitMin = bus.arrtime / 60;
                            int busRideMin = 20;
                            int totalDurationMin = (walkToStartStopSec[0] / 60) + waitMin + busRideMin + walkToEndStopMin;

                            String summary = String.format("총 %d분 소요 (도보 %d분 + 대기 %d분 + 버스 %d분)", totalDurationMin, (walkToStartStopSec[0] / 60) + walkToEndStopMin, waitMin, busRideMin);
                            String detail = String.format("%s번 버스 (%s 정류장 승차)", bus.routeno, startStop.nodenm);

                            potentialRoutes.add(new RouteInfo("대중교통", summary, "약 " + waitMin + "분 후 도착", detail, totalDurationMin, false));
                            potentialRoutes.sort(Comparator.comparingInt(RouteInfo::getDuration));

                            routeList.clear();
                            routeList.addAll(potentialRoutes);
                            routeAdapter.notifyDataSetChanged();
                            updateRouteListVisibility(routeList.isEmpty(), "추천 경로가 없습니다.");
                        }
                    }

                    @Override
                    public void onFailure(Call<TmapPedestrianResponse> call, Throwable t) {
                        Log.e(TAG, "출발지->정류장 도보 경로 탐색 실패", t);
                    }
                });
    }

    private TagoBusRouteStationResponse.RouteStation findClosestStopToEndLocation(List<TagoBusRouteStationResponse.RouteStation> stations) {
        if (stations != null && stations.size() > 5) {
            return stations.get(stations.size() - 2);
        }
        return null;
    }

    private void updateRouteListVisibility(boolean noRoutes, String message) {
        if(getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (noRoutes) {
                textNoRoutes.setText(message);
                textNoRoutes.setVisibility(View.VISIBLE);
                recyclerViewRoutes.setVisibility(View.GONE);
            } else {
                textNoRoutes.setVisibility(View.GONE);
                recyclerViewRoutes.setVisibility(View.VISIBLE);
            }
        });
    }

    private void handleApiError(String apiName, Response<?> response) {
        try {
            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
            Log.e(TAG, apiName + " API Error: " + response.code() + " - " + errorBody);
            updateRouteListVisibility(true, apiName + " API 호출에 실패했습니다.");
        } catch (Exception e) {
            Log.e(TAG, "에러 응답 처리 중 예외 발생", e);
        }
    }

    private void handleApiFailure(String apiName, Throwable t) {
        Log.e(TAG, apiName + " API 네트워크 호출 실패", t);
        updateRouteListVisibility(true, "네트워크 오류로 " + apiName + " 정보를 가져올 수 없습니다.");
    }

    private void openMapView() {
        if (getActivity() instanceof MainActivity) {
            MapFragment mapFragment = new MapFragment();
            Bundle args = new Bundle();
            args.putString("start_location", editStartLocation.getText().toString());
            args.putString("end_location", editEndLocation.getText().toString());
            mapFragment.setArguments(args);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mapFragment)
                    .addToBackStack(null)
                    .commit();

            if (((MainActivity) getActivity()).getSupportActionBar() != null) {
                ((MainActivity) getActivity()).getSupportActionBar().setTitle("지도");
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
                Toast.makeText(getContext(), "위치 권한이 거부되었습니다. 앱 설정에서 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
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
            this.routeType = type;
            this.routeSummary = summary;
            this.departureTime = departure;
            this.routeDetail = detail;
            this.duration = duration;
            this.isRecommended = recommended;
        }

        public String getRouteType() { return routeType; }
        public String getRouteSummary() { return routeSummary; }
        public String getDepartureTime() { return departureTime; }
        public String getRouteDetail() { return routeDetail; }
        public boolean isRecommended() { return isRecommended; }
        public int getDuration() { return duration; }
    }

    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {
        private List<RouteInfo> routes;

        public RouteAdapter(List<RouteInfo> routes) {
            this.routes = routes;
        }

        @NonNull
        @Override
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

        @Override
        public int getItemCount() {
            return routes.size();
        }

        class RouteViewHolder extends RecyclerView.ViewHolder {
            TextView textRouteType, textRouteSummary, textDepartureTime;
            Button buttonExpandRoute, buttonStartNavigation;
            LinearLayout layoutRouteDetail;

            RouteViewHolder(View itemView) {
                super(itemView);
                textRouteType = itemView.findViewById(R.id.textRouteType);
                textRouteSummary = itemView.findViewById(R.id.textRouteSummary);
                textDepartureTime = itemView.findViewById(R.id.textDepartureTime);
                buttonExpandRoute = itemView.findViewById(R.id.buttonExpandRoute);
                buttonStartNavigation = itemView.findViewById(R.id.buttonStartNavigation);
                layoutRouteDetail = itemView.findViewById(R.id.layoutRouteDetail);
            }
        }
    }
}