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
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


// [수정] 인터페이스를 RouteFragment 클래스 외부, 하지만 파일 내부에 정의합니다.
interface RouteInteractionListener {
    void onNavigate(RouteFragment.RouteInfo route);
}

interface DetailToggleListener {
    void onToggle(int position);
}

public class RouteFragment extends Fragment {

    private static final String TAG = "RouteEngine"; // 디버깅을 위한 로그 태그
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String TMAP_API_BASE_URL = "https://apis.openapi.sk.com/";
    private static final String TAGO_API_BASE_URL = "https://apis.data.go.kr/1613000/";
    private static final int AVERAGE_TIME_PER_BUS_STOP_MIN = 2;
    // [요청사항 반영] 정류장 검색 범위 확대
    private static final int NEARBY_STOPS_COUNT = 20;

    private EditText editStartLocation, editEndLocation;
    private Button buttonSearchRoute, buttonMapView;
    private TextView textNoRoutes;
    private RecyclerView recyclerViewRoutes;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private final List<RouteInfo> routeList = new ArrayList<>();
    private RouteAdapter routeAdapter;
    private Geocoder geocoder;
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
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(requireContext(), Locale.KOREAN);
        executorService = Executors.newSingleThreadExecutor();
        Gson tagoGson = new GsonBuilder().registerTypeAdapter(TagoBusArrivalResponse.Body.class, new TagoBusArrivalDeserializer()).setLenient().create();
        Gson generalGson = new GsonBuilder().setLenient().create();
        tmapApiService = new Retrofit.Builder().baseUrl(TMAP_API_BASE_URL).addConverterFactory(GsonConverterFactory.create(generalGson)).build().create(TmapApiService.class);
        tagoApiService = new Retrofit.Builder().baseUrl(TAGO_API_BASE_URL).addConverterFactory(GsonConverterFactory.create(tagoGson)).build().create(TagoApiService.class);
    }

    private void setupRecyclerView() {
        recyclerViewRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
        routeAdapter = new RouteAdapter(routeList, this::navigateToMap, this::toggleRouteDetails);
        recyclerViewRoutes.setAdapter(routeAdapter);
    }

    private void setupClickListeners() {
        if (buttonSearchRoute != null) {
            buttonSearchRoute.setOnClickListener(v -> searchRoutes());
        } else {
            Log.e(TAG, "FATAL: buttonSearchRoute is null. Check the ID in fragment_route.xml");
        }
        if (buttonMapView != null) {
            buttonMapView.setOnClickListener(v -> {
                if (routeList.isEmpty()) { Toast.makeText(getContext(), "경로를 먼저 검색해주세요.", Toast.LENGTH_SHORT).show(); }
                else { openMapView(routeList.get(0)); }
            });
        }
    }

    private void toggleRouteDetails(int position) {
        if (position >= 0 && position < routeList.size()) {
            RouteInfo route = routeList.get(position);
            route.setExpanded(!route.isExpanded());
            routeAdapter.notifyItemChanged(position);
        }
    }

    private void navigateToMap(RouteInfo route) { openMapView(route); }

    private void loadCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
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
            if (addresses != null && !addresses.isEmpty()) return addresses.get(0).getAddressLine(0);
        } catch (IOException e) { Log.e(TAG, "주소 변환 실패", e); }
        return "";
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
        } catch (IOException e) { Log.e(TAG, "getCoordinatesFromAddress에서 IOException 발생: " + addressString, e); }
        return null;
    }

    private void searchRoutes() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editEndLocation.getText().toString().trim();
        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            Toast.makeText(getContext(), "출발지와 도착지를 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        updateRouteListVisibility(false, "경로를 탐색 중입니다...");
        executorService.execute(() -> {
            Location start = getCoordinatesFromAddress(startAddress);
            Location end = getCoordinatesFromAddress(endAddress);
            new Handler(Looper.getMainLooper()).post(() -> {
                this.startLocation = start;
                this.endLocation = end;
                if (startLocation == null || endLocation == null) {
                    Toast.makeText(getContext(), "주소를 좌표로 변환할 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                searchPublicTransitRoutes();
            });
        });
    }

    private void searchPublicTransitRoutes() {
        routeList.clear();
        routeAdapter.notifyDataSetChanged();
        tagoApiService.getNearbyBusStops(BuildConfig.TAGO_API_KEY_DECODED, startLocation.getLatitude(), startLocation.getLongitude(), NEARBY_STOPS_COUNT, 1, "json")
                .enqueue(new Callback<TagoBusStopResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TagoBusStopResponse> call, @NonNull Response<TagoBusStopResponse> response) {
                        if (isSuccess(response)) {
                            List<TagoBusStopResponse.BusStop> startStops = response.body().response.body.items.item;
                            Log.d(TAG, ">>> [1-Start] 출발지 주변 정류장 " + startStops.size() + "개 발견:");
                            for (TagoBusStopResponse.BusStop stop : startStops) {
                                Log.d(TAG, "  -> " + stop.nodenm + " (ID: " + stop.nodeid.trim() + ")");
                            }
                            findEndStopsAndProcess(startStops);
                        } else {
                            updateRouteListVisibility(true, "출발지 주변에 버스 정류장이 없습니다.");
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<TagoBusStopResponse> call, @NonNull Throwable t) {
                        handleApiFailure("출발지 주변 정류장 검색", t);
                    }
                });
    }

    private void findEndStopsAndProcess(List<TagoBusStopResponse.BusStop> startStops) {
        tagoApiService.getNearbyBusStops(BuildConfig.TAGO_API_KEY_DECODED, endLocation.getLatitude(), endLocation.getLongitude(), NEARBY_STOPS_COUNT, 1, "json")
                .enqueue(new Callback<TagoBusStopResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TagoBusStopResponse> call, @NonNull Response<TagoBusStopResponse> response) {
                        if (isSuccess(response)) {
                            List<TagoBusStopResponse.BusStop> endStops = response.body().response.body.items.item;
                            Log.d(TAG, ">>> [1-End] 목적지 주변 정류장 " + endStops.size() + "개 발견:");
                            for (TagoBusStopResponse.BusStop stop : endStops) {
                                Log.d(TAG, "  -> " + stop.nodenm + " (ID: " + stop.nodeid.trim() + ")");
                            }
                            findCommonRoutes(startStops, endStops);
                        } else {
                            updateRouteListVisibility(true, "목적지 주변에 버스 정류장이 없습니다.");
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<TagoBusStopResponse> call, @NonNull Throwable t) {
                        handleApiFailure("목적지 주변 정류장 검색", t);
                    }
                });
    }

    private void findCommonRoutes(List<TagoBusStopResponse.BusStop> startStops, List<TagoBusStopResponse.BusStop> endStops) {
        final List<RouteInfo> potentialRoutes = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger pendingArrivalCalls = new AtomicInteger(startStops.size());

        if (startStops.isEmpty()) {
            updateRouteListVisibility(true, "출발지 주변에 버스 정류장이 없습니다.");
            return;
        }

        for (TagoBusStopResponse.BusStop startStop : startStops) {
            tagoApiService.getBusArrivalInfo(BuildConfig.TAGO_API_KEY_DECODED, startStop.citycode, startStop.nodeid, 40, 1, "json")
                    .enqueue(new Callback<TagoBusArrivalResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<TagoBusArrivalResponse> call, @NonNull Response<TagoBusArrivalResponse> response) {
                            if (isSuccess(response)) {
                                TagoBusArrivalResponse.ItemsContainer itemsContainer = new Gson().fromJson(response.body().response.body.items, TagoBusArrivalResponse.ItemsContainer.class);
                                if (itemsContainer != null && itemsContainer.item != null) {
                                    AtomicInteger pendingRouteCalls = new AtomicInteger(itemsContainer.item.size());
                                    for (TagoBusArrivalResponse.BusArrival bus : itemsContainer.item) {
                                        checkIfRouteConnects(bus, startStop, endStops, potentialRoutes, pendingRouteCalls, pendingArrivalCalls);
                                    }
                                } else {
                                    if (pendingArrivalCalls.decrementAndGet() == 0) {
                                        finalizeRouteSearch(potentialRoutes);
                                    }
                                }
                            } else {
                                if (pendingArrivalCalls.decrementAndGet() == 0) {
                                    finalizeRouteSearch(potentialRoutes);
                                }
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<TagoBusArrivalResponse> call, @NonNull Throwable t) {
                            if (pendingArrivalCalls.decrementAndGet() == 0) {
                                finalizeRouteSearch(potentialRoutes);
                            }
                        }
                    });
        }
    }

    private void checkIfRouteConnects(TagoBusArrivalResponse.BusArrival bus, TagoBusStopResponse.BusStop startStop, List<TagoBusStopResponse.BusStop> endStops, List<RouteInfo> potentialRoutes, AtomicInteger pendingRouteCalls, AtomicInteger pendingArrivalCalls) {
        tagoApiService.getBusRouteStationList(BuildConfig.TAGO_API_KEY_DECODED, startStop.citycode, bus.routeid, 200, 1, "json")
                .enqueue(new Callback<TagoBusRouteStationResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<TagoBusRouteStationResponse> call, @NonNull Response<TagoBusRouteStationResponse> response) {
                        if (isSuccess(response)) {
                            List<TagoBusRouteStationResponse.RouteStation> routeStations = response.body().response.body.items.item;
                            Log.d(TAG, ">>> [2] " + bus.routeno + "번 버스 노선 확인 (정류장 " + routeStations.size() + "개)");

                            // [디버깅 로그 강화]
                            // 이 버스 노선에 포함된 모든 정류장 ID 목록을 출력합니다.
                            StringBuilder routeStationIds = new StringBuilder();
                            for(TagoBusRouteStationResponse.RouteStation station : routeStations) {
                                routeStationIds.append(station.nodeid.trim()).append(", ");
                            }
                            Log.d(TAG, "    [노선 전체 ID 목록]: " + routeStationIds.toString());

                            // 목적지 근처 정류장 ID 목록을 출력합니다.
                            StringBuilder endStopIds = new StringBuilder();
                            for(TagoBusStopResponse.BusStop stop : endStops) {
                                endStopIds.append(stop.nodeid.trim()).append(" (" + stop.nodenm + "), ");
                            }
                            Log.d(TAG, "    [탐색할 목적지 ID 목록]: " + endStopIds.toString());


                            int startOrd = findStationOrd(startStop.nodeid, routeStations);
                            // [디버깅 로그 강화] 출발 정류장의 순번(ord) 값을 확인합니다.
                            Log.d(TAG, "    [출발 정류장 " + startStop.nodenm + "(" + startStop.nodeid.trim() + ")] 의 순번(ord): " + startOrd);

                            if (startOrd != -1) {
                                TagoBusRouteStationResponse.RouteStation bestEndStop = findBestEndStop(endStops, routeStations, startOrd);

                                if (bestEndStop != null) {
                                    Log.i(TAG, "✅ [3] 경로 발견! (" + bus.routeno + "번) " + startStop.nodenm + " ("+startOrd+"번째) -> " + bestEndStop.nodenm + " ("+bestEndStop.ord+"번째)");
                                    calculateWalkingTimesAndFinalizeRoute(startStop, bestEndStop, bus, startOrd, potentialRoutes);
                                } else {
                                    Log.d(TAG, "❌ [3] 경로 없음 (" + bus.routeno + "번): 목적지 근처 정류장을 경유하지 않거나, 진행 방향이 맞지 않음.");
                                }
                            } else {
                                Log.w(TAG, "⚠️ [3] 데이터 오류 ("+bus.routeno+"번): 전체 노선에 출발정류장 ID("+startStop.nodeid.trim()+")가 없음.");
                            }
                        }

                        // 비동기 호출 카운터 관리
                        if (pendingRouteCalls.decrementAndGet() == 0) {
                            if (pendingArrivalCalls.decrementAndGet() == 0) {
                                finalizeRouteSearch(potentialRoutes);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<TagoBusRouteStationResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "노선 정보 조회 실패: " + bus.routeno, t);
                        // 비동기 호출 카운터 관리
                        if (pendingRouteCalls.decrementAndGet() == 0) {
                            if (pendingArrivalCalls.decrementAndGet() == 0) {
                                finalizeRouteSearch(potentialRoutes);
                            }
                        }
                    }
                });
    }

    /**
     * [수정된 메서드]
     * 노선 목록에서 특정 정류장 ID의 순번(ord)을 찾는 메서드.
     * ID의 숫자 부분만 비교하여 API 데이터의 비일관성 문제를 해결합니다.
     */
    private int findStationOrd(String nodeId, List<TagoBusRouteStationResponse.RouteStation> routeStations) {
        if (nodeId == null || nodeId.length() <= 3) return -1;
        // ID에서 숫자 부분만 추출 (예: "CJB283000037" -> "283000037")
        String numericNodeId = nodeId.substring(3);

        for (TagoBusRouteStationResponse.RouteStation station : routeStations) {
            if (station.nodeid != null && station.nodeid.length() > 3) {
                String numericStationId = station.nodeid.substring(3);
                if (numericStationId.equals(numericNodeId)) {
                    return station.ord; // 숫자 부분이 일치하면 순번을 반환
                }
            }
        }
        return -1; // 찾지 못한 경우
    }

    /**
     * [수정된 메서드]
     * 목적지 근처 정류장 목록과 버스 전체 노선을 비교하여,
     * 출발 정류장 이후에 있는 가장 적합한 도착 정류장을 찾는 메서드.
     * 이 메서드 역시 ID의 숫자 부분만 비교합니다.
     */
    private TagoBusRouteStationResponse.RouteStation findBestEndStop(List<TagoBusStopResponse.BusStop> endStops, List<TagoBusRouteStationResponse.RouteStation> routeStations, int startOrd) {
        for (TagoBusStopResponse.BusStop endStop : endStops) {
            if (endStop.nodeid == null || endStop.nodeid.length() <= 3) continue;
            String numericEndStopId = endStop.nodeid.substring(3);

            for (TagoBusRouteStationResponse.RouteStation stationOnRoute : routeStations) {
                if (stationOnRoute.nodeid != null && stationOnRoute.nodeid.length() > 3) {
                    String numericStationOnRouteId = stationOnRoute.nodeid.substring(3);

                    // 숫자 ID가 일치하고, 버스 진행 방향이 맞는지(순번이 더 큰지) 확인
                    if (numericStationOnRouteId.equals(numericEndStopId) && stationOnRoute.ord > startOrd) {
                        Log.d(TAG, "  [상세] 목적지 정류장 후보 발견: " + endStop.nodenm + " (ID: " + endStop.nodeid.trim() + ", 순번: " + stationOnRoute.ord + ")");
                        return stationOnRoute;
                    }
                }
            }
        }
        return null; // 적합한 도착 정류장을 찾지 못한 경우
    }


    private void calculateWalkingTimesAndFinalizeRoute(TagoBusStopResponse.BusStop startStop, TagoBusRouteStationResponse.RouteStation endStop, TagoBusArrivalResponse.BusArrival bus, int startOrd, List<RouteInfo> potentialRoutes) {
        tmapApiService.getPedestrianRoute(BuildConfig.TMAP_API_KEY, String.valueOf(startLocation.getLongitude()), String.valueOf(startLocation.getLatitude()), String.valueOf(startStop.gpslong), String.valueOf(startStop.gpslati), "출발지", startStop.nodenm)
                .enqueue(new Callback<TmapPedestrianResponse>() {
                    @Override
                    public void onResponse(Call<TmapPedestrianResponse> call, Response<TmapPedestrianResponse> response) {
                        if (isSuccess(response)) {
                            TmapPedestrianResponse.Feature feature = response.body().getFeatures().get(0);
                            int walkToStartStopSec = feature.getProperties().getTotalTime();
                            int endOrd = endStop.ord;
                            int busRideMin = (endOrd > startOrd) ? (endOrd - startOrd) * AVERAGE_TIME_PER_BUS_STOP_MIN : 20;
                            int walkToEndStopMin = 5;
                            int waitMin = bus.arrtime / 60;
                            int totalDurationMin = (walkToStartStopSec / 60) + waitMin + busRideMin + walkToEndStopMin;
                            RouteInfo routeInfo = new RouteInfo("대중교통", totalDurationMin, waitMin, bus.routeno, startStop.nodenm, endStop.nodenm);
                            routeInfo.setWalkingTimeToStartStop(walkToStartStopSec / 60);
                            routeInfo.setBusRideTime(busRideMin);
                            routeInfo.setWalkingTimeToDestination(walkToEndStopMin);
                            if (feature.getGeometry() != null && feature.getGeometry().getCoordinates() != null) {
                                routeInfo.setPathCoordinates(new ArrayList<>(feature.getGeometry().getCoordinates()));
                            }

                            boolean isDuplicate = false;
                            for(RouteInfo existingRoute : potentialRoutes) {
                                if(existingRoute.getBusNumber().equals(routeInfo.getBusNumber()) && existingRoute.getStartStopName().equals(routeInfo.getStartStopName())) {
                                    isDuplicate = true;
                                    break;
                                }
                            }
                            if(!isDuplicate) {
                                potentialRoutes.add(routeInfo);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<TmapPedestrianResponse> call, Throwable t) {
                        Log.e(TAG, "도보 경로 탐색 실패", t);
                    }
                });
    }

    private void finalizeRouteSearch(List<RouteInfo> potentialRoutes) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (potentialRoutes.isEmpty()) {
                updateRouteListVisibility(true, "이용 가능한 직행 경로를 찾지 못했습니다.");
            } else {
                potentialRoutes.sort(Comparator.comparingInt(RouteInfo::getDuration));
                routeList.clear();
                routeList.addAll(potentialRoutes);
                routeAdapter.notifyDataSetChanged();
                updateRouteListVisibility(false, "");
                Log.i(TAG, ">>> 최종 " + routeList.size() + "개의 경로를 찾았습니다.");
            }
        });
    }

    private boolean isSuccess(Response<?> response) {
        if (!response.isSuccessful() || response.body() == null) return false;
        if (response.body() instanceof TagoBusStopResponse) {
            TagoBusStopResponse body = (TagoBusStopResponse) response.body();
            return body.response != null && body.response.body != null && body.response.body.items != null && body.response.body.items.item != null;
        }
        if (response.body() instanceof TagoBusArrivalResponse) {
            TagoBusArrivalResponse body = (TagoBusArrivalResponse) response.body();
            return body.response != null && body.response.body != null && body.response.body.items != null;
        }
        if (response.body() instanceof TagoBusRouteStationResponse) {
            TagoBusRouteStationResponse body = (TagoBusRouteStationResponse) response.body();
            return body.response != null && body.response.body != null && body.response.body.items != null && body.response.body.items.item != null;
        }
        if (response.body() instanceof TmapPedestrianResponse) {
            TmapPedestrianResponse body = (TmapPedestrianResponse) response.body();
            return body.getFeatures() != null && !body.getFeatures().isEmpty();
        }
        return false;
    }

    private void updateRouteListVisibility(boolean noRoutes, String message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            textNoRoutes.setVisibility(noRoutes ? View.VISIBLE : View.GONE);
            recyclerViewRoutes.setVisibility(noRoutes ? View.GONE : View.VISIBLE);
            if (noRoutes) textNoRoutes.setText(message);
        });
    }

    private void handleApiError(String apiName, Response<?> response) {
        try {
            String errorBodyString = response.errorBody() != null ? response.errorBody().string() : "응답 내용 없음";
            Log.e(TAG, apiName + " API Error: " + response.code() + " - " + errorBodyString);
        } catch (Exception e) { Log.e(TAG, "에러 응답 처리 중 예외 발생", e); }
    }

    private void handleApiFailure(String apiName, Throwable t) {
        Log.e(TAG, apiName + " API 네트워크 호출 실패", t);
        updateRouteListVisibility(true, "네트워크 오류로 " + apiName + " 정보를 가져올 수 없습니다.");
    }

    private void openMapView(RouteInfo route) {
        if (getActivity() instanceof MainActivity) {
            MapFragment mapFragment = new MapFragment();
            Bundle args = new Bundle();
            args.putString("start_location", editStartLocation.getText().toString());
            args.putString("end_location", editEndLocation.getText().toString());
            if (route.getPathCoordinates() != null) {
                args.putSerializable("route_path_coords", (Serializable) route.getPathCoordinates());
            }
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
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCurrentLocation();
        } else {
            Toast.makeText(getContext(), "위치 권한이 거부되었습니다.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    public static class RouteInfo implements Serializable {
        private String routeType, busNumber, startStopName, endStopName;
        private int duration, busWaitTime, busRideTime, walkingTimeToStartStop, walkingTimeToDestination;
        private boolean isExpanded = false;
        private List<List<Double>> pathCoordinates;
        public RouteInfo(String type, int totalDuration, int waitTime, String busNum, String startStop, String endStop) {
            this.routeType = type; this.duration = totalDuration; this.busWaitTime = waitTime; this.busNumber = busNum; this.startStopName = startStop; this.endStopName = endStop;
        }
        public String getRouteType() { return routeType; }
        public int getDuration() { return duration; }
        public String getBusNumber() { return busNumber; }
        public String getStartStopName() { return startStopName; }
        public String getEndStopName() { return endStopName; }
        public int getBusWaitTime() { return busWaitTime; }
        public int getBusRideTime() { return busRideTime; }
        public int getWalkingTimeToStartStop() { return walkingTimeToStartStop; }
        public int getWalkingTimeToDestination() { return walkingTimeToDestination; }
        public boolean isExpanded() { return isExpanded; }
        public List<List<Double>> getPathCoordinates() { return pathCoordinates; }
        public void setBusRideTime(int busRideTime) { this.busRideTime = busRideTime; }
        public void setWalkingTimeToStartStop(int time) { this.walkingTimeToStartStop = time; }
        public void setWalkingTimeToDestination(int time) { this.walkingTimeToDestination = time; }
        public void setExpanded(boolean expanded) { isExpanded = expanded; }
        public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }
        public String getRouteSummary() {
            int walkTime = walkingTimeToStartStop + walkingTimeToDestination;
            return String.format("총 %d분 소요 (도보 %d분 + 대기 %d분 + 버스 %d분)", duration, walkTime, busWaitTime, busRideTime);
        }
        public String getDepartureTimeInfo() { return String.format("약 %d분 후 도착", busWaitTime); }
    }

    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {
        private List<RouteInfo> routes;
        private final RouteInteractionListener listener;
        private final DetailToggleListener detailListener;
        public RouteAdapter(List<RouteInfo> routes, RouteInteractionListener listener, DetailToggleListener detailListener) {
            this.routes = routes; this.listener = listener; this.detailListener = detailListener;
        }
        @NonNull @Override
        public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route, parent, false);
            return new RouteViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
            RouteInfo route = routes.get(position);
            holder.textRouteType.setText(route.getRouteType());
            holder.textRouteSummary.setText(route.getRouteSummary());
            holder.textDepartureTime.setText(route.getDepartureTimeInfo());
            holder.layoutRouteDetail.setVisibility(route.isExpanded() ? View.VISIBLE : View.GONE);
            holder.buttonExpandRoute.setText(route.isExpanded() ? "간략히 보기" : "상세 보기");
        }
        @Override
        public int getItemCount() { return routes.size(); }
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
                buttonExpandRoute.setOnClickListener(v -> detailListener.onToggle(getAdapterPosition()));
                buttonStartNavigation.setOnClickListener(v -> listener.onNavigate(routes.get(getAdapterPosition())));
            }
        }
    }
}