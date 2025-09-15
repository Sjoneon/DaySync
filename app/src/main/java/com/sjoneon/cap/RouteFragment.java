// app/src/main/java/com/sjoneon/cap/RouteFragment.java

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
import androidx.core.content.ContextCompat;
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
import java.util.Calendar;
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
 * 회차 방향 문제와 방향 정보 표시 문제가 모두 해결된 버스 경로 탐색 시스템
 */
public class RouteFragment extends Fragment {

    // ================================================================================================
    // 1. 상수 정의
    // ================================================================================================

    private static final String TAG = "RouteEngine";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private static final String TMAP_API_BASE_URL = "https://apis.openapi.sk.com/";
    private static final String TAGO_API_BASE_URL = "https://apis.data.go.kr/1613000/";

    private static final int AVERAGE_BUS_SPEED_BETWEEN_STOPS_MIN = 2;
    private static final int MAX_ROUTES_TO_SHOW = 10;
    private static final int DEFAULT_BUS_RIDE_TIME_MIN = 15;
    private static final int MAX_API_PAGES = 5;
    private static final int NEARBY_STOPS_COUNT = 50;
    private static final int EXTENDED_SEARCH_RADIUS = 1000;

    // 새로운 버스 탑승 시간 계산 관련 상수
    private static final double DISTANCE_MULTIPLIER = 1.3;
    private static final int BUS_AVERAGE_SPEED_M_PER_MIN = 200;
    private static final int MIN_BUS_RIDE_TIME = 2;
    private static final int MAX_BUS_RIDE_TIME = 50;
    private static final double MINUTES_PER_STOP = 1.8;

    // ================================================================================================
    // 2. 멤버 변수
    // ================================================================================================

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

    private LinearLayout layoutLoading;

    private Location startLocation, endLocation;

    // ================================================================================================
    // 3. 생명주기 메서드
    // ================================================================================================

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
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

    // ================================================================================================
    // 4. 초기화 메서드들
    // ================================================================================================

    private void initializeViews(View view) {
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editEndLocation = view.findViewById(R.id.editEndLocation);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);
        buttonMapView = view.findViewById(R.id.buttonMapView);
        textNoRoutes = view.findViewById(R.id.textNoRoutes);
        recyclerViewRoutes = view.findViewById(R.id.recyclerViewRoutes);
        layoutLoading = view.findViewById(R.id.layoutLoading);
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
            buttonSearchRoute.setOnClickListener(v -> {
                showLoading(true);
                searchRoutes();
            });
        }

        if (buttonMapView != null) {
            buttonMapView.setOnClickListener(v -> {
                if (routeList.isEmpty()) {
                    showToast("경로를 먼저 검색해주세요.");
                } else {
                    navigateToMap(null);
                }
            });
        }
    }

    private void showLoading(boolean show) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void loadCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null && geocoder != null) {
                        try {
                            List<Address> addresses = geocoder.getFromLocation(
                                    location.getLatitude(), location.getLongitude(), 1);
                            if (!addresses.isEmpty()) {
                                Address address = addresses.get(0);
                                String currentAddress = address.getAddressLine(0);
                                if (currentAddress != null && editStartLocation != null) {
                                    editStartLocation.setText(currentAddress);
                                }
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "현재 위치 주소 변환 실패", e);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "현재 위치 가져오기 실패", e));
    }

    // ================================================================================================
    // 5. 경로 탐색 메서드들
    // ================================================================================================

    private void searchRoutes() {
        String startAddress = editStartLocation.getText().toString().trim();
        String endAddress = editEndLocation.getText().toString().trim();

        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            showToast("출발지와 도착지를 모두 입력해주세요.");
            showLoading(false);
            return;
        }

        updateRouteListVisibility(false, "경로를 탐색 중입니다...");
        routeList.clear();
        routeAdapter.notifyDataSetChanged();

        executorService.execute(() -> {
            try {
                Log.i(TAG, "=== 🚌 회차 방향성 및 방향 정보 문제 해결된 경로 탐색 시작 ===");

                Location start = getCoordinatesFromAddress(startAddress);
                Location end = getCoordinatesFromAddress(endAddress);

                if (start == null || end == null) {
                    mainHandler.post(() -> {
                        updateRouteListVisibility(true, "주소를 찾을 수 없습니다.");
                        showToast("주소를 다시 확인해주세요.");
                        showLoading(false);
                    });
                    return;
                }

                mainHandler.post(() -> {
                    this.startLocation = start;
                    this.endLocation = end;
                });

                searchComprehensiveBusRoutesAsync(start, end, new ComprehensiveRouteCallback() {
                    @Override
                    public void onSuccess(List<RouteInfo> routes) {
                        mainHandler.post(() -> finalizeAndDisplayRoutes(routes));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> {
                            updateRouteListVisibility(true, "경로 탐색 중 오류가 발생했습니다: " + errorMessage);
                            showToast("경로 탐색에 실패했습니다.");
                            showLoading(false);
                        });
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "경로 탐색 중 예외 발생", e);
                mainHandler.post(() -> {
                    updateRouteListVisibility(true, "경로 탐색 중 오류가 발생했습니다.");
                    showToast("경로 탐색에 실패했습니다.");
                    showLoading(false);
                });
            }
        });
    }

    private Location getCoordinatesFromAddress(String address) {
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (!addresses.isEmpty()) {
                Address addr = addresses.get(0);
                Location location = new Location("geocoder");
                location.setLatitude(addr.getLatitude());
                location.setLongitude(addr.getLongitude());
                return location;
            }
        } catch (IOException e) {
            Log.e(TAG, "주소 -> 좌표 변환 실패: " + address, e);
        }
        return null;
    }

    // ================================================================================================
    // 6. 종합 버스 경로 탐색 시스템
    // ================================================================================================

    private void searchComprehensiveBusRoutesAsync(Location startLocation, Location endLocation,
                                                   ComprehensiveRouteCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "=== 1단계: 출발지/도착지 근처 정류장 탐색 ===");

                List<TagoBusStopResponse.BusStop> allStartStops = searchBusStopsInMultipleRadii(
                        startLocation.getLatitude(), startLocation.getLongitude(), "출발지");
                List<TagoBusStopResponse.BusStop> allEndStops = searchBusStopsInMultipleRadii(
                        endLocation.getLatitude(), endLocation.getLongitude(), "도착지");

                if (allStartStops.isEmpty() || allEndStops.isEmpty()) {
                    callback.onError("근처에 버스 정류장이 없습니다.");
                    return;
                }

                Log.i(TAG, "출발지 근처 정류장: " + allStartStops.size() + "개, 도착지 근처 정류장: " + allEndStops.size() + "개");

                Log.d(TAG, "=== 2단계: 목적지 키워드 추출 ===");
                Set<String> destinationKeywords = extractKeywordsFromStops(allEndStops);
                Log.d(TAG, "추출된 키워드: " + destinationKeywords);

                searchBusRoutesWithEnhancedDirection(startLocation, endLocation, allStartStops, allEndStops, destinationKeywords, new RouteSearchCallback() {
                    @Override
                    public void onSuccess(List<RouteInfo> routes) {
                        callback.onSuccess(routes);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "종합 버스 경로 탐색 중 예외 발생", e);
                callback.onError("경로 탐색 중 오류 발생: " + e.getMessage());
            }
        });
    }

    // ================================================================================================
    // 7. 🆕 완전히 개선된 버스 경로 탐색 (모든 문제 해결)
    // ================================================================================================

    private void searchBusRoutesWithEnhancedDirection(Location startLocation, Location endLocation,
                                                      List<TagoBusStopResponse.BusStop> allStartStops,
                                                      List<TagoBusStopResponse.BusStop> allEndStops,
                                                      Set<String> destinationKeywords,
                                                      RouteSearchCallback callback) {

        executorService.execute(() -> {
            try {
                List<RouteInfo> potentialRoutes = Collections.synchronizedList(new ArrayList<>());
                Set<String> processedRoutes = Collections.synchronizedSet(new HashSet<>());
                AtomicInteger pendingRequests = new AtomicInteger(0);
                AtomicInteger completedRequests = new AtomicInteger(0);

                Log.d(TAG, "=== 3단계: 🚌 완전 개선된 회차 방향성 검증 포함 버스 노선 분석 ===");

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

                        // 🆕 개선된 방향성 검증 포함한 노선 매칭
                        RouteMatchResult matchResult = findDirectionalRouteMatchEnhanced(
                                startStop, allEndStops, destinationKeywords, bus);

                        if (matchResult != null) {
                            // 🆕 완전히 개선된 회차 방향성 검증 (핵심 해결 부분)
                            boolean isCorrectDirection = validateRouteDirectionEnhanced(
                                    startLocation, endLocation,
                                    startStop, matchResult.endStopBusStop, bus);

                            if (isCorrectDirection) {
                                Log.i(TAG, "✅ 🎯 완전 검증된 회차 방향성 통과: " + bus.routeno + "번 -> " +
                                        matchResult.endStopBusStop.nodenm);

                                pendingRequests.incrementAndGet();
                                processedRoutes.add(routeKey);

                                calculateRouteInfoWithEnhancedDirectionInfo(startLocation, endLocation,
                                        startStop, matchResult.endStopBusStop, bus,
                                        matchResult.directionInfo, // 🆕 정확한 방향 정보
                                        new RouteInfoCallback() {
                                            @Override
                                            public void onSuccess(RouteInfo routeInfo) {
                                                if (!isDuplicateRoute(potentialRoutes, routeInfo)) {
                                                    potentialRoutes.add(routeInfo);
                                                    Log.i(TAG, "🎯 ✅ 완전 검증된 경로 추가: " +
                                                            routeInfo.getBusNumber() + "번 " +
                                                            routeInfo.getDirectionInfo() +
                                                            " (총 " + routeInfo.getDuration() + "분)");
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
                            } else {
                                Log.w(TAG, "❌ 🚨 회차 방향성 검증 실패: " + bus.routeno + "번 (잘못된 방향/회차 대기 필요)");
                            }
                        }
                    }
                }

                if (pendingRequests.get() == 0) {
                    callback.onSuccess(new ArrayList<>());
                }

            } catch (Exception e) {
                Log.e(TAG, "완전 개선된 버스 탐색 중 예외 발생", e);
                callback.onError("경로 탐색 중 오류 발생: " + e.getMessage());
            }
        });
    }

    // ================================================================================================
    // 8. 🆕 완전히 개선된 회차 방향 검증 메서드들 (모든 문제 해결)
    // ================================================================================================

    /**
     * 🆕 완전히 개선된 회차 방향성 검증
     * 모든 회차 방향 및 방향 정보 표시 문제 해결
     */
    private boolean validateRouteDirectionEnhanced(Location startLocation, Location endLocation,
                                                   TagoBusStopResponse.BusStop startStop,
                                                   TagoBusStopResponse.BusStop endStop,
                                                   TagoBusArrivalResponse.BusArrival bus) {
        try {
            // 1. 노선 정보 가져오기
            Response<TagoBusRouteStationResponse> routeResponse = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    bus.routeid,
                    200, 1, "json"
            ).execute();

            if (!isValidResponse(routeResponse, "버스 노선 정보")) {
                Log.w(TAG, bus.routeno + "번: 노선 정보 조회 실패");
                return false;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations =
                    routeResponse.body().response.body.items.item;

            // 2. 🆕 완전히 개선된 회차 방향 분석기 사용
            BusDirectionAnalyzer.RouteDirectionInfo directionInfo =
                    BusDirectionAnalyzer.analyzeRouteDirection(startStop, endStop, bus, routeStations);

            // 3. 결과 로깅
            Log.i(TAG, String.format("🎯 %s번 버스 완전 개선된 회차 분석 결과: %s (신뢰도: %d%%, 구간: %s)",
                    bus.routeno,
                    directionInfo.isValidDirection ? "승차가능" : "회차대기",
                    directionInfo.confidence,
                    directionInfo.currentSegment));

            // 4. 🔧 완전히 수정된 신뢰도 기반 3단계 판정 로직
            if (directionInfo.confidence >= 70) {
                // 높은 신뢰도: BusDirectionAnalyzer 결과 신뢰
                Log.i(TAG, bus.routeno + "번: 높은 신뢰도 (" + directionInfo.confidence + "%) - 회차 분석 결과 채택");
                return directionInfo.isValidDirection;

            } else if (directionInfo.confidence >= 50) {
                // 중간 신뢰도: 엄격한 기존 방식과 비교하여 일치할 때만 허용
                boolean strictResult = validateRouteDirectionStrict(startLocation, endLocation, startStop, endStop, bus);

                if (directionInfo.isValidDirection && strictResult) {
                    Log.i(TAG, bus.routeno + "번: 중간 신뢰도 (" + directionInfo.confidence + "%), 엄격한 검증과 일치하여 승차 허용");
                    return true;
                } else {
                    Log.w(TAG, bus.routeno + "번: 중간 신뢰도 (" + directionInfo.confidence + "%), 엄격한 검증과 불일치하여 회차 대기");
                    return false;
                }

            } else {
                // 낮은 신뢰도: 매우 엄격한 검증만 통과
                boolean strictResult = validateRouteDirectionStrict(startLocation, endLocation, startStop, endStop, bus);

                if (strictResult) {
                    Log.w(TAG, bus.routeno + "번: 낮은 신뢰도 (" + directionInfo.confidence + "%), 엄격한 검증으로 조건부 승차 허용");
                    return true;
                } else {
                    Log.w(TAG, bus.routeno + "번: 낮은 신뢰도 (" + directionInfo.confidence + "%), 엄격한 검증 실패로 회차 대기");
                    return false;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "완전 개선된 방향성 검증 실패: " + bus.routeno + "번", e);
            // 예외 발생 시도 엄격한 검증으로 폴백
            return validateRouteDirectionStrict(startLocation, endLocation, startStop, endStop, bus);
        }
    }

    /**
     * 🆕 엄격한 방향성 검증 (AND 조건으로 851번 같은 문제 해결)
     * 순서기반과 좌표기반이 모두 일치해야 통과
     */
    private boolean validateRouteDirectionStrict(Location startLocation, Location endLocation,
                                                 TagoBusStopResponse.BusStop startStop,
                                                 TagoBusStopResponse.BusStop endStop,
                                                 TagoBusArrivalResponse.BusArrival bus) {

        // 1. 정류장 순서 기반 검증
        boolean directionByOrder = isCorrectDirection(startStop, endStop, bus);

        // 2. 좌표 기반 검증
        boolean directionByCoords = isCorrectDirectionByCoordinates(
                startLocation, endLocation, startStop, endStop, bus);

        Log.d(TAG, String.format("%s번 버스 엄격한 방향성 검증: 순서기반=%b, 좌표기반=%b",
                bus.routeno, directionByOrder, directionByCoords));

        // 🔧 핵심 수정: AND 조건으로 851번 같은 회차 문제 해결
        boolean finalResult = directionByOrder && directionByCoords;

        if (!finalResult) {
            // 🚨 상세 분석으로 회차 문제 디버깅
            if (directionByOrder && !directionByCoords) {
                Log.w(TAG, String.format("🚨 %s번: 순서상 맞지만 좌표상 맞지 않음 - 회차 구간일 가능성 높음", bus.routeno));
            } else if (!directionByOrder && directionByCoords) {
                Log.w(TAG, String.format("🚨 %s번: 좌표상 맞지만 순서상 맞지 않음 - 노선 데이터 문제일 가능성", bus.routeno));
            } else {
                Log.w(TAG, String.format("🚨 %s번: 순서와 좌표 모두 맞지 않음 - 명확한 잘못된 방향", bus.routeno));
            }
        }

        return finalResult;
    }

    // ================================================================================================
    // 9. 🆕 개선된 방향성 검증 지원 메서드들
    // ================================================================================================

    /**
     * 🆕 개선된 방향성을 고려한 노선 매칭
     */
    private RouteMatchResult findDirectionalRouteMatchEnhanced(TagoBusStopResponse.BusStop startStop,
                                                               List<TagoBusStopResponse.BusStop> endStops,
                                                               Set<String> destinationKeywords,
                                                               TagoBusArrivalResponse.BusArrival bus) {
        try {
            Log.d(TAG, "🔍 " + bus.routeno + "번 버스 개선된 방향성 검증 시작");

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

            int startIndex = findStationIndex(routeStations, startStop);
            if (startIndex == -1) {
                Log.w(TAG, bus.routeno + "번: 출발지 정류장을 노선에서 찾을 수 없음");
                return null;
            }

            // 🆕 BusDirectionAnalyzer로 정확한 방향 정보 미리 분석
            TagoBusStopResponse.BusStop tempEndStop = endStops.isEmpty() ? null : endStops.get(0);
            if (tempEndStop != null) {
                BusDirectionAnalyzer.RouteDirectionInfo preAnalysis =
                        BusDirectionAnalyzer.analyzeRouteDirection(startStop, tempEndStop, bus, routeStations);

                Log.d(TAG, String.format("🎯 %s번 사전 방향 분석: %s", bus.routeno, preAnalysis.directionDescription));
            }

            // 도착지 후보들을 순방향으로만 검증
            for (TagoBusStopResponse.BusStop endStop : endStops) {
                int endIndex = findStationIndex(routeStations, endStop);

                if (endIndex != -1 && endIndex > startIndex) {
                    // 🆕 정확한 방향 정보 생성
                    BusDirectionAnalyzer.RouteDirectionInfo directionInfo =
                            BusDirectionAnalyzer.analyzeRouteDirection(startStop, endStop, bus, routeStations);

                    Log.i(TAG, String.format("✅ %s번: 올바른 방향 - %s(%d) -> %s(%d), 방향: %s",
                            bus.routeno, startStop.nodenm, startIndex, endStop.nodenm, endIndex,
                            directionInfo.directionDescription));

                    return new RouteMatchResult(endStop, directionInfo.directionDescription);
                }
            }

            // 키워드 기반 매칭도 방향성 고려
            for (String keyword : getImportantKeywords(destinationKeywords)) {
                for (int i = startIndex + 1; i < routeStations.size(); i++) {
                    TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
                    if (station.nodenm != null && station.nodenm.contains(keyword)) {
                        TagoBusStopResponse.BusStop nearestEndStop = findNearestEndStop(station, endStops);
                        if (nearestEndStop != null) {
                            // 🆕 정확한 방향 정보 생성
                            BusDirectionAnalyzer.RouteDirectionInfo directionInfo =
                                    BusDirectionAnalyzer.analyzeRouteDirection(startStop, nearestEndStop, bus, routeStations);

                            Log.i(TAG, String.format("✅ %s번: 키워드 매칭 - %s (인덱스: %d), 방향: %s",
                                    bus.routeno, keyword, i, directionInfo.directionDescription));

                            return new RouteMatchResult(nearestEndStop, directionInfo.directionDescription);
                        }
                    }
                }
            }

            Log.d(TAG, "❌ " + bus.routeno + "번: 올바른 방향의 도착지 정류장 없음");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "개선된 방향성 노선 매칭 중 예외: " + bus.routeno + "번", e);
            return null;
        }
    }

    /**
     * 정류장 순서 기반 방향 판단 (기존 메서드 유지)
     */
    private boolean isCorrectDirection(TagoBusStopResponse.BusStop startStop,
                                       TagoBusStopResponse.BusStop endStop,
                                       TagoBusArrivalResponse.BusArrival bus) {
        try {
            Response<TagoBusRouteStationResponse> routeResponse = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    bus.routeid,
                    200, 1, "json"
            ).execute();

            if (!isValidResponse(routeResponse, "버스 노선 정보")) {
                return false;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations = routeResponse.body().response.body.items.item;

            int startIndex = findStationIndex(routeStations, startStop);
            int endIndex = findStationIndex(routeStations, endStop);

            if (startIndex == -1 || endIndex == -1) {
                Log.w(TAG, "정류장 인덱스를 찾을 수 없음: start=" + startIndex + ", end=" + endIndex);
                return false;
            }

            boolean isForwardDirection = startIndex < endIndex;

            Log.d(TAG, String.format("%s번 버스 방향 판단: 출발(%d) -> 도착(%d), 순방향: %b",
                    bus.routeno, startIndex, endIndex, isForwardDirection));

            return isForwardDirection;

        } catch (Exception e) {
            Log.e(TAG, "방향 판단 중 오류", e);
            return false;
        }
    }

    /**
     * 좌표를 이용한 방향성 판단 (기존 메서드 유지)
     */
    private boolean isCorrectDirectionByCoordinates(Location startLocation, Location endLocation,
                                                    TagoBusStopResponse.BusStop startStop,
                                                    TagoBusStopResponse.BusStop endStop,
                                                    TagoBusArrivalResponse.BusArrival bus) {
        try {
            Response<TagoBusRouteStationResponse> routeResponse = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    bus.routeid,
                    200, 1, "json"
            ).execute();

            if (!isValidResponse(routeResponse, "버스 노선 정보")) {
                return false;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations = routeResponse.body().response.body.items.item;

            TagoBusRouteStationResponse.RouteStation closestStartStation = findClosestStation(
                    routeStations, startLocation.getLatitude(), startLocation.getLongitude());

            TagoBusRouteStationResponse.RouteStation closestEndStation = findClosestStation(
                    routeStations, endLocation.getLatitude(), endLocation.getLongitude());

            if (closestStartStation == null || closestEndStation == null) {
                Log.w(TAG, "좌표 기반 정류장 매칭 실패");
                return false;
            }

            int startRouteIndex = routeStations.indexOf(closestStartStation);
            int endRouteIndex = routeStations.indexOf(closestEndStation);

            if (startRouteIndex == -1 || endRouteIndex == -1) {
                return false;
            }

            boolean isForward = startRouteIndex < endRouteIndex;

            Log.d(TAG, String.format("좌표 기반 방향 판단: %s번 버스, 출발(%d) -> 도착(%d), 순방향: %b",
                    bus.routeno, startRouteIndex, endRouteIndex, isForward));

            return isForward;

        } catch (Exception e) {
            Log.e(TAG, "좌표 기반 방향 판단 중 오류", e);
            return false;
        }
    }

    // ================================================================================================
    // 10. 정류장 및 버스 정보 수집 메서드들 (기존 메서드 유지)
    // ================================================================================================

    private List<TagoBusStopResponse.BusStop> searchBusStopsInMultipleRadii(double latitude, double longitude, String locationName) {
        List<TagoBusStopResponse.BusStop> allStops = new ArrayList<>();
        Set<String> uniqueStopIds = new HashSet<>();

        int[] radiusMeters = {500, 800, 1200};
        int[] numOfRowsOptions = {30, 50, 100};

        for (int radius : radiusMeters) {
            for (int numOfRows : numOfRowsOptions) {
                try {
                    Response<TagoBusStopResponse> response = tagoApiService.getNearbyBusStops(
                            BuildConfig.TAGO_API_KEY_DECODED,
                            latitude, longitude,
                            numOfRows, 1, "json"
                    ).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        List<TagoBusStopResponse.BusStop> stops = response.body().response.body.items.item;

                        for (TagoBusStopResponse.BusStop stop : stops) {
                            if (stop.nodeid != null && !uniqueStopIds.contains(stop.nodeid)) {
                                double distance = calculateDistance(latitude, longitude,
                                        stop.gpslati, stop.gpslong);

                                if (distance <= radius) {
                                    uniqueStopIds.add(stop.nodeid);
                                    allStops.add(stop);
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    Log.w(TAG, locationName + " 정류장 검색 실패 - radius: " + radius + "m, rows: " + numOfRows, e);
                }
            }
        }

        Log.i(TAG, locationName + " 근처 정류장 " + allStops.size() + "개 발견");
        return allStops;
    }

    private List<TagoBusArrivalResponse.BusArrival> getAllBusesAtStop(TagoBusStopResponse.BusStop stop) {
        List<TagoBusArrivalResponse.BusArrival> allBuses = new ArrayList<>();
        Set<String> uniqueBusIds = new HashSet<>();

        try {
            int[] numOfRowsOptions = {50, 100, 200};

            for (int numOfRows : numOfRowsOptions) {
                for (int page = 1; page <= MAX_API_PAGES; page++) {
                    try {
                        Response<TagoBusArrivalResponse> response = tagoApiService.getBusArrivalInfo(
                                BuildConfig.TAGO_API_KEY_DECODED,
                                stop.citycode,
                                stop.nodeid,
                                numOfRows, page, "json"
                        ).execute();

                        if (!response.isSuccessful() || response.body() == null) {
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

            Log.d(TAG, "🚌 정류장 " + stop.nodenm + "에서 최종 " + allBuses.size() + "개 고유 버스 수집 완료");

        } catch (Exception e) {
            Log.e(TAG, "버스 정보 수집 중 예외 발생", e);
        }

        return allBuses;
    }

    // ================================================================================================
    // 11. 유틸리티 메서드들
    // ================================================================================================

    private int findStationIndex(List<TagoBusRouteStationResponse.RouteStation> stations,
                                 TagoBusStopResponse.BusStop targetStop) {
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);

            if (station.nodeid != null && station.nodeid.equals(targetStop.nodeid)) {
                return i;
            }

            if (station.nodenm != null && targetStop.nodenm != null) {
                if (normalizeStopName(station.nodenm).equals(normalizeStopName(targetStop.nodenm))) {
                    return i;
                }
            }

            if (station.gpslati > 0 && station.gpslong > 0) {
                double distance = calculateDistance(
                        station.gpslati, station.gpslong,
                        targetStop.gpslati, targetStop.gpslong
                );
                if (distance <= 100) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String normalizeStopName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\s·.-]", "").toLowerCase();
    }

    private Set<String> extractKeywordsFromStops(List<TagoBusStopResponse.BusStop> stops) {
        Set<String> keywords = new HashSet<>();

        for (TagoBusStopResponse.BusStop stop : stops) {
            if (stop.nodenm != null && !stop.nodenm.trim().isEmpty()) {
                String[] words = stop.nodenm.split("[\\s·.-]");
                for (String word : words) {
                    String cleaned = word.replaceAll("정류장|정류소|버스|앞|입구|사거리", "").trim();
                    if (cleaned.length() >= 2) {
                        keywords.add(cleaned);
                    }
                }
            }
        }

        return keywords;
    }

    private Set<String> getImportantKeywords(Set<String> allKeywords) {
        Set<String> important = new HashSet<>();

        for (String keyword : allKeywords) {
            if (keyword.length() >= 3) {
                if (keyword.contains("터미널") || keyword.contains("병원") ||
                        keyword.contains("대학교") || keyword.contains("시청") ||
                        keyword.contains("역") || keyword.contains("공항")) {
                    important.add(keyword);
                }
            }
        }

        return important;
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private TagoBusRouteStationResponse.RouteStation findClosestStation(
            List<TagoBusRouteStationResponse.RouteStation> stations,
            double targetLat, double targetLng) {

        TagoBusRouteStationResponse.RouteStation closest = null;
        double minDistance = Double.MAX_VALUE;

        for (TagoBusRouteStationResponse.RouteStation station : stations) {
            if (station.gpslati > 0 && station.gpslong > 0) {
                double distance = calculateDistance(
                        station.gpslati, station.gpslong,
                        targetLat, targetLng
                );

                if (distance < minDistance) {
                    minDistance = distance;
                    closest = station;
                }
            }
        }

        return closest;
    }

    private TagoBusStopResponse.BusStop findNearestEndStop(TagoBusRouteStationResponse.RouteStation station,
                                                           List<TagoBusStopResponse.BusStop> endStops) {
        if (station.gpslati <= 0 || station.gpslong <= 0) {
            return null;
        }

        TagoBusStopResponse.BusStop nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (TagoBusStopResponse.BusStop endStop : endStops) {
            double distance = calculateDistance(
                    station.gpslati, station.gpslong,
                    endStop.gpslati, endStop.gpslong
            );

            if (distance < minDistance && distance <= 500) {
                minDistance = distance;
                nearest = endStop;
            }
        }

        return nearest;
    }

    private <T> boolean isValidResponse(Response<T> response, String operation) {
        if (response == null || !response.isSuccessful() || response.body() == null) {
            Log.w(TAG, operation + " API 응답 실패");
            return false;
        }
        return true;
    }

    // ================================================================================================
    // 12. 🆕 개선된 경로 정보 계산 및 처리 (정확한 방향 정보 포함)
    // ================================================================================================

    /**
     * 🆕 개선된 방향 정보와 함께 경로 정보 계산
     */
    private void calculateRouteInfoWithEnhancedDirectionInfo(Location startLocation, Location endLocation,
                                                             TagoBusStopResponse.BusStop startStop,
                                                             TagoBusStopResponse.BusStop endStop,
                                                             TagoBusArrivalResponse.BusArrival bus,
                                                             String enhancedDirectionInfo, // 🆕 정확한 방향 정보
                                                             RouteInfoCallback callback) {

        executorService.execute(() -> {
            try {
                int walkToStartMin = calculateWalkingTime(startLocation, startStop);
                int walkToEndMin = calculateWalkingTime(endLocation, endStop);
                int busWaitMin = Math.max(1, bus.arrtime / 60);
                int busRideMin = calculateOptimalBusRideTime(startStop, endStop, bus.routeid, bus.routeno);

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
                routeInfo.setDirectionInfo(enhancedDirectionInfo); // 🆕 정확한 방향 정보 설정

                Log.i(TAG, String.format("🎯 완전 개선된 경로 정보 생성: %s번 버스 %s, 총 %d분",
                        bus.routeno, enhancedDirectionInfo, totalDurationMin));

                mainHandler.post(() -> callback.onSuccess(routeInfo));

            } catch (Exception e) {
                Log.e(TAG, "개선된 경로 정보 생성 중 예외", e);
                mainHandler.post(() -> callback.onError());
            }
        });
    }

    private int calculateWalkingTime(Location fromLocation, TagoBusStopResponse.BusStop toStop) {
        double distance = calculateDistance(
                fromLocation.getLatitude(), fromLocation.getLongitude(),
                toStop.gpslati, toStop.gpslong
        );
        return Math.max(1, (int) Math.ceil(distance / 83.33)); // 5km/h 속도로 계산
    }

    private int calculateOptimalBusRideTime(TagoBusStopResponse.BusStop startStop,
                                            TagoBusStopResponse.BusStop endStop,
                                            String routeId,
                                            String busNumber) {
        try {
            // 거리 기반 계산
            int distanceBasedTime = calculateBusRideTimeByDistance(startStop, endStop);

            // 정류장 개수 기반 계산
            int stopsBasedTime = calculateBusRideTimeByStops(startStop, endStop, routeId);

            // 두 값의 가중평균
            int baseTime;
            if (stopsBasedTime > 0) {
                baseTime = (int) (distanceBasedTime * 0.6 + stopsBasedTime * 0.4);
            } else {
                baseTime = distanceBasedTime;
            }

            int finalTime = Math.max(MIN_BUS_RIDE_TIME, Math.min(MAX_BUS_RIDE_TIME, baseTime));

            Log.d(TAG, String.format("%s번 버스 탑승시간 계산: 거리기반=%d분, 정류장기반=%d분, 최종=%d분",
                    busNumber, distanceBasedTime, stopsBasedTime, finalTime));

            return finalTime;

        } catch (Exception e) {
            Log.w(TAG, "버스 탑승 시간 계산 실패: " + busNumber + "번", e);
            return DEFAULT_BUS_RIDE_TIME_MIN;
        }
    }

    private int calculateBusRideTimeByDistance(TagoBusStopResponse.BusStop startStop,
                                               TagoBusStopResponse.BusStop endStop) {
        double distance = calculateDistance(
                startStop.gpslati, startStop.gpslong,
                endStop.gpslati, endStop.gpslong
        );

        double adjustedDistance = distance * DISTANCE_MULTIPLIER;
        return (int) Math.ceil(adjustedDistance / BUS_AVERAGE_SPEED_M_PER_MIN);
    }

    private int calculateBusRideTimeByStops(TagoBusStopResponse.BusStop startStop,
                                            TagoBusStopResponse.BusStop endStop,
                                            String routeId) {
        try {
            Response<TagoBusRouteStationResponse> response = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    routeId,
                    200, 1, "json"
            ).execute();

            if (isValidResponse(response, "노선 정보")) {
                List<TagoBusRouteStationResponse.RouteStation> stations = response.body().response.body.items.item;

                int startIndex = findStationIndex(stations, startStop);
                int endIndex = findStationIndex(stations, endStop);

                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    int stopCount = endIndex - startIndex;
                    return (int) Math.ceil(stopCount * MINUTES_PER_STOP);
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "정류장 개수 기반 시간 계산 실패", e);
        }

        return 0;
    }

    private boolean isDuplicateRoute(List<RouteInfo> routes, RouteInfo newRoute) {
        for (RouteInfo existing : routes) {
            if (existing.getBusNumber().equals(newRoute.getBusNumber()) &&
                    existing.getStartStopName().equals(newRoute.getStartStopName()) &&
                    existing.getEndStopName().equals(newRoute.getEndStopName())) {
                return true;
            }
        }
        return false;
    }

    private void finalizeRoutes(List<RouteInfo> routes, RouteSearchCallback callback) {
        Collections.sort(routes, Comparator.comparingInt(RouteInfo::getDuration));

        List<RouteInfo> finalRoutes = routes.size() > MAX_ROUTES_TO_SHOW
                ? routes.subList(0, MAX_ROUTES_TO_SHOW)
                : routes;

        Log.i(TAG, "=== 🎯 완전 개선된 최종 경로 결과 (" + finalRoutes.size() + "개) ===");
        for (int i = 0; i < finalRoutes.size(); i++) {
            RouteInfo route = finalRoutes.get(i);
            Log.d(TAG, String.format("경로 %d: %s번 버스 %s, %d분 소요",
                    i + 1, route.getBusNumber(), route.getDirectionInfo(), route.getDuration()));
        }

        mainHandler.post(() -> callback.onSuccess(finalRoutes));
    }

    // ================================================================================================
    // 13. UI 업데이트 및 네비게이션 (기존 메서드 유지)
    // ================================================================================================

    private void finalizeAndDisplayRoutes(List<RouteInfo> routes) {
        if (routes.isEmpty()) {
            updateRouteListVisibility(true, "경로를 찾을 수 없습니다.\n다른 출발지나 도착지를 시도해보세요.");
        } else {
            routeList.clear();
            routeList.addAll(routes);
            routeAdapter.notifyDataSetChanged();
            updateRouteListVisibility(false, "");
            Log.i(TAG, "🎯 완전 개선된 경로 탐색 완료: " + routes.size() + "개 경로 표시");
        }

        showLoading(false);
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

    private void navigateToMap(RouteInfo route) {
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

    private void toggleRouteDetails(int position) {
        RouteInfo route = routeList.get(position);
        route.setExpanded(!route.isExpanded());
        routeAdapter.notifyItemChanged(position);
    }

    // ================================================================================================
    // 14. 내부 클래스 및 인터페이스
    // ================================================================================================

    public static class RouteInfo implements Serializable {
        private String type;
        private int duration;
        private int busWaitTime;
        private String busNumber;
        private String startStopName;
        private String endStopName;
        private int busRideTime;
        private int walkingTimeToStartStop;
        private int walkingTimeToDestination;
        private boolean isExpanded = false;
        private String directionInfo;

        public RouteInfo(String type, int duration, int busWaitTime, String busNumber, String startStopName, String endStopName) {
            this.type = type;
            this.duration = duration;
            this.busWaitTime = busWaitTime;
            this.busNumber = busNumber;
            this.startStopName = startStopName;
            this.endStopName = endStopName;
        }

        // Getters
        public String getType() { return type; }
        public int getDuration() { return duration; }
        public int getBusWaitTime() { return busWaitTime; }
        public String getBusNumber() { return busNumber; }
        public String getStartStopName() { return startStopName; }
        public String getEndStopName() { return endStopName; }
        public int getBusRideTime() { return busRideTime; }
        public int getWalkingTimeToStartStop() { return walkingTimeToStartStop; }
        public int getWalkingTimeToDestination() { return walkingTimeToDestination; }
        public boolean isExpanded() { return isExpanded; }
        public String getDirectionInfo() { return directionInfo; }

        // Setters
        public void setBusRideTime(int busRideTime) { this.busRideTime = busRideTime; }
        public void setWalkingTimeToStartStop(int time) { this.walkingTimeToStartStop = time; }
        public void setWalkingTimeToDestination(int time) { this.walkingTimeToDestination = time; }
        public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
        public void setDirectionInfo(String directionInfo) { this.directionInfo = directionInfo; }

        public String getRouteSummary() {
            int totalWalkTime = walkingTimeToStartStop + walkingTimeToDestination;
            return String.format("총 %d분 소요 (도보 %d분 + 대기 %d분 + 버스 %d분)",
                    duration, totalWalkTime, busWaitTime, busRideTime);
        }

        public String getDepartureTimeInfo() {
            return String.format("약 %d분 후 버스 도착", busWaitTime);
        }

        public String getDetailedRouteInfo() {
            if (directionInfo != null && !directionInfo.equals("방향 정보 없음")) {
                return String.format("%s번 버스 (%s)", busNumber, directionInfo);
            } else {
                return String.format("%s번 버스", busNumber);
            }
        }

        public String getStopInfo() {
            return String.format("%s → %s", startStopName, endStopName);
        }

        public String getBoardingInfo() {
            return String.format("📍 %s 정류장에서 승차", startStopName);
        }
    }

    private static class RouteMatchResult {
        public final TagoBusStopResponse.BusStop endStopBusStop;
        public final String directionInfo;

        public RouteMatchResult(TagoBusStopResponse.BusStop endStopBusStop, String directionInfo) {
            this.endStopBusStop = endStopBusStop;
            this.directionInfo = directionInfo;
        }
    }

    // 콜백 인터페이스들
    interface ComprehensiveRouteCallback {
        void onSuccess(List<RouteInfo> routes);
        void onError(String errorMessage);
    }

    interface RouteSearchCallback {
        void onSuccess(List<RouteInfo> routes);
        void onError(String error);
    }

    interface RouteInfoCallback {
        void onSuccess(RouteInfo routeInfo);
        void onError();
    }

    interface RouteInteractionListener {
        void onNavigate(RouteInfo route);
    }

    interface DetailToggleListener {
        void onToggle(int position);
    }

    // ================================================================================================
    // 15. RecyclerView 어댑터 (기존 어댑터 유지)
    // ================================================================================================

    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {
        private List<RouteInfo> routes;
        private final RouteInteractionListener listener;
        private final DetailToggleListener detailListener;

        public RouteAdapter(List<RouteInfo> routes, RouteInteractionListener listener, DetailToggleListener detailListener) {
            this.routes = routes;
            this.listener = listener;
            this.detailListener = detailListener;
        }

        @NonNull
        @Override
        public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route, parent, false);
            return new RouteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
            RouteInfo route = routes.get(position);

            // 🆕 정확한 방향 정보가 포함된 버스 정보 표시
            holder.textRouteType.setText(route.getDetailedRouteInfo());

            // 승차 정류장 정보 + 총 시간
            String summaryText = route.getBoardingInfo() + "\n" + route.getRouteSummary();
            holder.textRouteSummary.setText(summaryText);

            // 도착 시간 정보
            holder.textDepartureTime.setText(route.getDepartureTimeInfo());

            // 상세 정보 표시/숨김
            holder.layoutRouteDetail.setVisibility(route.isExpanded() ? View.VISIBLE : View.GONE);
            holder.buttonExpandRoute.setText(route.isExpanded() ? "간략히 보기" : "상세 보기");

            // 상세 정보에는 전체 경로 표시
            if (route.isExpanded()) {
                // 기존 상세 정보 제거
                holder.layoutRouteDetail.removeAllViews();

                // 경로 상세 정보 추가
                TextView routeDetailText = new TextView(holder.itemView.getContext());
                routeDetailText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
                routeDetailText.setTextSize(14);
                routeDetailText.setPadding(16, 8, 16, 8);

                String detailInfo = String.format(
                        "🚌 버스 경로: %s\n" +
                                "🚶‍♂️ 도보 %d분 → 🚏 %s에서 승차\n" +
                                "⏰ %d분 대기 → 🚌 %d분 버스 이용 (%s)\n" +
                                "🚏 %s에서 하차 → 🚶‍♂️ 도보 %d분",
                        route.getStopInfo(),
                        route.getWalkingTimeToStartStop(),
                        route.getStartStopName(),
                        route.getBusWaitTime(),
                        route.getBusRideTime(),
                        route.getDirectionInfo(), // 🆕 정확한 방향 정보 표시
                        route.getEndStopName(),
                        route.getWalkingTimeToDestination()
                );

                routeDetailText.setText(detailInfo);
                holder.layoutRouteDetail.addView(routeDetailText);
            }

            // 클릭 리스너
            holder.buttonExpandRoute.setOnClickListener(v -> detailListener.onToggle(position));
            holder.buttonStartNavigation.setOnClickListener(v -> listener.onNavigate(route));
        }

        @Override
        public int getItemCount() {
            return routes.size();
        }

        class RouteViewHolder extends RecyclerView.ViewHolder {
            TextView textRouteType, textRouteSummary, textDepartureTime;
            Button buttonExpandRoute, buttonStartNavigation;
            LinearLayout layoutRouteDetail;

            RouteViewHolder(@NonNull View itemView) {
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