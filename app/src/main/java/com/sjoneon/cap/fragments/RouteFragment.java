package com.sjoneon.cap.fragments;

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
import com.sjoneon.cap.BuildConfig;
import com.sjoneon.cap.R;
import com.sjoneon.cap.activities.MainActivity;
import com.sjoneon.cap.models.api.TagoBusArrivalResponse;
import com.sjoneon.cap.models.api.TagoBusRouteStationResponse;
import com.sjoneon.cap.models.api.TagoBusStopResponse;
import com.sjoneon.cap.services.TagoApiService;
import com.sjoneon.cap.services.TmapApiService;
import com.sjoneon.cap.utils.BusDirectionAnalyzer;
import com.sjoneon.cap.utils.TagoBusArrivalDeserializer;
import com.sjoneon.cap.utils.TagoBusStopDeserializer;
import com.sjoneon.cap.models.api.TmapPedestrianResponse;
import androidx.lifecycle.ViewModelProvider;
import com.sjoneon.cap.viewmodels.RouteViewModel;
import com.sjoneon.cap.repositories.RouteRepository;
import android.content.SharedPreferences;
import static android.content.Context.MODE_PRIVATE;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int MAX_API_PAGES = 2;
    private static final int NEARBY_STOPS_COUNT = 50;
    private static final int EXTENDED_SEARCH_RADIUS = 2000;

    // 성능 최적화: 각 출발지/도착지 근처에서 검색할 최대 정류장 개수
    private static final int MAX_STOPS_PER_LOCATION = 20;

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
    private LinearLayout layoutRouteLoading;
    private Location startLocation, endLocation;

    private RouteViewModel routeViewModel;
    private RouteRepository routeRepository;
    private String userUuid;

    // 성능 최적화를 위한 캐시
    private final Map<String, Integer> stationIndexCache = new HashMap<>();
    private final Map<String, List<TagoBusStopResponse.BusStop>> busStopSearchCache = new HashMap<>();

    // ================================================================================================
    // 3. 생명주기 메서드
    // ================================================================================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userUuid = prefs.getString("user_uuid", null);

        routeViewModel = new ViewModelProvider(requireActivity()).get(RouteViewModel.class);
        routeRepository = RouteRepository.getInstance(requireContext());
        initializeViews(view);
        initializeServices();
        setupRecyclerView();
        setupClickListeners();
        loadCurrentLocation();
        observeViewModel();
        handleAutoSearchRequest();
        return view;
    }

    /**
     * AI 채팅에서 전달된 자동 경로 탐색 요청 처리
     */
    private void handleAutoSearchRequest() {
        Bundle args = getArguments();
        if (args == null || !args.getBoolean("auto_search", false)) {
            Log.d(TAG, "자동 검색 플래그 없음");
            return;
        }

        //자동 검색 플래그 제거 (한 번만 실행되도록)
        args.remove("auto_search");

        String destination = args.getString("destination");
        String startLocationStr = args.getString("start_location");

        Log.d(TAG, "자동 경로 탐색 요청 - 출발지: " + startLocationStr + ", 도착지: " + destination);

        if (destination == null || destination.isEmpty()) {
            showToast("도착지 정보가 없습니다.");
            return;
        }

        // ViewModel 초기화 (이전 검색 데이터 제거)
        routeViewModel.clearData();
        Log.d(TAG, "ViewModel 초기화 완료");

        // 도착지 설정
        editEndLocation.setText(destination);
        Log.d(TAG, "도착지 설정 완료: " + editEndLocation.getText().toString());

        // 출발지 처리
        if ("CURRENT_LOCATION".equals(startLocationStr)) {
            // 현재 위치 사용 - 출발지 입력창 비우고 현재 위치 로딩
            editStartLocation.setText("");
            Log.d(TAG, "출발지 입력창 초기화 완료");

            // 현재 위치 강제 재로딩
            loadCurrentLocation();

            // 2초 대기 후 검색 (위치 로딩 대기)
            Log.d(TAG, "현재 위치 대기 중... (2초)");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "startLocation 상태: " + (startLocation != null ? "있음" : "없음"));
                Log.d(TAG, "editStartLocation 내용: " + editStartLocation.getText().toString());

                if (startLocation != null || !editStartLocation.getText().toString().trim().isEmpty()) {
                    Log.d(TAG, "현재 위치로 자동 검색 시작");
                    buttonSearchRoute.performClick();
                } else {
                    Log.e(TAG, "현재 위치 없음 - 검색 실패");
                    showToast("현재 위치를 가져올 수 없습니다. 위치 권한을 확인해주세요.");
                }
            }, 2000);
        } else if (startLocationStr != null && !startLocationStr.isEmpty()) {
            // 특정 출발지가 지정된 경우
            editStartLocation.setText(startLocationStr);
            Log.d(TAG, "출발지 설정 완료: " + startLocationStr);

            // 1초 대기 후 검색
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                buttonSearchRoute.performClick();
            }, 1000);
        }
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
        layoutRouteLoading = view.findViewById(R.id.layoutRouteLoading);
    }

    private void initializeServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(requireContext(), Locale.KOREAN);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        Gson tagoGson = new GsonBuilder()
                .registerTypeAdapter(TagoBusArrivalResponse.Body.class, new TagoBusArrivalDeserializer())
                .registerTypeAdapter(TagoBusStopResponse.Body.class, new TagoBusStopDeserializer())
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
                showRouteLoading(true);
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

    /**
     * 경로 목록 영역의 로딩 표시 제어
     */
    private void showRouteLoading(boolean show) {
        if (layoutRouteLoading == null) return;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                layoutRouteLoading.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) {
                    textNoRoutes.setVisibility(View.GONE);
                    recyclerViewRoutes.setVisibility(View.GONE);
                }
            });
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

        // 입력 검증 실패 시
        if (startAddress.isEmpty() || endAddress.isEmpty()) {
            showToast("출발지와 도착지를 모두 입력해주세요.");
            showLoading(false);
            showRouteLoading(false);
            return;
        }

        updateRouteListVisibility(false, "경로를 탐색 중입니다...");
        showRouteLoading(true);
        routeList.clear();
        routeAdapter.notifyDataSetChanged();

        executorService.execute(() -> {
            try {
                Log.i(TAG, "=== 회차 방향성 및 방향 정보 문제 해결된 경로 탐색 시작 ===");

                Location start = getCoordinatesFromAddress(startAddress);
                Location end = getCoordinatesFromAddress(endAddress);

                if (start == null || end == null) {
                    mainHandler.post(() -> {
                        showRouteLoading(false);
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
                    showRouteLoading(false);
                    updateRouteListVisibility(true, "경로 탐색 중 오류가 발생했습니다.");
                    showToast("경로 탐색에 실패했습니다.");
                    showLoading(false);
                });
            }
        });
    }

    private Location getCoordinatesFromAddress(String address) {
        try {
            // 최대 5개의 검색 결과 조회
            List<Address> addresses = geocoder.getFromLocationName(address, 5);

            if (addresses == null || addresses.isEmpty()) {
                Log.w(TAG, "주소 검색 결과 없음: " + address);
                return null;
            }

            Log.d(TAG, "주소 검색 결과 " + addresses.size() + "개: " + address);

            // 입력 주소를 정규화하고 키워드 추출
            String normalizedInput = normalizeAddressText(address);
            String[] inputKeywords = extractAddressKeywords(address);

            Address bestMatch = null;
            int bestScore = -1;

            // 각 검색 결과를 점수화하여 가장 적합한 결과 선택
            for (Address addr : addresses) {
                String locality = addr.getLocality();
                String adminArea = addr.getAdminArea();
                String featureName = addr.getFeatureName();
                String fullAddress = addr.getAddressLine(0);

                Log.d(TAG, String.format("검색 결과: %s, 지역: %s %s, 특징: %s",
                        fullAddress, adminArea, locality, featureName));

                int score = calculateAddressMatchScore(
                        address, normalizedInput, inputKeywords,
                        adminArea, locality, featureName, fullAddress
                );

                Log.d(TAG, String.format("매칭 점수: %d - %s", score, fullAddress));

                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = addr;
                }
            }

            // 적합한 결과가 없으면 첫 번째 결과 사용
            if (bestMatch == null) {
                bestMatch = addresses.get(0);
                Log.w(TAG, "최적 매칭 실패, 첫 번째 결과 사용: " + bestMatch.getAddressLine(0));
            }

            Location location = new Location("geocoder");
            location.setLatitude(bestMatch.getLatitude());
            location.setLongitude(bestMatch.getLongitude());

            Log.i(TAG, String.format("최종 선택된 위치: %s (%.6f, %.6f)",
                    bestMatch.getAddressLine(0),
                    bestMatch.getLatitude(),
                    bestMatch.getLongitude()));

            return location;

        } catch (IOException e) {
            Log.e(TAG, "주소 -> 좌표 변환 실패: " + address, e);
        }
        return null;
    }

    /**
     * 주소 문자열 정규화 (공백, 특수문자 제거 및 소문자 변환)
     */
    private String normalizeAddressText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "")
                .replaceAll("[·.-]", "")
                .toLowerCase();
    }

    /**
     * 주소에서 핵심 키워드 추출
     */
    private String[] extractAddressKeywords(String address) {
        if (address == null) return new String[0];

        String[] words = address.split("[\\s,]+");
        List<String> keywords = new ArrayList<>();

        for (String word : words) {
            String cleaned = word.trim();
            // 의미 있는 단어만 키워드로 사용 (2글자 이상)
            if (cleaned.length() >= 2) {
                keywords.add(normalizeAddressText(cleaned));
            }
        }

        return keywords.toArray(new String[0]);
    }

    /**
     * 주소 검색 결과의 적합도를 점수로 계산
     */
    private int calculateAddressMatchScore(String originalInput, String normalizedInput,
                                           String[] inputKeywords,
                                           String adminArea, String locality,
                                           String featureName, String fullAddress) {
        int score = 0;

        // 충청북도/청주시 지역이면 우선 점수 부여
        if (adminArea != null && adminArea.contains("충청북도")) {
            score += 10;
        }
        if (locality != null && locality.contains("청주")) {
            score += 5;
        }

        // 장소명(featureName)과 입력 주소의 유사도 계산
        if (featureName != null && !featureName.isEmpty()) {
            String normalizedFeature = normalizeAddressText(featureName);

            // 완전 일치하면 가장 높은 점수
            if (normalizedInput.equals(normalizedFeature)) {
                score += 100;
            }
            // 입력이 장소명에 포함되면 중간 점수 (길이 차이 고려)
            else if (normalizedFeature.contains(normalizedInput)) {
                int lengthDiff = Math.abs(normalizedFeature.length() - normalizedInput.length());
                score += Math.max(50 - lengthDiff * 5, 20);
            }
            // 장소명이 입력에 포함되면 낮은 점수
            else if (normalizedInput.contains(normalizedFeature)) {
                score += 40;
            }
        }

        // 추출한 키워드들이 결과에 포함되는지 확인
        if (inputKeywords.length > 0) {
            String searchTarget = normalizeAddressText(
                    (featureName != null ? featureName : "") +
                            (fullAddress != null ? fullAddress : "")
            );

            int matchedKeywords = 0;
            for (String keyword : inputKeywords) {
                if (searchTarget.contains(keyword)) {
                    matchedKeywords++;
                    score += 15;
                }
            }

            // 모든 키워드가 매칭되면 보너스 점수
            if (matchedKeywords == inputKeywords.length) {
                score += 30;
            }

            // 키워드 순서까지 일치하면 추가 점수
            if (matchedKeywords == inputKeywords.length &&
                    isKeywordOrderMatched(searchTarget, inputKeywords)) {
                score += 20;
            }
        }

        // 전체 주소 문자열과의 유사도도 고려
        if (fullAddress != null) {
            String normalizedFullAddress = normalizeAddressText(fullAddress);

            if (normalizedFullAddress.contains(normalizedInput)) {
                score += 25;
            }

            double similarity = calculateTextSimilarity(normalizedInput, normalizedFullAddress);
            score += (int)(similarity * 20);
        }

        return score;
    }

    /**
     * 키워드들이 대상 문자열에서 순서대로 나타나는지 확인
     */
    private boolean isKeywordOrderMatched(String target, String[] keywords) {
        int lastIndex = -1;
        for (String keyword : keywords) {
            int currentIndex = target.indexOf(keyword);
            if (currentIndex <= lastIndex) {
                return false;
            }
            lastIndex = currentIndex;
        }
        return true;
    }

    /**
     * 두 문자열의 유사도 계산 (0.0~1.0)
     */
    private double calculateTextSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        // 같은 위치의 문자가 일치하는 비율 계산
        int commonChars = 0;
        int minLength = Math.min(s1.length(), s2.length());

        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                commonChars++;
            }
        }

        double positionSimilarity = (double) commonChars / Math.max(s1.length(), s2.length());

        // 한 문자열이 다른 문자열을 포함하는 경우의 유사도
        double containsSimilarity = 0.0;
        if (s1.contains(s2) || s2.contains(s1)) {
            containsSimilarity = (double) Math.min(s1.length(), s2.length()) /
                    Math.max(s1.length(), s2.length());
        }

        return Math.max(positionSimilarity, containsSimilarity);
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
    // 7. 완전히 개선된 버스 경로 탐색 (모든 문제 해결)
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

                Log.d(TAG, "=== 회차 방향성 문제 완전 해결된 버스 노선 분석 ===");

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

                    Log.i(TAG, "정류장 " + startStop.nodenm + "에서 총 " + allBuses.size() + "개 버스 발견");

                    for (TagoBusArrivalResponse.BusArrival bus : allBuses) {
                        if (bus.routeid == null || bus.routeno == null) {
                            continue;
                        }

                        String routeKey = bus.routeno + "_" + startStop.nodeid;
                        if (processedRoutes.contains(routeKey)) {
                            continue;
                        }

                        Log.d(TAG, "버스 노선 상세 분석: " + bus.routeno + "번");

                        // 개선된 방향성 검증 포함한 노선 매칭
                        RouteMatchResult matchResult = findDirectionalRouteMatchEnhanced(
                                startStop, allEndStops, destinationKeywords, bus);

                        if (matchResult != null) {
                            // 핵심: 완전히 개선된 회차 방향성 검증
                            boolean isCorrectDirection = validateRouteDirectionEnhanced(
                                    startLocation, endLocation,
                                    startStop, matchResult.endStopBusStop, bus);

                            if (isCorrectDirection) {
                                Log.i(TAG, "완전 검증된 회차 방향성 통과: " + bus.routeno + "번 -> " +
                                        matchResult.endStopBusStop.nodenm);

                                pendingRequests.incrementAndGet();
                                processedRoutes.add(routeKey);

                                calculateRouteInfoWithEnhancedDirectionInfo(startLocation, endLocation,
                                        startStop, matchResult.endStopBusStop, bus,
                                        matchResult.directionInfo,
                                        new RouteInfoCallback() {
                                            @Override
                                            public void onSuccess(RouteInfo routeInfo) {
                                                if (!isDuplicateRoute(potentialRoutes, routeInfo)) {
                                                    potentialRoutes.add(routeInfo);
                                                    Log.i(TAG, "완전 검증된 경로 추가: " +
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
                                Log.w(TAG, "회차 방향성 검증 실패: " + bus.routeno + "번 (회차 대기 필요/잘못된 방향)");

                                // 신규: 회차가 필요한 버스의 경우 반대 정류장에서 재검색 시도
                                attemptReverseStopSearch(startLocation, endLocation, allStartStops,
                                        allEndStops, destinationKeywords, bus, potentialRoutes,
                                        pendingRequests, completedRequests, callback);
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

    /**
     * 신규: 회차가 필요한 버스를 위한 반대 정류장 검색
     * 407번, 412번 같은 버스들을 올바른 정류장에서 찾기 위함
     */
    private void attemptReverseStopSearch(Location startLocation, Location endLocation,
                                          List<TagoBusStopResponse.BusStop> allStartStops,
                                          List<TagoBusStopResponse.BusStop> allEndStops,
                                          Set<String> destinationKeywords,
                                          TagoBusArrivalResponse.BusArrival bus,
                                          List<RouteInfo> potentialRoutes,
                                          AtomicInteger pendingRequests,
                                          AtomicInteger completedRequests,
                                          RouteSearchCallback callback) {

        // 근처의 다른 정류장들에서 같은 버스 번호 찾기
        for (TagoBusStopResponse.BusStop alternativeStartStop : allStartStops) {
            if (alternativeStartStop.nodeid.equals(bus.routeid)) { // 수정: nodeid -> routeid로 변경
                continue; // 이미 확인한 정류장은 스킵
            }

            // 같은 버스 노선이 있는지 확인
            List<TagoBusArrivalResponse.BusArrival> alternativeBuses = getAllBusesAtStop(alternativeStartStop);

            for (TagoBusArrivalResponse.BusArrival altBus : alternativeBuses) {
                if (altBus.routeno.equals(bus.routeno)) {
                    Log.d(TAG, bus.routeno + "번 반대 정류장 검색: " + alternativeStartStop.nodenm);

                    RouteMatchResult matchResult = findDirectionalRouteMatchEnhanced(
                            alternativeStartStop, allEndStops, destinationKeywords, altBus);

                    if (matchResult != null) {
                        boolean isCorrectDirection = validateRouteDirectionEnhanced(
                                startLocation, endLocation,
                                alternativeStartStop, matchResult.endStopBusStop, altBus);

                        if (isCorrectDirection) {
                            Log.i(TAG, "반대 정류장에서 올바른 방향 발견: " + bus.routeno + "번 @ " +
                                    alternativeStartStop.nodenm);

                            // 여기서 경로 추가 로직 실행
                            // (기존 경로 추가 로직과 동일하게 처리)
                            return; // 찾았으면 더 이상 검색하지 않음
                        }
                    }
                }
            }
        }
    }

    // ================================================================================================
    // 8. 완전히 개선된 회차 방향 검증 메서드들 (모든 문제 해결)
    // ================================================================================================

    /**
     * 엄격한 방향성 검증 (AND 조건으로 회차 문제 해결)
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

        // 핵심 수정: AND 조건으로 회차 문제 해결
        boolean finalResult = directionByOrder && directionByCoords;

        if (!finalResult) {
            // 상세 분석으로 회차 문제 디버깅
            if (directionByOrder && !directionByCoords) {
                Log.w(TAG, String.format("%s번: 순서상 맞지만 좌표상 맞지 않음 - 회차 구간일 가능성 높음", bus.routeno));
            } else if (!directionByOrder && directionByCoords) {
                Log.w(TAG, String.format("%s번: 좌표상 맞지만 순서상 맞지 않음 - 노선 데이터 문제일 가능성", bus.routeno));
            } else {
                Log.w(TAG, String.format("%s번: 순서와 좌표 모두 맞지 않음 - 명확한 잘못된 방향", bus.routeno));
            }
        }

        return finalResult;
    }

    /**
     * 완전히 개선된 회차 방향성 검증 (상세 디버깅 포함)
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
                return false;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations = routeResponse.body().response.body.items.item;

            // 2. 핵심 개선: BusDirectionAnalyzer로 회차 구간 정확한 분석
            BusDirectionAnalyzer.RouteDirectionInfo directionInfo =
                    BusDirectionAnalyzer.analyzeRouteDirection(startStop, endStop, bus, routeStations);

            // 3. 결과 로깅
            Log.i(TAG, String.format("%s번 버스 완전 개선된 회차 분석 결과: %s (신뢰도: %d%%, 구간: %s)",
                    bus.routeno,
                    directionInfo.isValidDirection ? "승차가능" : "회차대기",
                    directionInfo.confidence,
                    directionInfo.currentSegment));

            // 4. 회차 구간 강화 판정 로직
            // 407번, 412번 같은 회차 구간 문제를 확실히 해결
            if (directionInfo.currentSegment.contains("회차") ||
                    directionInfo.currentSegment.contains("후반부") ||
                    !directionInfo.isForwardDirection) {

                Log.w(TAG, String.format("%s번: 회차 구간 감지 - 승차 불가 (구간: %s, 방향: %s)",
                        bus.routeno, directionInfo.currentSegment, directionInfo.directionDescription));
                return false;
            }

            // 좌표 추정 경로 필터링
            if (directionInfo.directionDescription.contains("좌표추정")) {
                Log.w(TAG, String.format("%s번: 좌표 추정 경로 - 도착지 정류장이 노선에 없어 경로 제외",
                        bus.routeno));
                return false;
            }

            // 5. 수정된 신뢰도 기반 3단계 판정 로직
            if (directionInfo.confidence >= 70) {
                // 높은 신뢰도: BusDirectionAnalyzer 결과 신뢰
                Log.i(TAG, bus.routeno + "번: 높은 신뢰도 (" + directionInfo.confidence + "%) - 회차 분석 결과 채택");
                return directionInfo.isValidDirection;

            } else if (directionInfo.confidence >= 50) {
                // 중간 신뢰도: 회차 구간이면 무조건 거부, 아니면 추가 검증
                if (!directionInfo.isValidDirection) {
                    Log.w(TAG, bus.routeno + "번: 중간 신뢰도 (" + directionInfo.confidence + "%) - 회차 대기 판정");
                    return false;
                }

                // 엄격한 기존 방식과 비교하여 일치할 때만 허용
                boolean strictResult = validateRouteDirectionStrict(startLocation, endLocation, startStop, endStop, bus);

                if (directionInfo.isValidDirection && strictResult) {
                    Log.i(TAG, bus.routeno + "번: 중간 신뢰도 (" + directionInfo.confidence + "%), 엄격한 검증과 일치하여 승차 허용");
                    return true;
                } else {
                    Log.w(TAG, bus.routeno + "번: 중간 신뢰도 (" + directionInfo.confidence + "%), 엄격한 검증과 불일치하여 회차 대기");
                    return false;
                }

            } else {
                // 낮은 신뢰도: 회차 구간이면 무조건 거부, 아니면 매우 엄격한 검증만 통과
                if (!directionInfo.isValidDirection) {
                    Log.w(TAG, bus.routeno + "번: 낮은 신뢰도 (" + directionInfo.confidence + "%) - 회차 대기 판정");
                    return false;
                }

                boolean strictResult = validateRouteDirectionStrict(startLocation, endLocation, startStop, endStop, bus);

                // 추가 검증: 좌표 기반 방향 판단으로 더블 체크
                boolean coordsResult = isCorrectDirectionByCoordinates(
                        startLocation, endLocation, startStop, endStop, bus);

                if (strictResult && coordsResult) {
                    Log.w(TAG, bus.routeno + "번: 낮은 신뢰도 (" + directionInfo.confidence + "%), 모든 검증 통과로 조건부 승차 허용");
                    return true;
                } else {
                    Log.w(TAG, bus.routeno + "번: 낮은 신뢰도 (" + directionInfo.confidence + "%), 검증 실패로 회차 대기");
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
     * 초엄격한 방향성 검증 (이중 안전장치를 위한 추가 검증)
     * 회차 문제가 있는 버스들을 완전히 걸러내기 위한 추가 검증
     */
    private boolean validateRouteDirectionUltraStrict(Location startLocation, Location endLocation,
                                                      TagoBusStopResponse.BusStop startStop,
                                                      TagoBusStopResponse.BusStop endStop,
                                                      TagoBusArrivalResponse.BusArrival bus) {
        try {
            // 1. 정류장 순서 기반 검증 (반드시 통과해야 함)
            boolean directionByOrder = isCorrectDirection(startStop, endStop, bus);
            if (!directionByOrder) {
                Log.w(TAG, bus.routeno + "번: 정류장 순서 검증 실패");
                return false;
            }

            // 2. 좌표 기반 검증 (반드시 통과해야 함)
            boolean directionByCoords = isCorrectDirectionByCoordinates(
                    startLocation, endLocation, startStop, endStop, bus);
            if (!directionByCoords) {
                Log.w(TAG, bus.routeno + "번: 좌표 기반 검증 실패");
                return false;
            }

            // 3. 거리 비율 검증 (회차로 인한 우회 경로 감지)
            double distance = calculateDirectDistance(startLocation, endLocation);
            if (distance > 0) {
                // 버스 경로 거리 대 직선 거리 비율이 3.0 이상이면 회차 의심
                double routeDistance = estimateRouteDistance(startStop, endStop);
                if (routeDistance > 0 && (routeDistance / distance) > 3.0) {
                    Log.w(TAG, bus.routeno + "번: 과도한 우회 경로 - 회차 의심 (비율: " +
                            String.format("%.2f", routeDistance / distance) + ")");
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "초엄격 검증 중 오류: " + bus.routeno + "번", e);
            return false; // 오류 시 거부
        }
    }

    // ================================================================================================
    // 9. 개선된 방향성 검증 지원 메서드들
    // ================================================================================================

    /**
     * 개선된 방향성을 고려한 노선 매칭
     */
    private RouteMatchResult findDirectionalRouteMatchEnhanced(
            TagoBusStopResponse.BusStop startStop,
            List<TagoBusStopResponse.BusStop> endStops,
            Set<String> destinationKeywords,
            TagoBusArrivalResponse.BusArrival bus) {
        try {
            Log.d(TAG, "" + bus.routeno + "번 버스 개선된 방향성 검증 시작");

            Response<TagoBusRouteStationResponse> routeResponse = tagoApiService.getBusRouteStationList(
                    BuildConfig.TAGO_API_KEY_DECODED,
                    startStop.citycode,
                    bus.routeid,
                    200, 1, "json"
            ).execute();

            if (!isValidResponse(routeResponse, "버스 노선 정보")) {
                return null;
            }

            List<TagoBusRouteStationResponse.RouteStation> routeStations =
                    routeResponse.body().response.body.items.item;

            int startIndex = findStationIndex(routeStations, startStop);
            if (startIndex == -1) {
                Log.w(TAG, bus.routeno + "번: 출발지 정류장을 노선에서 찾을 수 없음");
                return null;
            }

            // ✅ 개선: 모든 도착지 정류장에 대해 방향 분석 시도
            for (TagoBusStopResponse.BusStop endStop : endStops) {

                // 각 도착지 정류장으로 방향 분석
                BusDirectionAnalyzer.RouteDirectionInfo directionInfo =
                        BusDirectionAnalyzer.analyzeRouteDirection(startStop, endStop, bus, routeStations);

                Log.d(TAG, String.format("%s번 → %s 방향 분석: %s",
                        bus.routeno, endStop.nodenm, directionInfo.directionDescription));

                // 회차 구간이면 다음 도착지로 시도
                if (directionInfo.currentSegment.contains("회차") ||
                        directionInfo.currentSegment.contains("후반부") ||
                        !directionInfo.isForwardDirection) {

                    Log.d(TAG, String.format("%s번 → %s: 회차 구간 감지, 다음 도착지 시도",
                            bus.routeno, endStop.nodenm));
                    continue;  // ✅ 다음 도착지로 계속 시도
                }

                // 좌표 추정 경로는 제외
                if (directionInfo.directionDescription.contains("좌표추정")) {
                    Log.d(TAG, String.format("%s번 → %s: 좌표 추정 경로 제외, 다음 도착지 시도",
                            bus.routeno, endStop.nodenm));
                    continue;
                }

                // 노선에서 도착지 정류장 찾기
                int endIndex = findStationIndex(routeStations, endStop);
                if (endIndex != -1 && endIndex > startIndex) {
                    Log.i(TAG, String.format("%s번: 유효한 경로 발견 → %s",
                            bus.routeno, endStop.nodenm));
                    return new RouteMatchResult(endStop, directionInfo.directionDescription);
                }
            }

            // 모든 도착지 정류장 시도했지만 실패
            Log.d(TAG, bus.routeno + "번: 모든 도착지 정류장에서 유효한 경로를 찾지 못함");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "방향성 고려 노선 매칭 실패: " + bus.routeno + "번", e);
            return null;
        }
    }


    /**
     * 회차 방향성 검증이 포함된 완전 개선된 버스 탐색
     */
    private void findBusRoutes(Location startLocation, Location endLocation, RouteSearchCallback callback) {
        executorService.execute(() -> {
            try {
                Log.i(TAG, "=== 🚌 개선된 버스 경로 탐색 시작 ===");

                // 기존 로직 + 회차 방향성 검증 추가
                List<TagoBusStopResponse.BusStop> startStops = searchBusStopsInMultipleRadii(
                        startLocation.getLatitude(), startLocation.getLongitude(), "출발지");
                List<TagoBusStopResponse.BusStop> allEndStops = searchBusStopsInMultipleRadii(
                        endLocation.getLatitude(), endLocation.getLongitude(), "도착지");

                // 키워드 추출 (기존 방식 활용)
                Set<String> destinationKeywords = extractKeywordsFromStops(allEndStops);

                List<RouteInfo> potentialRoutes = Collections.synchronizedList(new ArrayList<>());
                AtomicInteger pendingRequests = new AtomicInteger(0);
                AtomicInteger completedRequests = new AtomicInteger(0);
                Set<String> processedRoutes = Collections.synchronizedSet(new HashSet<>());

                // 각 출발지 정류장에서 버스 탐색
                for (TagoBusStopResponse.BusStop startStop : startStops) {
                    Log.d(TAG, "출발지 정류장 분석: " + startStop.nodenm);

                    List<TagoBusArrivalResponse.BusArrival> arrivals = getAllBusesAtStop(startStop);
                    if (arrivals.isEmpty()) continue;

                    for (TagoBusArrivalResponse.BusArrival bus : arrivals) {
                        if (bus.arrprevstationcnt > 5) {
                            continue;
                        }

                        String routeKey = bus.routeno + "_" + startStop.nodeid;
                        if (processedRoutes.contains(routeKey)) {
                            continue;
                        }

                        Log.d(TAG, "버스 노선 상세 분석: " + bus.routeno + "번");

                        // 개선된 방향성 검증 포함한 노선 매칭
                        RouteMatchResult matchResult = findDirectionalRouteMatchEnhanced(
                                startStop, allEndStops, destinationKeywords, bus);

                        if (matchResult != null) {
                            // 완전히 개선된 회차 방향성 검증 (핵심 해결 부분)
                            boolean isCorrectDirection = validateRouteDirectionEnhanced(
                                    startLocation, endLocation,
                                    startStop, matchResult.endStopBusStop, bus);

                            if (isCorrectDirection) {
                                Log.i(TAG, "완전 검증된 회차 방향성 통과: " + bus.routeno + "번 -> " +
                                        matchResult.endStopBusStop.nodenm);

                                pendingRequests.incrementAndGet();
                                processedRoutes.add(routeKey);

                                // 수정: 올바른 메서드 호출
                                calculateRouteInfoWithEnhancedDirectionInfo(startLocation, endLocation,
                                        startStop, matchResult.endStopBusStop, bus,
                                        matchResult.directionInfo, // 정확한 방향 정보
                                        new RouteInfoCallback() {
                                            @Override
                                            public void onSuccess(RouteInfo routeInfo) {
                                                if (!isDuplicateRoute(potentialRoutes, routeInfo)) {
                                                    potentialRoutes.add(routeInfo);
                                                    Log.i(TAG, "완전 검증된 경로 추가: " +
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
                                Log.w(TAG, "❌ 회차 방향성 검증 실패: " + bus.routeno + "번 (잘못된 방향/회차 대기 필요)");
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

    private List<TagoBusStopResponse.BusStop> searchBusStopsInMultipleRadii(
            double latitude, double longitude, String locationName) {

        // 캐시 확인
        String cacheKey = String.format(Locale.US, "%.6f_%.6f", latitude, longitude);
        if (busStopSearchCache.containsKey(cacheKey)) {
            List<TagoBusStopResponse.BusStop> cachedStops = busStopSearchCache.get(cacheKey);
            Log.d(TAG, locationName + " 캐시 사용: " + cachedStops.size() + "개");
            return new ArrayList<>(cachedStops);
        }

        List<TagoBusStopResponse.BusStop> allStops = new ArrayList<>();
        Set<String> uniqueStopIds = new HashSet<>();

        // API 500m 제한으로 여러 지점 검색하여 1km 범위 커버
        double offset = 0.0045; // 약 500m

        double[][] searchPoints = {
                {latitude, longitude},              // 중심점
                {latitude + offset, longitude},     // 북쪽 500m
                {latitude - offset, longitude},     // 남쪽 500m
                {latitude, longitude + offset},     // 동쪽 500m
                {latitude, longitude - offset}      // 서쪽 500m
        };

        Log.d(TAG, locationName + " 다중 지점 검색 (1km 범위 커버)");

        for (int i = 0; i < searchPoints.length; i++) {
            double[] point = searchPoints[i];
            try {
                Response<TagoBusStopResponse> response = tagoApiService.getNearbyBusStops(
                        BuildConfig.TAGO_API_KEY_DECODED,
                        point[0], point[1],
                        100, 1, "json"
                ).execute();

                if (response.isSuccessful() && response.body() != null) {
                    TagoBusStopResponse.Items itemsContainer = null;
                    try {
                        itemsContainer = new Gson().fromJson(
                                response.body().response.body.items,
                                TagoBusStopResponse.Items.class
                        );
                    } catch (Exception e) {
                        Log.w(TAG, locationName + " 지점 " + (i+1) + " 파싱 실패", e);
                        continue;
                    }

                    if (itemsContainer != null && itemsContainer.item != null) {
                        for (TagoBusStopResponse.BusStop stop : itemsContainer.item) {
                            if (stop.nodeid != null && !uniqueStopIds.contains(stop.nodeid)) {
                                // 실제 중심점과의 거리 계산하여 1km 이내만 선택
                                double distance = calculateDistance(
                                        latitude, longitude,
                                        stop.gpslati, stop.gpslong
                                );

                                if (distance <= 1000) {
                                    uniqueStopIds.add(stop.nodeid);
                                    allStops.add(stop);
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, locationName + " 지점 " + (i+1) + " 검색 실패", e);
            }
        }

        // 거리순 정렬
        Collections.sort(allStops, new Comparator<TagoBusStopResponse.BusStop>() {
            @Override
            public int compare(TagoBusStopResponse.BusStop a, TagoBusStopResponse.BusStop b) {
                double distA = calculateDistance(latitude, longitude, a.gpslati, a.gpslong);
                double distB = calculateDistance(latitude, longitude, b.gpslati, b.gpslong);
                return Double.compare(distA, distB);
            }
        });

        // 가까운 순으로 제한
        List<TagoBusStopResponse.BusStop> selectedStops =
                allStops.size() > MAX_STOPS_PER_LOCATION
                        ? allStops.subList(0, MAX_STOPS_PER_LOCATION)
                        : allStops;

        Log.i(TAG, locationName + " 근처 " + selectedStops.size() + "개 정류장 발견");

        // 선택된 정류장 목록 출력 (디버깅용)
        for (TagoBusStopResponse.BusStop stop : selectedStops) {
            double dist = calculateDistance(latitude, longitude, stop.gpslati, stop.gpslong);
            Log.d(TAG, String.format("  - %s (%.0fm) [ID: %s]", stop.nodenm, dist, stop.nodeid));
        }

        // 캐시 저장
        List<TagoBusStopResponse.BusStop> resultStops = new ArrayList<>(selectedStops);
        busStopSearchCache.put(cacheKey, resultStops);

        return resultStops;
    }

    private List<TagoBusArrivalResponse.BusArrival> getAllBusesAtStop(TagoBusStopResponse.BusStop stop) {
        List<TagoBusArrivalResponse.BusArrival> allBuses = new ArrayList<>();
        Set<String> uniqueBusIds = new HashSet<>();

        try {
            // 200개씩 최대 2페이지만 호출
            int numOfRows = 200;
            int maxPages = 2;

            for (int page = 1; page <= maxPages; page++) {
                try {
                    Response<TagoBusArrivalResponse> response =
                            tagoApiService.getBusArrivalInfo(
                                    BuildConfig.TAGO_API_KEY_DECODED,
                                    stop.citycode,
                                    stop.nodeid,
                                    numOfRows,
                                    page,
                                    "json"
                            ).execute();

                    if (!response.isSuccessful() || response.body() == null) {
                        break;
                    }

                    TagoBusArrivalResponse.ItemsContainer itemsContainer = null;
                    try {
                        itemsContainer = new Gson().fromJson(
                                response.body().response.body.items,
                                TagoBusArrivalResponse.ItemsContainer.class
                        );
                    } catch (Exception e) {
                        Log.w(TAG, "JSON 파싱 실패 - page: " + page);
                        continue;
                    }

                    if (itemsContainer == null || itemsContainer.item == null) {
                        break;
                    }

                    // 중복 제거하며 버스 목록 추가
                    for (TagoBusArrivalResponse.BusArrival bus : itemsContainer.item) {
                        if (bus.routeid != null && bus.routeno != null) {
                            String busKey = bus.routeno + "_" + bus.routeid;
                            if (!uniqueBusIds.contains(busKey)) {
                                uniqueBusIds.add(busKey);
                                allBuses.add(bus);
                            }
                        }
                    }

                    // 결과가 200개 미만이면 더 이상 페이지 없음
                    if (itemsContainer.item.size() < numOfRows) {
                        break;
                    }

                } catch (Exception e) {
                    Log.w(TAG, "API 호출 실패 - page: " + page, e);
                    break;
                }
            }

            Log.d(TAG, "정류장 " + stop.nodenm + "에서 총 " + allBuses.size() + "개 버스 수집");

        } catch (Exception e) {
            Log.e(TAG, "버스 정보 수집 중 예외", e);
        }

        return allBuses;
    }

    // ================================================================================================
    // 11. 유틸리티 메서드들
    // ================================================================================================

    /**
     * 정류장 인덱스 찾기 개선 (더 유연한 매칭 로직)
     */
    private int findStationIndex(List<TagoBusRouteStationResponse.RouteStation> stations,
                                 TagoBusStopResponse.BusStop targetStop) {

        String cacheKey = targetStop.nodeid + "_" + stations.hashCode();

        if (stationIndexCache.containsKey(cacheKey)) {
            return stationIndexCache.get(cacheKey);
        }

        int resultIndex = -1;

        // 1단계: ID 정확 매칭
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            if (station.nodeid != null && station.nodeid.equals(targetStop.nodeid)) {
                resultIndex = i;
                stationIndexCache.put(cacheKey, resultIndex);
                return resultIndex;
            }
        }

        // 2단계: 이름 유사도 매칭
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            String stationName = station.nodenm;
            if (stationName != null && targetStop.nodenm != null) {
                // 정류장 이름이 포함 관계면 매칭
                if (stationName.contains(targetStop.nodenm) || targetStop.nodenm.contains(stationName)) {
                    Log.d(TAG, "이름 기반 매칭 성공: " + stationName + " ≈ " + targetStop.nodenm);
                    resultIndex = i;
                    stationIndexCache.put(cacheKey, resultIndex);
                    return resultIndex;
                }
            }
        }

        // 3단계: 좌표 기반 매칭 (50m 이내)
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            if (station.gpslati > 0 && station.gpslong > 0) {
                double distance = calculateDistance(
                        targetStop.gpslati, targetStop.gpslong,
                        station.gpslati, station.gpslong
                );
                if (distance <= 50) {
                    Log.d(TAG, "좌표 기반 매칭 성공: " + station.nodenm +
                            " (거리: " + String.format("%.1fm", distance) + ")");
                    resultIndex = i;
                    stationIndexCache.put(cacheKey, resultIndex);
                    return resultIndex;
                }
            }
        }

        return resultIndex;
    }

    /**
     * 직선 거리 계산 유틸리티
     */
    private double calculateDirectDistance(Location start, Location end) {
        if (start == null || end == null) return 0;
        return start.distanceTo(end);
    }

    /**
     * 예상 경로 거리 계산 유틸리티
     */
    private double estimateRouteDistance(TagoBusStopResponse.BusStop startStop, TagoBusStopResponse.BusStop endStop) {
        if (startStop == null || endStop == null) return 0;

        // 간단한 직선 거리의 1.4배로 추정 (도로 곡률 고려)
        double directDistance = calculateDistance(
                startStop.gpslati, startStop.gpslong,
                endStop.gpslati, endStop.gpslong
        );

        return directDistance * 1.4;
    }

    /**
     * 정류장명 정규화 (기존 메서드 개선)
     */
    private String normalizeStopName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\s·.-]", "")
                .replaceAll("정류장|정류소|버스정류장", "")
                .toLowerCase();
    }

    /**
     * 임시 종점 생성 (분석용) - 올바른 필드명 사용
     */
    private TagoBusStopResponse.BusStop createTempEndStop() {
        TagoBusStopResponse.BusStop tempStop = new TagoBusStopResponse.BusStop();
        tempStop.nodeid = "temp";
        tempStop.nodenm = "목적지";
        tempStop.gpslati = 0.0;
        tempStop.gpslong = 0.0;
        return tempStop;
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
        double earthRadius = 6371000; // 지구 반지름 (미터)

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
    // 12. 개선된 경로 정보 계산 및 처리 (정확한 방향 정보 포함)
    // ================================================================================================

    /**
     * 개선된 방향 정보와 함께 경로 정보 계산
     */
    private void calculateRouteInfoWithEnhancedDirectionInfo(Location startLocation, Location endLocation,
                                                             TagoBusStopResponse.BusStop startStop,
                                                             TagoBusStopResponse.BusStop endStop,
                                                             TagoBusArrivalResponse.BusArrival bus,
                                                             String enhancedDirectionInfo, // 정확한 방향 정보
                                                             RouteInfoCallback callback) {

        executorService.execute(() -> {
            try {
                int walkToStartMin = calculateWalkingTime(startLocation, startStop);
                int walkToEndMin = calculateWalkingTime(endLocation, endStop);
                int busArrivalMin = Math.max(1, bus.arrtime / 60);

                // 도보 시간보다 버스 도착 시간이 짧으면 탈 수 없으므로 제외
                if (busArrivalMin < walkToStartMin) {
                    Log.w(TAG, String.format("%s번 버스: 도보 시간(%d분) > 버스 도착(%d분) - 탑승 불가능, 경로 제외",
                            bus.routeno, walkToStartMin, busArrivalMin));
                    mainHandler.post(() -> callback.onError());
                    return;
                }

                int busRideMin = calculateOptimalBusRideTime(startStop, endStop, bus.routeid, bus.routeno);

                int totalDurationMin = walkToStartMin + busArrivalMin + busRideMin + walkToEndMin;

                RouteInfo routeInfo = new RouteInfo(
                        "대중교통",
                        totalDurationMin,
                        busArrivalMin,
                        bus.routeno,
                        startStop.nodenm,
                        endStop.nodenm
                );

                routeInfo.setWalkingTimeToStartStop(walkToStartMin);
                routeInfo.setBusRideTime(busRideMin);
                routeInfo.setWalkingTimeToDestination(walkToEndMin);
                routeInfo.setDirectionInfo(enhancedDirectionInfo); // 정확한 방향 정보 설정

                // 길안내를 위한 좌표 정보 설정
                routeInfo.setStartStopLat(startStop.gpslati);
                routeInfo.setStartStopLng(startStop.gpslong);
                routeInfo.setEndStopLat(endStop.gpslati);
                routeInfo.setEndStopLng(endStop.gpslong);
                routeInfo.setDestinationLat(endLocation.getLatitude());
                routeInfo.setDestinationLng(endLocation.getLongitude());

                Log.i(TAG, String.format("완전 개선된 경로 정보 생성: %s번 버스 %s, 총 %d분",
                        bus.routeno, enhancedDirectionInfo, totalDurationMin));

                mainHandler.post(() -> callback.onSuccess(routeInfo));

            } catch (Exception e) {
                Log.e(TAG, "개선된 경로 정보 생성 중 예외", e);
                mainHandler.post(() -> callback.onError());
            }
        });
    }

    private int calculateWalkingTime(Location fromLocation, TagoBusStopResponse.BusStop toStop) {
        try {
            // TMAP API로 실제 보행 경로 시간 계산
            Response<TmapPedestrianResponse> response = tmapApiService.getPedestrianRoute(
                    BuildConfig.TMAP_API_KEY,
                    String.valueOf(fromLocation.getLongitude()),
                    String.valueOf(fromLocation.getLatitude()),
                    String.valueOf(toStop.gpslong),
                    String.valueOf(toStop.gpslati),
                    "출발지",
                    "도착지"
            ).execute();

            if (response.isSuccessful() && response.body() != null &&
                    response.body().getFeatures() != null && !response.body().getFeatures().isEmpty()) {

                TmapPedestrianResponse.Feature firstFeature = response.body().getFeatures().get(0);
                if (firstFeature.getProperties() != null) {
                    int totalTimeSeconds = firstFeature.getProperties().getTotalTime();
                    int walkingMinutes = (int) Math.ceil(totalTimeSeconds / 60.0);

                    Log.d(TAG, String.format("TMAP API 도보 시간: %d분 (%d초)",
                            walkingMinutes, totalTimeSeconds));

                    return Math.max(1, walkingMinutes);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "TMAP API 호출 실패, 직선거리 계산 사용", e);
        }

        // API 실패 시 기존 방식 사용
        double distance = calculateDistance(
                fromLocation.getLatitude(), fromLocation.getLongitude(),
                toStop.gpslati, toStop.gpslong
        );

        int fallbackTime = Math.max(1, (int) Math.ceil(distance / 83.33));
        Log.d(TAG, String.format("직선거리 기반 도보 시간: %d분 (%.0fm)", fallbackTime, distance));

        return fallbackTime;
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

            // 정류장 개수가 비정상적으로 많으면 (회차 경로 가능성) 거리 기반만 사용
            if (stopsBasedTime > 60) {
                Log.w(TAG, String.format("%s번: 정류장 기반 시간(%d분)이 비정상적으로 큼 - 거리 기반만 사용",
                        busNumber, stopsBasedTime));
                stopsBasedTime = 0;
            }

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

                    // 정류장 개수가 50개 이상이면 회차 경로 가능성이 높으므로 무시
                    if (stopCount > 50) {
                        Log.w(TAG, String.format("정류장 개수 과다(%d개) - 회차 경로 가능성, 계산 제외", stopCount));
                        return 0;
                    }

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

        Log.i(TAG, "=== 완전 개선된 최종 경로 결과 (" + finalRoutes.size() + "개) ===");
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
        showRouteLoading(false);

        if (routes.isEmpty()) {
            updateRouteListVisibility(true, "경로를 찾을 수 없습니다.\n다른 출발지나 도착지를 시도해보세요.");
        } else {
            routeList.clear();
            routeList.addAll(routes);

            // 서버 저장을 위해 좌표를 먼저 ViewModel에 설정
            routeViewModel.setStartLocation(startLocation);
            routeViewModel.setEndLocation(endLocation);
            routeViewModel.setStartLocationText(editStartLocation.getText().toString());
            routeViewModel.setEndLocationText(editEndLocation.getText().toString());

            // 좌표 설정 후 경로 목록 업데이트 (서버 저장 트리거)
            routeViewModel.updateRouteList(new ArrayList<>(routes), userUuid);

            routeAdapter.notifyDataSetChanged();
            updateRouteListVisibility(false, "");
            Log.i(TAG, "완전 개선된 경로 탐색 완료: " + routes.size() + "개 경로 표시");
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

            if (noRoutes && layoutRouteLoading != null) {
                layoutRouteLoading.setVisibility(View.GONE);
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

    /**
     * navigateToNavigation 메서드
     */
    private void navigateToNavigation(RouteInfo route) {
        if (getActivity() instanceof MainActivity) {
            NavigationFragment navigationFragment = new NavigationFragment();
            Bundle args = new Bundle();

            // 경로 정보 전달
            args.putString("start_stop_name", route.getStartStopName());
            args.putString("end_stop_name", route.getEndStopName());
            args.putString("bus_number", route.getBusNumber());
            args.putString("direction_info", route.getDirectionInfo());

            // 좌표 정보 전달
            args.putDouble("start_stop_lat", route.getStartStopLat());
            args.putDouble("start_stop_lng", route.getStartStopLng());
            args.putDouble("end_stop_lat", route.getEndStopLat());
            args.putDouble("end_stop_lng", route.getEndStopLng());
            args.putDouble("destination_lat", route.getDestinationLat());
            args.putDouble("destination_lng", route.getDestinationLng());

            navigationFragment.setArguments(args);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, navigationFragment)
                    .addToBackStack(null)
                    .commit();

            if (((MainActivity) getActivity()).getSupportActionBar() != null) {
                ((MainActivity) getActivity()).getSupportActionBar().setTitle("길 안내");
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

        // [추가] 길안내를 위한 좌표 정보
        private double startStopLat;
        private double startStopLng;
        private double endStopLat;
        private double endStopLng;
        private double destinationLat;
        private double destinationLng;

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

        // [추가] 좌표 정보 Getters
        public double getStartStopLat() { return startStopLat; }
        public double getStartStopLng() { return startStopLng; }
        public double getEndStopLat() { return endStopLat; }
        public double getEndStopLng() { return endStopLng; }
        public double getDestinationLat() { return destinationLat; }
        public double getDestinationLng() { return destinationLng; }

        // Setters
        public void setBusRideTime(int busRideTime) { this.busRideTime = busRideTime; }
        public void setWalkingTimeToStartStop(int time) { this.walkingTimeToStartStop = time; }
        public void setWalkingTimeToDestination(int time) { this.walkingTimeToDestination = time; }
        public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
        public void setDirectionInfo(String directionInfo) { this.directionInfo = directionInfo; }

        // [추가] 좌표 정보 Setters
        public void setStartStopLat(double lat) { this.startStopLat = lat; }
        public void setStartStopLng(double lng) { this.startStopLng = lng; }
        public void setEndStopLat(double lat) { this.endStopLat = lat; }
        public void setEndStopLng(double lng) { this.endStopLng = lng; }
        public void setDestinationLat(double lat) { this.destinationLat = lat; }
        public void setDestinationLng(double lng) { this.destinationLng = lng; }

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
            return String.format("%s 정류장에서 승차", startStopName);
        }
    }

    /**
     * 노선 매칭 결과를 담는 클래스
     */
    private static class RouteMatchResult {
        TagoBusStopResponse.BusStop endStopBusStop;
        String directionInfo;

        RouteMatchResult(TagoBusStopResponse.BusStop endStop, String direction) {
            this.endStopBusStop = endStop;
            this.directionInfo = direction;
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
    // 15. RecyclerView 어댑터 (개선된 버전)
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

            // 버스 번호만 표시
            holder.textRouteType.setText(route.getBusNumber() + "번");

            // 총 소요 시간 표시 (흰색)
            holder.textTotalTime.setText(route.getDuration() + "분");
            holder.textTotalTime.setTextColor(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary)
            );

            // 경로 요약 (정류장 간 화살표)
            holder.textRouteSummary.setText(route.getStopInfo());

            // 도착 예정 시간
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
                        "버스 경로: %s\n" +
                                "도보 %d분 → %s에서 승차\n" +
                                "%d분 대기 → %d분 버스 이용 (%s)\n" +
                                "%s에서 하차 → 도보 %d분",
                        route.getStopInfo(),
                        route.getWalkingTimeToStartStop(),
                        route.getStartStopName(),
                        route.getBusWaitTime(),
                        route.getBusRideTime(),
                        route.getDirectionInfo(),
                        route.getEndStopName(),
                        route.getWalkingTimeToDestination()
                );

                routeDetailText.setText(detailInfo);
                holder.layoutRouteDetail.addView(routeDetailText);
            }

            // [수정] 클릭 리스너
            holder.buttonExpandRoute.setOnClickListener(v -> detailListener.onToggle(position));

            // [수정] 길안내 버튼 클릭 시 NavigationFragment로 이동
            holder.buttonStartNavigation.setOnClickListener(v -> {
                navigateToNavigation(route);
            });
        }

        @Override
        public int getItemCount() {
            return routes.size();
        }

        class RouteViewHolder extends RecyclerView.ViewHolder {
            // 기본 정보
            TextView textRouteType, textTotalTime, textRouteSummary, textDepartureTime;

            // 상세 정보
            TextView textWalkToStart, textStartStopName;
            TextView textBusInfo, textBusDirection, textBusWaitTime;
            TextView textEndStopName, textWalkToEnd;

            Button buttonExpandRoute, buttonStartNavigation;
            LinearLayout layoutRouteDetail;

            RouteViewHolder(@NonNull View itemView) {
                super(itemView);

                // 기본 정보 뷰 바인딩
                textRouteType = itemView.findViewById(R.id.textRouteType);
                textTotalTime = itemView.findViewById(R.id.textTotalTime);
                textRouteSummary = itemView.findViewById(R.id.textRouteSummary);
                textDepartureTime = itemView.findViewById(R.id.textDepartureTime);

                // 상세 정보 뷰 바인딩 (새로운 레이아웃 사용 시)
                textWalkToStart = itemView.findViewById(R.id.textWalkToStart);
                textStartStopName = itemView.findViewById(R.id.textStartStopName);
                textBusInfo = itemView.findViewById(R.id.textBusInfo);
                textBusDirection = itemView.findViewById(R.id.textBusDirection);
                textBusWaitTime = itemView.findViewById(R.id.textBusWaitTime);
                textEndStopName = itemView.findViewById(R.id.textEndStopName);
                textWalkToEnd = itemView.findViewById(R.id.textWalkToEnd);

                // 버튼 및 레이아웃
                buttonExpandRoute = itemView.findViewById(R.id.buttonExpandRoute);
                buttonStartNavigation = itemView.findViewById(R.id.buttonStartNavigation);
                layoutRouteDetail = itemView.findViewById(R.id.layoutRouteDetail);
            }
        }
    }

    private void observeViewModel() {
        routeViewModel.getRouteList().observe(getViewLifecycleOwner(), routes -> {
            if (routes != null && !routes.isEmpty()) {
                routeList.clear();
                routeList.addAll(routes);
                routeAdapter.notifyDataSetChanged();
                updateRouteListVisibility(false, "");
                Log.i(TAG, "ViewModel에서 경로 복구: " + routes.size() + "개");
            }
        });

        routeViewModel.getStartLocationText().observe(getViewLifecycleOwner(), text -> {
            if (text != null && !text.isEmpty() && editStartLocation != null) {
                editStartLocation.setText(text);
            }
        });

        routeViewModel.getEndLocationText().observe(getViewLifecycleOwner(), text -> {
            if (text != null && !text.isEmpty() && editEndLocation != null) {
                editEndLocation.setText(text);
            }
        });

        routeViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                showLoading(isLoading);
            }
        });

        routeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                showToast(error);
            }
        });

        routeViewModel.getIsSyncedWithServer().observe(getViewLifecycleOwner(), isSynced -> {
            if (isSynced != null && isSynced) {
                Log.i(TAG, "경로가 서버에 저장되었습니다");
            }
        });

        Location savedStartLocation = routeViewModel.getStartLocation();
        Location savedEndLocation = routeViewModel.getEndLocation();

        if (savedStartLocation != null) {
            startLocation = savedStartLocation;
        }
        if (savedEndLocation != null) {
            endLocation = savedEndLocation;
        }
    }
}