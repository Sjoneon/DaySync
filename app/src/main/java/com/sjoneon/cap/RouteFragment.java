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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 개선된 버스 경로 탐색 시스템
 * - 정류장 검색 범위 확대
 * - 노선 검증 강화
 * - 로깅 개선
 */
public class RouteFragment extends Fragment {

    private static final String TAG = "RouteEngine";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private static final String TMAP_API_BASE_URL = "https://apis.openapi.sk.com/";
    private static final String TAGO_API_BASE_URL = "https://apis.data.go.kr/1613000/";

    private static final int AVERAGE_BUS_SPEED_BETWEEN_STOPS_MIN = 2;
    private static final int MAX_ROUTES_TO_SHOW = 10;
    private static final int DEFAULT_BUS_RIDE_TIME_MIN = 15;
    private static final int MAX_API_PAGES = 5;

    // [수정] 정류장 검색 범위 확대
    private static final int NEARBY_STOPS_COUNT = 50; // 30 -> 50으로 증가
    private static final int EXTENDED_SEARCH_RADIUS = 1000; // 미터 단위

    private EditText editStartLocation, editEndLocation;
    private Button buttonSearchRoute, buttonMapView;
    private TextView textNoRoutes;
    private RecyclerView recyclerViewRoutes;

    private FusedLocationProviderClient fusedLocationClient;
    private final List<RouteInfo> routeList = new ArrayList<>();
    private RouteAdapter routeAdapter;
    private Geocoder geocoder;
    private TmapApiService tmapApiService;
    private TagoApiService tagoApiService;
    private ExecutorService executorService;
    private Handler mainHandler;

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
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        Gson tagoGson = new GsonBuilder()
                .registerTypeAdapter(TagoBusArrivalResponse.Body.class, new TagoBusArrivalDeserializer())
                .setLenient()
                .create();

        Gson generalGson = new GsonBuilder().setLenient().create();

        tmapApiService = new Retrofit.Builder()
                .baseUrl(TMAP_API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(generalGson))
                .build()
                .create(TmapApiService.class);

        tagoApiService = new Retrofit.Builder()
                .baseUrl(TAGO_API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(tagoGson))
                .build()
                .create(TagoApiService.class);
    }

    private void setupRecyclerView() {
        recyclerViewRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
        routeAdapter = new RouteAdapter(routeList, this::navigateToMap, this::toggleRouteDetails);
        recyclerViewRoutes.setAdapter(routeAdapter);
    }

    private void setupClickListeners() {
        if (buttonSearchRoute != null) {
            buttonSearchRoute.setOnClickListener(v -> searchRoutes());
        }

        if (buttonMapView != null) {
            buttonMapView.setOnClickListener(v -> {
                if (routeList.isEmpty()) {
                    showToast("경로를 먼저 검색해주세요.");
                } else {
                    openMapView(routeList.get(0));
                }
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

    private void navigateToMap(RouteInfo route) {
        openMapView(route);
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
                            mainHandler.post(() -> editStartLocation.setText(address));
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
        return "현재 위치";
    }

    private Location getCoordinatesFromAddress(String addressString) {
        if (addressString == null || addressString.trim().isEmpty()) {
            return null;
        }

        try {
            List<Address> addresses = geocoder.getFromLocationName(addressString.trim(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                Location location = new Location("geocoded");
                location.setLatitude(address.getLatitude());
                location.setLongitude(address.getLongitude());
                Log.d(TAG, "주소 변환 성공: " + addressString + " -> (" + location.getLatitude() + ", " + location.getLongitude() + ")");
                return location;
            }
        } catch (IOException e) {
            Log.e(TAG, "주소를 좌표로 변환 실패: " + addressString, e);
        }
        return null;
    }

    private void searchRoutes() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editEndLocation.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            showToast("출발지와 도착지를 모두 입력해주세요.");
            return;
        }

        updateRouteListVisibility(false, "경로를 탐색 중입니다...");
        routeList.clear();
        routeAdapter.notifyDataSetChanged();

        executorService.execute(() -> {
            try {
                Log.i(TAG, "=== 경로 탐색 시작 ===");

                Location start = getCoordinatesFromAddress(startAddress);
                Location end = getCoordinatesFromAddress(endAddress);

                if (start == null || end == null) {
                    mainHandler.post(() -> {
                        updateRouteListVisibility(true, "주소를 찾을 수 없습니다.");
                        showToast("주소를 다시 확인해주세요.");
                    });
                    return;
                }

                mainHandler.post(() -> {
                    this.startLocation = start;
                    this.endLocation = end;
                });

                mainHandler.post(() -> {
                    searchComprehensiveBusRoutesAsync(start, end, new ComprehensiveRouteCallback() {
                        @Override
                        public void onSuccess(List<RouteInfo> routes) {
                            finalizeAndDisplayRoutes(routes);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            updateRouteListVisibility(true, "경로 탐색 중 오류가 발생했습니다: " + errorMessage);
                            showToast("경로 탐색에 실패했습니다.");
                        }
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "경로 탐색 중 예외 발생", e);
                mainHandler.post(() -> {
                    updateRouteListVisibility(true, "경로 탐색 중 오류가 발생했습니다.");
                });
            }
        });
    }

    /**
     * [개선] 확장된 정류장 검색 및 노선 검증 강화
     */
    private void searchComprehensiveBusRoutesAsync(Location startLocation, Location endLocation,
                                                   ComprehensiveRouteCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "=== 1단계: 확장된 출발지 주변 정류장 검색 ===");

                // [수정] 더 많은 정류장을 검색하고, 여러 반경으로 검색
                List<TagoBusStopResponse.BusStop> allStartStops = searchBusStopsInMultipleRadii(
                        startLocation.getLatitude(), startLocation.getLongitude(), "출발지");

                if (allStartStops.isEmpty()) {
                    mainHandler.post(() -> callback.onError("출발지 주변 정류장을 찾을 수 없습니다"));
                    return;
                }

                Log.i(TAG, "출발지 주변 정류장 " + allStartStops.size() + "개 발견");

                Log.d(TAG, "=== 2단계: 확장된 도착지 주변 정류장 검색 ===");

                List<TagoBusStopResponse.BusStop> allEndStops = searchBusStopsInMultipleRadii(
                        endLocation.getLatitude(), endLocation.getLongitude(), "도착지");

                if (allEndStops.isEmpty()) {
                    mainHandler.post(() -> callback.onError("도착지 주변 정류장을 찾을 수 없습니다"));
                    return;
                }

                Log.i(TAG, "도착지 주변 정류장 " + allEndStops.size() + "개 발견");

                // [새로 추가] 도착지 정류장 목록 상세 로깅
                logDestinationStops(allEndStops);

                Set<String> destinationKeywords = extractKeywordsFromStops(allEndStops);
                Log.d(TAG, "=== 도착지 키워드: " + destinationKeywords + " ===");

                List<RouteInfo> potentialRoutes = Collections.synchronizedList(new ArrayList<>());
                Set<String> processedRoutes = Collections.synchronizedSet(new HashSet<>());
                AtomicInteger pendingRequests = new AtomicInteger(0);
                AtomicInteger completedRequests = new AtomicInteger(0);

                Log.d(TAG, "=== 3단계: 강화된 버스 노선 분석 시작 ===");

                for (TagoBusStopResponse.BusStop startStop : allStartStops) {
                    if (startStop.citycode == null || startStop.nodeid == null) {
                        continue;
                    }

                    Log.d(TAG, "출발 정류장 분석: " + startStop.nodenm + " (ID: " + startStop.nodeid + ")");

                    List<TagoBusArrivalResponse.BusArrival> allBuses = getAllBusesAtStop(startStop);

                    if (allBuses.isEmpty()) {
                        Log.d(TAG, "정류장 " + startStop.nodenm + "에 도착 예정 버스 없음");
                        continue;
                    }

                    Log.i(TAG, "🚌 정류장 " + startStop.nodenm + "에서 총 " + allBuses.size() + "개 버스 발견");

                    for (TagoBusArrivalResponse.BusArrival bus : allBuses) {
                        if (bus.routeid == null || bus.routeno == null) {
                            continue;
                        }

                        String routeKey = bus.routeno + "_" + startStop.nodeid;
                        if (processedRoutes.contains(routeKey)) {
                            continue;
                        }

                        Log.d(TAG, "🔍 버스 노선 상세 분석: " + bus.routeno + "번");

                        // [개선] 강화된 노선 매칭
                        RouteMatchResult matchResult = findStrictRouteMatch(startStop, allEndStops, destinationKeywords, bus);

                        if (matchResult != null) {
                            Log.i(TAG, "✅ 검증된 경로 발견: " + bus.routeno + "번 -> " + matchResult.endStopBusStop.nodenm);

                            pendingRequests.incrementAndGet();
                            processedRoutes.add(routeKey);

                            mainHandler.post(() -> {
                                calculateRouteInfoWithRealWalkingTime(startLocation, endLocation,
                                        startStop, matchResult.endStopBusStop, bus,
                                        new RouteInfoCallback() {
                                            @Override
                                            public void onSuccess(RouteInfo routeInfo) {
                                                if (!isDuplicateRoute(potentialRoutes, routeInfo)) {
                                                    potentialRoutes.add(routeInfo);
                                                    Log.i(TAG, "🎯 경로 추가: " + routeInfo.getBusNumber() + "번 (총 " + routeInfo.getDuration() + "분)");
                                                }

                                                if (completedRequests.incrementAndGet() == pendingRequests.get()) {
                                                    finalizeRoutes(potentialRoutes, callback);
                                                }
                                            }

                                            @Override
                                            public void onError() {
                                                Log.w(TAG, "경로 정보 계산 실패: " + bus.routeno + "번");

                                                if (completedRequests.incrementAndGet() == pendingRequests.get()) {
                                                    finalizeRoutes(potentialRoutes, callback);
                                                }
                                            }
                                        });
                            });
                        }
                    }
                }

                if (pendingRequests.get() == 0) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                }

            } catch (Exception e) {
                Log.e(TAG, "포괄적 버스 탐색 중 예외 발생", e);
                mainHandler.post(() -> callback.onError("경로 탐색 중 오류 발생: " + e.getMessage()));
            }
        });
    }

    /**
     * [새로 추가] 여러 반경으로 정류장 검색하여 누락 방지
     */
    private List<TagoBusStopResponse.BusStop> searchBusStopsInMultipleRadii(double latitude, double longitude, String locationName) {
        List<TagoBusStopResponse.BusStop> allStops = new ArrayList<>();
        Set<String> uniqueStopIds = new HashSet<>();

        // 여러 반경과 numOfRows로 검색
        int[] radiusMeters = {500, 800, 1200}; // 다양한 반경
        int[] numOfRowsOptions = {30, 50, 100}; // 다양한 개수

        for (int radius : radiusMeters) {
            for (int numOfRows : numOfRowsOptions) {
                try {
                    Response<TagoBusStopResponse> response = tagoApiService.getNearbyBusStops(
                            BuildConfig.TAGO_API_KEY_DECODED,
                            latitude, longitude,
                            numOfRows, 1, "json"
                    ).execute();

                    if (isValidResponse(response, locationName + " 정류장 검색")) {
                        List<TagoBusStopResponse.BusStop> stops = response.body().response.body.items.item;

                        for (TagoBusStopResponse.BusStop stop : stops) {
                            if (stop.nodeid != null && !uniqueStopIds.contains(stop.nodeid)) {
                                // 거리 체크 (반경 내에 있는지 확인)
                                double distance = calculateDistance(latitude, longitude, stop.gpslati, stop.gpslong);
                                if (distance <= radius) {
                                    uniqueStopIds.add(stop.nodeid);
                                    allStops.add(stop);
                                    Log.v(TAG, String.format("%s 정류장 추가: %s (거리: %.0fm)",
                                            locationName, stop.nodenm, distance));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, locationName + " 정류장 검색 실패 - radius: " + radius + ", numOfRows: " + numOfRows, e);
                }
            }
        }

        Log.i(TAG, locationName + " 최종 정류장 수: " + allStops.size() + "개");
        return allStops;
    }

    /**
     * [새로 추가] 도착지 정류장 목록 상세 로깅
     */
    private void logDestinationStops(List<TagoBusStopResponse.BusStop> endStops) {
        Log.d(TAG, "=== 도착지 정류장 목록 ===");
        for (TagoBusStopResponse.BusStop stop : endStops) {
            Log.d(TAG, String.format("- %s (ID: %s)", stop.nodenm, stop.nodeid));
        }
        Log.d(TAG, "=== 도착지 정류장 목록 끝 ===");
    }

    /**
     * [개선] 더 엄격한 노선 매칭 - 실제 노선 경유지 검증 강화
     */
    private RouteMatchResult findStrictRouteMatch(TagoBusStopResponse.BusStop startStop,
                                                  List<TagoBusStopResponse.BusStop> endStops,
                                                  Set<String> destinationKeywords,
                                                  TagoBusArrivalResponse.BusArrival bus) {
        try {
            Log.d(TAG, "🔍 " + bus.routeno + "번 버스 노선 검증 시작");

            Response<TagoBusRouteStationResponse> routeResponse = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    bus.routeid,
                    200, 1, "json"
            ).execute();

            if (!isValidResponse(routeResponse, "버스 노선 정보")) {
                Log.w(TAG, bus.routeno + "번 버스 노선 정보 조회 실패");
                return null;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations = routeResponse.body().response.body.items.item;
            Log.d(TAG, bus.routeno + "번 버스가 경유하는 정류장 " + routeStations.size() + "개");

            // [새로 추가] 노선의 모든 정류장 로깅 (디버깅용)
            logRouteStations(bus.routeno, routeStations);

            // 1. 정확한 정류장 이름 매칭 (가장 높은 우선순위)
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                for (TagoBusRouteStationResponse.RouteStation routeStation : routeStations) {
                    if (endStop.nodenm != null && routeStation.nodenm != null &&
                            endStop.nodenm.equals(routeStation.nodenm)) {
                        Log.i(TAG, "✅ " + bus.routeno + "번: 정확한 이름 매칭 - " + endStop.nodenm);
                        return new RouteMatchResult(endStop);
                    }
                }
            }

            // 2. 정규화된 이름 매칭 (공백, 특수문자 제거 후 비교)
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                String normalizedEndStop = normalizeStopName(endStop.nodenm);
                for (TagoBusRouteStationResponse.RouteStation routeStation : routeStations) {
                    String normalizedRouteStation = normalizeStopName(routeStation.nodenm);
                    if (normalizedEndStop.equals(normalizedRouteStation)) {
                        Log.i(TAG, "✅ " + bus.routeno + "번: 정규화된 이름 매칭 - " + endStop.nodenm + " ↔ " + routeStation.nodenm);
                        return new RouteMatchResult(endStop);
                    }
                }
            }

            // 3. [개선] 더 엄격한 키워드 매칭 - 중요 키워드만
            Set<String> importantKeywords = getImportantKeywords(destinationKeywords);
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                for (TagoBusRouteStationResponse.RouteStation routeStation : routeStations) {
                    if (routeStation.nodenm != null) {
                        for (String keyword : importantKeywords) {
                            if (routeStation.nodenm.contains(keyword)) {
                                Log.i(TAG, "✅ " + bus.routeno + "번: 중요 키워드 매칭 - " + keyword + " in " + routeStation.nodenm);
                                return new RouteMatchResult(endStop);
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "❌ " + bus.routeno + "번: 매칭되는 도착지 정류장 없음");

        } catch (Exception e) {
            Log.e(TAG, "노선 매칭 중 예외: " + bus.routeno + "번", e);
        }

        return null;
    }

    /**
     * [새로 추가] 노선의 모든 정류장 로깅 (디버깅용)
     */
    private void logRouteStations(String busNumber, List<TagoBusRouteStationResponse.RouteStation> stations) {
        Log.d(TAG, "=== " + busNumber + "번 버스 경유 정류장 ===");
        for (int i = 0; i < Math.min(stations.size(), 10); i++) { // 처음 10개만
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            Log.d(TAG, String.format("  %d. %s", station.ord, station.nodenm));
        }
        if (stations.size() > 10) {
            Log.d(TAG, "  ... (총 " + stations.size() + "개 정류장)");
        }
        Log.d(TAG, "=== " + busNumber + "번 경유 정류장 끝 ===");
    }

    /**
     * [새로 추가] 정류장 이름 정규화
     */
    private String normalizeStopName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\s·.-]", "").toLowerCase();
    }

    /**
     * [새로 추가] 중요한 키워드만 필터링 (너무 관대한 매칭 방지)
     */
    private Set<String> getImportantKeywords(Set<String> allKeywords) {
        Set<String> important = new HashSet<>();

        // 길이가 3글자 이상이고 중요한 키워드만
        for (String keyword : allKeywords) {
            if (keyword.length() >= 3) {
                // 터미널, 병원, 대학교 등 중요한 시설
                if (keyword.contains("터미널") || keyword.contains("병원") ||
                        keyword.contains("대학교") || keyword.contains("시청") ||
                        keyword.contains("역") || keyword.contains("공항")) {
                    important.add(keyword);
                }
            }
        }

        Log.d(TAG, "중요 키워드 필터링: " + allKeywords + " -> " + important);
        return important;
    }

    /**
     * [새로 추가] 두 지점 간 거리 계산 (미터 단위)
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000; // 지구 반지름 (미터)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    // [기존 메서드들은 그대로 유지]
    private void calculateRouteInfoWithRealWalkingTime(Location startLocation, Location endLocation,
                                                       TagoBusStopResponse.BusStop startStop,
                                                       TagoBusStopResponse.BusStop endStop,
                                                       TagoBusArrivalResponse.BusArrival bus,
                                                       RouteInfoCallback callback) {

        if (!isValidTmapApiKey()) {
            Log.w(TAG, "TMAP API 키가 유효하지 않음, 기본값 사용");
            createRouteInfo(startLocation, endLocation, startStop, endStop, bus, 5, 5, callback);
            return;
        }

        AtomicInteger completedCalls = new AtomicInteger(0);
        AtomicInteger walkToStartMin = new AtomicInteger(5);
        AtomicInteger walkToEndMin = new AtomicInteger(5);

        calculateWalkingTime(
                startLocation.getLongitude(), startLocation.getLatitude(),
                startStop.gpslong, startStop.gpslati,
                "출발지", startStop.nodenm,
                new WalkingTimeCallback() {
                    @Override
                    public void onSuccess(int walkingTimeMinutes) {
                        walkToStartMin.set(walkingTimeMinutes);
                        if (completedCalls.incrementAndGet() == 2) {
                            createRouteInfo(startLocation, endLocation, startStop, endStop, bus,
                                    walkToStartMin.get(), walkToEndMin.get(), callback);
                        }
                    }

                    @Override
                    public void onError() {
                        if (completedCalls.incrementAndGet() == 2) {
                            createRouteInfo(startLocation, endLocation, startStop, endStop, bus,
                                    walkToStartMin.get(), walkToEndMin.get(), callback);
                        }
                    }
                }
        );

        calculateWalkingTime(
                endStop.gpslong, endStop.gpslati,
                endLocation.getLongitude(), endLocation.getLatitude(),
                endStop.nodenm, "도착지",
                new WalkingTimeCallback() {
                    @Override
                    public void onSuccess(int walkingTimeMinutes) {
                        walkToEndMin.set(walkingTimeMinutes);
                        if (completedCalls.incrementAndGet() == 2) {
                            createRouteInfo(startLocation, endLocation, startStop, endStop, bus,
                                    walkToStartMin.get(), walkToEndMin.get(), callback);
                        }
                    }

                    @Override
                    public void onError() {
                        if (completedCalls.incrementAndGet() == 2) {
                            createRouteInfo(startLocation, endLocation, startStop, endStop, bus,
                                    walkToStartMin.get(), walkToEndMin.get(), callback);
                        }
                    }
                }
        );
    }

    private int calculateWalkingTimeByDistance(double startLat, double startLng, double endLat, double endLng) {
        double earthRadius = 6371;
        double dLat = Math.toRadians(endLat - startLat);
        double dLng = Math.toRadians(endLng - startLng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadius * c;

        double walkingSpeedKmH = 4.0;
        double walkingTimeHours = distance / walkingSpeedKmH;
        int walkingTimeMinutes = (int) Math.ceil(walkingTimeHours * 60);

        return Math.max(1, Math.min(30, walkingTimeMinutes));
    }

    private void calculateWalkingTime(double startLng, double startLat,
                                      double endLng, double endLat,
                                      String startName, String endName,
                                      WalkingTimeCallback callback) {

        int estimatedWalkingTime = calculateWalkingTimeByDistance(startLat, startLng, endLat, endLng);

        Call<TmapPedestrianResponse> call = tmapApiService.getPedestrianRoute(
                BuildConfig.TMAP_API_KEY,
                String.valueOf(startLng),
                String.valueOf(startLat),
                String.valueOf(endLng),
                String.valueOf(endLat),
                startName,
                endName
        );

        call.enqueue(new Callback<TmapPedestrianResponse>() {
            @Override
            public void onResponse(@NonNull Call<TmapPedestrianResponse> call,
                                   @NonNull Response<TmapPedestrianResponse> response) {

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        TmapPedestrianResponse tmapResponse = response.body();
                        int walkingTimeMinutes = extractWalkingTimeFromResponse(tmapResponse);

                        if (walkingTimeMinutes > 0) {
                            Log.d(TAG, String.format("TMAP 도보 시간 계산 성공: %s → %s = %d분",
                                    startName, endName, walkingTimeMinutes));
                            callback.onSuccess(walkingTimeMinutes);
                            return;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, String.format("TMAP 응답 파싱 실패: %s → %s, 거리 기반 계산 사용",
                                startName, endName), e);
                    }
                }

                Log.d(TAG, String.format("TMAP API 실패, 거리 기반 도보 시간 사용: %s → %s = %d분",
                        startName, endName, estimatedWalkingTime));
                callback.onSuccess(estimatedWalkingTime);
            }

            @Override
            public void onFailure(@NonNull Call<TmapPedestrianResponse> call, @NonNull Throwable t) {
                Log.w(TAG, String.format("TMAP API 네트워크 오류: %s → %s, 거리 기반 계산 사용",
                        startName, endName), t);
                callback.onSuccess(estimatedWalkingTime);
            }
        });
    }

    private int extractWalkingTimeFromResponse(TmapPedestrianResponse response) {
        try {
            if (response == null || response.getFeatures() == null) {
                return 0;
            }

            for (TmapPedestrianResponse.Feature feature : response.getFeatures()) {
                if (feature != null && feature.getProperties() != null) {
                    int totalTimeSeconds = feature.getProperties().getTotalTime();

                    if (totalTimeSeconds > 0) {
                        int totalTimeMinutes = (int) Math.ceil(totalTimeSeconds / 60.0);
                        return Math.max(1, Math.min(30, totalTimeMinutes));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "TMAP 응답 파싱 중 예외 발생", e);
        }

        return 0;
    }

    private boolean isValidTmapApiKey() {
        try {
            String apiKey = BuildConfig.TMAP_API_KEY;
            return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("your_tmap_api_key_here");
        } catch (Exception e) {
            Log.e(TAG, "TMAP API 키 확인 중 오류", e);
            return false;
        }
    }

    private void createRouteInfo(Location startLocation, Location endLocation,
                                 TagoBusStopResponse.BusStop startStop,
                                 TagoBusStopResponse.BusStop endStop,
                                 TagoBusArrivalResponse.BusArrival bus,
                                 int walkToStartMin, int walkToEndMin,
                                 RouteInfoCallback callback) {
        try {
            int busWaitMin = Math.max(1, bus.arrtime / 60);
            int busRideMin = DEFAULT_BUS_RIDE_TIME_MIN;
            int totalDurationMin = walkToStartMin + busWaitMin + busRideMin + walkToEndMin;

            RouteInfo routeInfo = new RouteInfo(
                    "대중교통",
                    totalDurationMin,
                    busWaitMin,
                    bus.routeno,
                    startStop.nodenm,
                    endStop.nodenm
            );

            routeInfo.setWalkingTimeToStartStop(walkToStartMin);
            routeInfo.setBusRideTime(busRideMin);
            routeInfo.setWalkingTimeToDestination(walkToEndMin);

            Log.i(TAG, String.format("경로 정보 생성 완료: %s번 버스, 총 %d분 (도보: %d+%d분, 대기: %d분, 버스: %d분)",
                    bus.routeno, totalDurationMin, walkToStartMin, walkToEndMin, busWaitMin, busRideMin));

            callback.onSuccess(routeInfo);

        } catch (Exception e) {
            Log.e(TAG, "경로 정보 생성 중 예외", e);
            callback.onError();
        }
    }

    private void finalizeRoutes(List<RouteInfo> potentialRoutes, ComprehensiveRouteCallback callback) {
        List<RouteInfo> sortedRoutes = new ArrayList<>(potentialRoutes);
        sortedRoutes.sort(Comparator.comparingInt(RouteInfo::getDuration));

        if (sortedRoutes.size() > MAX_ROUTES_TO_SHOW) {
            sortedRoutes = sortedRoutes.subList(0, MAX_ROUTES_TO_SHOW);
        }

        Log.i(TAG, "=== 경로 탐색 완료: " + sortedRoutes.size() + "개 경로 발견 ===");
        callback.onSuccess(sortedRoutes);
    }

    private List<TagoBusArrivalResponse.BusArrival> getAllBusesAtStop(TagoBusStopResponse.BusStop stop) {
        List<TagoBusArrivalResponse.BusArrival> allBuses = new ArrayList<>();
        Set<String> uniqueBusIds = new HashSet<>();

        try {
            int[] rowCounts = {30, 50, 100};

            for (int numOfRows : rowCounts) {
                for (int page = 1; page <= MAX_API_PAGES; page++) {
                    try {
                        Response<TagoBusArrivalResponse> response = tagoApiService.getBusArrivalInfo(
                                BuildConfig.TAGO_API_KEY_DECODED,
                                stop.citycode,
                                stop.nodeid,
                                numOfRows, page, "json"
                        ).execute();

                        if (!isValidArrivalResponse(response)) {
                            break;
                        }

                        TagoBusArrivalResponse.ItemsContainer itemsContainer = null;
                        try {
                            itemsContainer = new Gson().fromJson(response.body().response.body.items, TagoBusArrivalResponse.ItemsContainer.class);
                        } catch (Exception e) {
                            Log.w(TAG, "JSON 파싱 실패 - numOfRows: " + numOfRows + ", page: " + page);
                            continue;
                        }

                        if (itemsContainer == null || itemsContainer.item == null) {
                            break;
                        }

                        for (TagoBusArrivalResponse.BusArrival bus : itemsContainer.item) {
                            if (bus.routeid != null && bus.routeno != null) {
                                String busKey = bus.routeno + "_" + bus.routeid;
                                if (!uniqueBusIds.contains(busKey)) {
                                    uniqueBusIds.add(busKey);
                                    allBuses.add(bus);
                                    Log.v(TAG, "새 버스 추가: " + bus.routeno + "번 (페이지: " + page + ", rows: " + numOfRows + ")");
                                }
                            }
                        }

                        if (itemsContainer.item.size() < numOfRows) {
                            break;
                        }

                    } catch (Exception e) {
                        Log.w(TAG, "API 호출 실패 - page: " + page + ", numOfRows: " + numOfRows, e);
                        break;
                    }
                }
            }

            Log.d(TAG, "🚌 정류장 " + stop.nodenm + "에서 최종 " + allBuses.size() + "개 고유 버스 수집 완료");

        } catch (Exception e) {
            Log.e(TAG, "버스 정보 수집 중 예외 발생", e);
        }

        return allBuses;
    }

    private Set<String> extractKeywordsFromStops(List<TagoBusStopResponse.BusStop> stops) {
        Set<String> keywords = new HashSet<>();

        for (TagoBusStopResponse.BusStop stop : stops) {
            if (stop.nodenm != null && !stop.nodenm.trim().isEmpty()) {
                String[] words = stop.nodenm.split("[\\s·.]");
                for (String word : words) {
                    String cleanWord = word.trim();
                    if (cleanWord.length() >= 2) {
                        keywords.add(cleanWord);
                    }
                }
            }
        }

        return keywords;
    }

    private static class RouteMatchResult {
        TagoBusStopResponse.BusStop endStopBusStop;

        RouteMatchResult(TagoBusStopResponse.BusStop endStopBusStop) {
            this.endStopBusStop = endStopBusStop;
        }
    }

    private boolean isDuplicateRoute(List<RouteInfo> existingRoutes, RouteInfo newRoute) {
        for (RouteInfo existing : existingRoutes) {
            if (existing.getBusNumber().equals(newRoute.getBusNumber()) &&
                    existing.getStartStopName().equals(newRoute.getStartStopName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidResponse(Response<?> response, String apiName) {
        if (!response.isSuccessful() || response.body() == null) {
            return false;
        }

        if (response.body() instanceof TagoBusStopResponse) {
            TagoBusStopResponse body = (TagoBusStopResponse) response.body();
            return body.response != null &&
                    body.response.body != null &&
                    body.response.body.items != null &&
                    body.response.body.items.item != null &&
                    !body.response.body.items.item.isEmpty();
        }

        if (response.body() instanceof TagoBusRouteStationResponse) {
            TagoBusRouteStationResponse body = (TagoBusRouteStationResponse) response.body();
            return body.response != null &&
                    body.response.body != null &&
                    body.response.body.items != null &&
                    body.response.body.items.item != null&&
                    !body.response.body.items.item.isEmpty();
        }

        return true;
    }

    private boolean isValidArrivalResponse(Response<TagoBusArrivalResponse> response) {
        if (!response.isSuccessful() || response.body() == null) {
            return false;
        }

        TagoBusArrivalResponse body = response.body();
        return body.response != null &&
                body.response.body != null &&
                body.response.body.items != null &&
                !body.response.body.items.isJsonNull();
    }

    private void finalizeAndDisplayRoutes(List<RouteInfo> routes) {
        if (routes == null || routes.isEmpty()) {
            updateRouteListVisibility(true, "이용 가능한 대중교통 경로를 찾을 수 없습니다.");
        } else {
            routeList.clear();
            routeList.addAll(routes);
            routeAdapter.notifyDataSetChanged();
            updateRouteListVisibility(false, "");
            Log.i(TAG, "경로 탐색 완료: " + routes.size() + "개 경로 표시");

            for (int i = 0; i < routes.size(); i++) {
                RouteInfo route = routes.get(i);
                Log.d(TAG, String.format("경로 %d: %s번 버스, %d분 소요 (%s -> %s)",
                        i + 1, route.getBusNumber(), route.getDuration(),
                        route.getStartStopName(), route.getEndStopName()));
            }
        }
    }

    private void updateRouteListVisibility(boolean noRoutes, String message) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (textNoRoutes != null && recyclerViewRoutes != null) {
                textNoRoutes.setVisibility(noRoutes ? View.VISIBLE : View.GONE);
                recyclerViewRoutes.setVisibility(noRoutes ? View.GONE : View.VISIBLE);
                if (noRoutes && !message.isEmpty()) {
                    textNoRoutes.setText(message);
                }
            }
        });
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void openMapView(RouteInfo route) {
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
                ((MainActivity) getActivity()).getSupportActionBar().setTitle("경로 지도");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCurrentLocation();
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

    // 콜백 인터페이스들
    interface WalkingTimeCallback {
        void onSuccess(int walkingTimeMinutes);
        void onError();
    }

    interface RouteInfoCallback {
        void onSuccess(RouteInfo routeInfo);
        void onError();
    }

    interface ComprehensiveRouteCallback {
        void onSuccess(List<RouteInfo> routes);
        void onError(String errorMessage);
    }

    // RouteInfo 클래스 및 어댑터는 기존과 동일하므로 생략...
    public static class RouteInfo implements Serializable {
        private String routeType, busNumber, startStopName, endStopName;
        private int duration, busWaitTime, busRideTime, walkingTimeToStartStop, walkingTimeToDestination;
        private boolean isExpanded = false;
        private List<List<Double>> pathCoordinates;

        public RouteInfo(String type, int totalDuration, int waitTime, String busNum, String startStop, String endStop) {
            this.routeType = type; this.duration = totalDuration; this.busWaitTime = waitTime;
            this.busNumber = busNum; this.startStopName = startStop; this.endStopName = endStop;
        }

        // Getter/Setter 메서드들은 기존과 동일
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
        public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
        public void setPathCoordinates(List<List<Double>> pathCoordinates) { this.pathCoordinates = pathCoordinates; }

        public String getRouteSummary() {
            int totalWalkTime = walkingTimeToStartStop + walkingTimeToDestination;
            return String.format("총 %d분 소요 (도보 %d분 + 대기 %d분 + 버스 %d분)",
                    duration, totalWalkTime, busWaitTime, busRideTime);
        }

        public String getDepartureTimeInfo() {
            return String.format("약 %d분 후 버스 도착", busWaitTime);
        }

        public String getDetailedRouteInfo() {
            return String.format("%s번 버스 (%s → %s)", busNumber, startStopName, endStopName);
        }
    }

    interface RouteInteractionListener { void onNavigate(RouteInfo route); }
    interface DetailToggleListener { void onToggle(int position); }

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
            holder.textRouteType.setText(route.getDetailedRouteInfo());
            holder.textRouteSummary.setText(route.getRouteSummary());
            holder.textDepartureTime.setText(route.getDepartureTimeInfo());
            holder.layoutRouteDetail.setVisibility(route.isExpanded() ? View.VISIBLE : View.GONE);
            holder.buttonExpandRoute.setText(route.isExpanded() ? "간략히 보기" : "상세 보기");
        }

        @Override public int getItemCount() { return routes.size(); }

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

                buttonExpandRoute.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) detailListener.onToggle(position);
                });

                buttonStartNavigation.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) listener.onNavigate(routes.get(position));
                });
            }
        }
    }
}