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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 간단한 버스 경로 탐색 - 방향 체크 없이 노선이 출발지와 도착지를 모두 지나가는지만 확인
 */
public class RouteFragment extends Fragment {

    private static final String TAG = "RouteEngine";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private static final String TMAP_API_BASE_URL = "https://apis.openapi.sk.com/";
    private static final String TAGO_API_BASE_URL = "https://apis.data.go.kr/1613000/";

    private static final int MAX_ROUTES_TO_SHOW = 10;
    private static final int DEFAULT_BUS_RIDE_TIME_MIN = 15;
    private static final int MAX_API_PAGES = 5;

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

                List<RouteInfo> routes = searchSimpleBusRoutes(start, end);
                mainHandler.post(() -> finalizeAndDisplayRoutes(routes));

            } catch (Exception e) {
                Log.e(TAG, "경로 탐색 중 예외 발생", e);
                mainHandler.post(() -> {
                    updateRouteListVisibility(true, "경로 탐색 중 오류가 발생했습니다.");
                });
            }
        });
    }

    /**
     * 간단한 버스 경로 탐색 - 방향 체크 없이 노선이 출발지와 도착지를 모두 지나가는지만 확인
     */
    private List<RouteInfo> searchSimpleBusRoutes(Location startLocation, Location endLocation) {
        List<RouteInfo> potentialRoutes = new ArrayList<>();

        try {
            Log.d(TAG, "=== 1단계: 출발지 주변 정류장 검색 ===");

            Response<TagoBusStopResponse> startStopsResponse = tagoApiService.getNearbyBusStops(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startLocation.getLatitude(),
                    startLocation.getLongitude(),
                    30, 1, "json"
            ).execute();

            if (!isValidResponse(startStopsResponse, "출발지 주변 정류장")) {
                return potentialRoutes;
            }

            List<TagoBusStopResponse.BusStop> startStops = startStopsResponse.body().response.body.items.item;
            Log.i(TAG, "출발지 주변 정류장 " + startStops.size() + "개 발견");

            Log.d(TAG, "=== 2단계: 도착지 주변 정류장 검색 ===");

            Response<TagoBusStopResponse> endStopsResponse = tagoApiService.getNearbyBusStops(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    endLocation.getLatitude(),
                    endLocation.getLongitude(),
                    30, 1, "json"
            ).execute();

            if (!isValidResponse(endStopsResponse, "도착지 주변 정류장")) {
                return potentialRoutes;
            }

            List<TagoBusStopResponse.BusStop> endStops = endStopsResponse.body().response.body.items.item;
            Log.i(TAG, "도착지 주변 정류장 " + endStops.size() + "개 발견");

            Set<String> destinationKeywords = extractKeywordsFromStops(endStops);
            Set<String> processedRoutes = new HashSet<>();

            Log.d(TAG, "=== 3단계: 버스 경로 분석 시작 ===");

            for (TagoBusStopResponse.BusStop startStop : startStops) {
                if (startStop.citycode == null || startStop.nodeid == null) {
                    continue;
                }

                Log.d(TAG, "출발 정류장 분석: " + startStop.nodenm + " (ID: " + startStop.nodeid + ")");

                List<TagoBusArrivalResponse.BusArrival> allBuses = getAllBusesAtStop(startStop);

                if (allBuses.isEmpty()) {
                    Log.d(TAG, "정류장 " + startStop.nodenm + "에 도착 예정 버스 없음");
                    continue;
                }

                Log.i(TAG, "정류장 " + startStop.nodenm + "에서 총 " + allBuses.size() + "개 버스 발견");

                for (TagoBusArrivalResponse.BusArrival bus : allBuses) {
                    if (bus.routeid == null || bus.routeno == null) {
                        continue;
                    }

                    String routeKey = bus.routeno + "_" + startStop.nodeid;
                    if (processedRoutes.contains(routeKey)) {
                        continue;
                    }

                    Log.d(TAG, "버스 노선 분석: " + bus.routeno + "번");

                    RouteMatchResult matchResult = findSimpleRouteMatch(startStop, endStops, destinationKeywords, bus);

                    if (matchResult != null) {
                        Log.i(TAG, "경로 발견: " + bus.routeno + "번 -> " + matchResult.endStopBusStop.nodenm);

                        RouteInfo routeInfo = calculateSafeRouteInfo(startLocation, endLocation,
                                startStop, matchResult.endStopBusStop, bus);

                        if (routeInfo != null && !isDuplicateRoute(potentialRoutes, routeInfo)) {
                            potentialRoutes.add(routeInfo);
                            processedRoutes.add(routeKey);
                            Log.i(TAG, "경로 추가: " + routeInfo.getBusNumber() + "번 (총 " + routeInfo.getDuration() + "분)");
                        }
                    } else {
                        Log.d(TAG, "경로 매칭 실패: " + bus.routeno + "번");
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "버스 탐색 중 예외 발생", e);
        }

        potentialRoutes.sort(Comparator.comparingInt(RouteInfo::getDuration));
        if (potentialRoutes.size() > MAX_ROUTES_TO_SHOW) {
            potentialRoutes = potentialRoutes.subList(0, MAX_ROUTES_TO_SHOW);
        }

        Log.i(TAG, "=== 경로 탐색 완료: " + potentialRoutes.size() + "개 경로 발견 ===");
        return potentialRoutes;
    }

    /**
     * 간단한 경로 매칭 - 방향 체크 없이 노선이 출발지와 도착지를 모두 지나가는지만 확인
     */
    private RouteMatchResult findSimpleRouteMatch(TagoBusStopResponse.BusStop startStop,
                                                  List<TagoBusStopResponse.BusStop> endStops,
                                                  Set<String> destinationKeywords,
                                                  TagoBusArrivalResponse.BusArrival bus) {
        try {
            Response<TagoBusRouteStationResponse> routeResponse = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    bus.routeid,
                    200, 1, "json"
            ).execute();

            if (!isValidResponse(routeResponse, "버스 노선 정보")) {
                return null;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations = routeResponse.body().response.body.items.item;
            Log.v(TAG, bus.routeno + "번 노선 총 " + routeStations.size() + "개 정류장");

            // 출발지가 노선에 있는지 확인
            boolean hasStartStop = routeStations.stream()
                    .anyMatch(station -> startStop.nodenm != null && startStop.nodenm.equals(station.nodenm));

            if (!hasStartStop) {
                Log.v(TAG, "출발지 '" + startStop.nodenm + "'가 " + bus.routeno + "번 노선에 없음");
                return null;
            }

            // 1. 정확한 이름 매칭
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                boolean hasEndStop = routeStations.stream()
                        .anyMatch(station -> endStop.nodenm != null && endStop.nodenm.equals(station.nodenm));

                if (hasEndStop) {
                    Log.i(TAG, "경로 매칭 성공 [정확매칭]: " + bus.routeno + "번");
                    Log.i(TAG, "   출발: " + startStop.nodenm);
                    Log.i(TAG, "   도착: " + endStop.nodenm);
                    return new RouteMatchResult(endStop);
                }
            }

            // 2. 키워드 기반 매칭
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                for (String keyword : destinationKeywords) {
                    boolean hasKeywordStop = routeStations.stream()
                            .anyMatch(station -> station.nodenm != null && station.nodenm.contains(keyword) && keyword.length() >= 2);

                    if (hasKeywordStop) {
                        Log.i(TAG, "경로 매칭 성공 [키워드매칭]: " + bus.routeno + "번");
                        Log.i(TAG, "   출발: " + startStop.nodenm);
                        Log.i(TAG, "   키워드: " + keyword);
                        return new RouteMatchResult(endStop);
                    }
                }
            }

            // 3. 부분 문자열 매칭
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                if (endStop.nodenm != null) {
                    String endStopSimple = endStop.nodenm.replaceAll("[\\s·.-]", "");

                    boolean hasSimilarStop = routeStations.stream()
                            .anyMatch(station -> {
                                if (station.nodenm != null) {
                                    String stationSimple = station.nodenm.replaceAll("[\\s·.-]", "");
                                    return endStopSimple.length() >= 3 && stationSimple.length() >= 3 &&
                                            (endStopSimple.contains(stationSimple) || stationSimple.contains(endStopSimple));
                                }
                                return false;
                            });

                    if (hasSimilarStop) {
                        Log.i(TAG, "경로 매칭 성공 [유사매칭]: " + bus.routeno + "번");
                        Log.i(TAG, "   출발: " + startStop.nodenm);
                        Log.i(TAG, "   유사 도착지: " + endStop.nodenm);
                        return new RouteMatchResult(endStop);
                    }
                }
            }

            Log.v(TAG, "경로 매칭 실패: " + bus.routeno + "번 (도착지를 지나가지 않음)");

        } catch (Exception e) {
            Log.e(TAG, "경로 매칭 중 예외: " + bus.routeno + "번", e);
        }

        return null;
    }

    /**
     * 간단한 경로 매칭 결과
     */
    private static class RouteMatchResult {
        TagoBusStopResponse.BusStop endStopBusStop;

        RouteMatchResult(TagoBusStopResponse.BusStop endStopBusStop) {
            this.endStopBusStop = endStopBusStop;
        }
    }

    /**
     * 정류장의 모든 버스 정보를 여러 API 호출로 가져오기
     */
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

            Log.d(TAG, "정류장 " + stop.nodenm + "에서 최종 " + allBuses.size() + "개 고유 버스 수집 완료");

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

    private RouteInfo calculateSafeRouteInfo(Location startLocation, Location endLocation,
                                             TagoBusStopResponse.BusStop startStop,
                                             TagoBusStopResponse.BusStop endStop,
                                             TagoBusArrivalResponse.BusArrival bus) {
        try {
            int walkToStartMin = 5;
            int walkToEndMin = 5;
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

            return routeInfo;

        } catch (Exception e) {
            Log.e(TAG, "경로 정보 계산 중 예외", e);
            return null;
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

    // RouteInfo, 어댑터 등 나머지 클래스들...
    public static class RouteInfo implements Serializable {
        private String routeType, busNumber, startStopName, endStopName;
        private int duration, busWaitTime, busRideTime, walkingTimeToStartStop, walkingTimeToDestination;
        private boolean isExpanded = false;
        private List<List<Double>> pathCoordinates;

        public RouteInfo(String type, int totalDuration, int waitTime, String busNum, String startStop, String endStop) {
            this.routeType = type; this.duration = totalDuration; this.busWaitTime = waitTime;
            this.busNumber = busNum; this.startStopName = startStop; this.endStopName = endStop;
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