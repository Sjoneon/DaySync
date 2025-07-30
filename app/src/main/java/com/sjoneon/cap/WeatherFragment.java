// /app/src/main/java/com/sjoneon/cap/WeatherFragment.java

package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * 날씨 정보를 표시하는 프래그먼트 (주간예보 1-7일 통합 버전)
 */
public class WeatherFragment extends Fragment {

    private static final String TAG = "WeatherFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String KMA_API_BASE_URL = "http://apis.data.go.kr/1360000/";
    private static final int MAX_RETRY_COUNT = 3;

    // UI 요소
    private TextView textCurrentLocation, textCurrentTemp, textCurrentCondition, textPrecipitation, textHumidity, textWindSpeed;
    private ImageView imageWeatherIcon;
    private RecyclerView recyclerViewHourlyForecast, recyclerViewWeeklyForecast;

    // 서비스 및 데이터
    private FusedLocationProviderClient fusedLocationClient;
    private WeatherApiService weatherApiService;
    private Geocoder geocoder;
    private HourlyForecastAdapter hourlyAdapter;
    private WeeklyForecastAdapter weeklyAdapter;
    private List<WeatherForecastItem> hourlyForecastList = new ArrayList<>();
    private List<WeeklyForecastItem> weeklyForecastList = new ArrayList<>();
    private Gson lenientGson;

    // 위치 정보
    private int currentNx, currentNy;
    private String midTermTempCode, midTermLandCode;

    // API 응답 데이터 임시 저장 변수
    private JsonObject shortTermData = null;
    private JsonObject weeklyTempData = null;
    private JsonObject weeklyLandData = null;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather, container, false);
        initializeViews(view);
        initializeServices();
        setupRecyclerViews();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissionAndLoadWeather();
    }

    private void initializeViews(View view) {
        textCurrentLocation = view.findViewById(R.id.textCurrentLocation);
        textCurrentTemp = view.findViewById(R.id.textCurrentTemp);
        textCurrentCondition = view.findViewById(R.id.textCurrentCondition);
        textPrecipitation = view.findViewById(R.id.textFeelsLike); // ID는 그대로 사용
        textHumidity = view.findViewById(R.id.textHumidity);
        textWindSpeed = view.findViewById(R.id.textWindSpeed);
        imageWeatherIcon = view.findViewById(R.id.imageWeatherIcon);
        recyclerViewHourlyForecast = view.findViewById(R.id.recyclerViewHourlyForecast);
        recyclerViewWeeklyForecast = view.findViewById(R.id.recyclerViewWeeklyForecast);
    }

    private void initializeServices() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(KMA_API_BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        weatherApiService = retrofit.create(WeatherApiService.class);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(getContext(), Locale.KOREAN);
        lenientGson = new GsonBuilder().setLenient().create();
    }

    private void setupRecyclerViews() {
        recyclerViewHourlyForecast.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        hourlyAdapter = new HourlyForecastAdapter(hourlyForecastList);
        recyclerViewHourlyForecast.setAdapter(hourlyAdapter);

        recyclerViewWeeklyForecast.setLayoutManager(new LinearLayoutManager(getContext()));
        weeklyAdapter = new WeeklyForecastAdapter(weeklyForecastList);
        recyclerViewWeeklyForecast.setAdapter(weeklyAdapter);
    }

    private void checkPermissionAndLoadWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocationAndFetchWeather();
        }
    }

    private void getCurrentLocationAndFetchWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                updateLocationInfo(location);
                fetchWeatherData();
            } else {
                Toast.makeText(getContext(), "현재 위치를 가져올 수 없어 기본 위치(청주)로 조회합니다.", Toast.LENGTH_SHORT).show();
                setFallbackLocation();
                fetchWeatherData();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "위치 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
            setFallbackLocation();
            fetchWeatherData();
        });
    }

    private void updateLocationInfo(Location location) {
        LatXLngY gridCoords = convertToGrid(location.getLatitude(), location.getLongitude());
        currentNx = (int) gridCoords.x;
        currentNy = (int) gridCoords.y;

        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String adminArea = address.getAdminArea() != null ? address.getAdminArea() : "";
                String locality = address.getLocality() != null ? address.getLocality() : "";
                textCurrentLocation.setText(String.format("%s %s", adminArea, locality).trim());
                midTermTempCode = getMidTermTempCode(adminArea);
                midTermLandCode = getMidTermLandCode(adminArea);
            }
        } catch (IOException e) {
            Log.e(TAG, "지오코딩 실패", e);
            setFallbackLocation();
        }
    }

    private void setFallbackLocation() {
        currentNx = 69;
        currentNy = 107;
        midTermTempCode = "11C10000";
        midTermLandCode = "11C10101";
        textCurrentLocation.setText("충청북도 청주시");
    }

    private void fetchWeatherData() {
        String apiKey = BuildConfig.KMA_API_KEY;
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().getTime());

        // 데이터 리셋
        shortTermData = null;
        weeklyTempData = null;
        weeklyLandData = null;
        weeklyForecastList.clear(); // 목록 초기화

        fetchVillageForecast(apiKey, currentDate, 0);
        fetchMidTermTemp(apiKey, 0);
        fetchMidLandForecast(apiKey, 0);
    }

    // --- API Fetching Methods ---

    private void fetchVillageForecast(String apiKey, String baseDate, int retryCount) {
        String baseTime = getBaseTimeFor("village");
        // 단기예보는 더 많은 데이터(1~3일치)를 가져오기 위해 numOfRows 증가
        weatherApiService.getVillageForecast(apiKey, 1000, 1, "JSON", baseDate, baseTime, currentNx, currentNy)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            shortTermData = lenientGson.fromJson(response.body(), JsonObject.class);
                            parseAndDisplayShortTermWeather();
                            combineAndDisplayWeeklyForecast(); // 데이터 도착 후 주간예보 조합 시도
                        } else if (retryCount < MAX_RETRY_COUNT) {
                            fetchVillageForecast(apiKey, baseDate, retryCount + 1);
                        } else {
                            handleApiError("단기예보", response);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            fetchVillageForecast(apiKey, baseDate, retryCount + 1);
                        } else {
                            handleApiFailure("단기예보", t);
                        }
                    }
                });
    }

    private void fetchMidTermTemp(String apiKey, int retryCount) {
        if (midTermTempCode == null) return;
        String baseTime = getBaseTimeFor("midterm");

        weatherApiService.getMidTermTemperature(apiKey, midTermTempCode, baseTime, "JSON")
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            weeklyTempData = lenientGson.fromJson(response.body(), JsonObject.class);
                            combineAndDisplayWeeklyForecast(); // 데이터 도착 후 주간예보 조합 시도
                        } else if (retryCount < MAX_RETRY_COUNT) {
                            fetchMidTermTemp(apiKey, retryCount + 1);
                        } else {
                            handleApiError("주간기온", response);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            fetchMidTermTemp(apiKey, retryCount + 1);
                        } else {
                            handleApiFailure("주간기온", t);
                        }
                    }
                });
    }

    private void fetchMidLandForecast(String apiKey, int retryCount) {
        if (midTermLandCode == null) return;
        String baseTime = getBaseTimeFor("midterm");

        weatherApiService.getMidLandForecast(apiKey, midTermLandCode, baseTime, "JSON")
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            weeklyLandData = lenientGson.fromJson(response.body(), JsonObject.class);
                            combineAndDisplayWeeklyForecast(); // 데이터 도착 후 주간예보 조합 시도
                        } else if (retryCount < MAX_RETRY_COUNT) {
                            fetchMidLandForecast(apiKey, retryCount + 1);
                        } else {
                            handleApiError("주간날씨", response);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            fetchMidLandForecast(apiKey, retryCount + 1);
                        } else {
                            handleApiFailure("주간날씨", t);
                        }
                    }
                });
    }


    // --- Parsing and Displaying Methods ---

    private void parseAndDisplayShortTermWeather() {
        if (shortTermData == null) return;
        try {
            JsonObject responseObj = shortTermData.getAsJsonObject("response");
            if (responseObj == null) throw new IllegalStateException("Response object is null");

            String resultCode = responseObj.getAsJsonObject("header").get("resultCode").getAsString();
            if (!"00".equals(resultCode)) {
                String errorMsg = responseObj.getAsJsonObject("header").get("resultMsg").getAsString();
                Toast.makeText(getContext(), "기상청 오류(단기): " + errorMsg, Toast.LENGTH_SHORT).show();
                return;
            }

            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(
                    responseObj.getAsJsonObject("body").getAsJsonObject("items").get("item"),
                    new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType()
            );

            // 현재 날씨 표시
            String currentFcstTime = items.stream().map(it -> it.fcstTime).findFirst().orElse("");
            Map<String, String> currentWeatherData = items.stream()
                    .filter(it -> it.fcstTime.equals(currentFcstTime))
                    .collect(Collectors.toMap(it -> it.category, it -> it.fcstValue, (v1, v2) -> v1));
            displayCurrentWeather(currentWeatherData);

            // 시간별 예보 표시
            hourlyForecastList.clear();
            items.stream()
                    .collect(Collectors.groupingBy(item -> item.fcstTime))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Map<String, String> data = entry.getValue().stream()
                                .collect(Collectors.toMap(item -> item.category, item -> item.fcstValue, (v1, v2) -> v1));
                        if (data.containsKey("TMP") && data.containsKey("SKY")) {
                            hourlyForecastList.add(new WeatherForecastItem(entry.getKey(), data.get("SKY"), data.get("TMP")));
                        }
                    });
            hourlyAdapter.notifyDataSetChanged();

        } catch (JsonSyntaxException | IllegalStateException e) {
            Log.e(TAG, "단기예보 파싱 에러: " + e.getMessage());
        }
    }

    private void combineAndDisplayWeeklyForecast() {
        // 모든 API 응답이 도착해야 실행
        if (shortTermData == null || weeklyTempData == null || weeklyLandData == null) {
            return;
        }

        weeklyForecastList.clear();

        try {
            // 1. 단기예보에서 1~2일 후 데이터 추출
            List<WeatherResponse.WeatherItem> shortTermItems = lenientGson.fromJson(
                    shortTermData.getAsJsonObject("response").getAsJsonObject("body").getAsJsonObject("items").get("item"),
                    new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType()
            );

            Map<String, List<WeatherResponse.WeatherItem>> groupedByDate = shortTermItems.stream()
                    .collect(Collectors.groupingBy(item -> item.fcstDate));

            // 오늘 날짜 다음, 다다음 날짜 계산
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN);
            cal.add(Calendar.DATE, 1);
            String tomorrow = sdf.format(cal.getTime());
            cal.add(Calendar.DATE, 1);
            String dayAfterTomorrow = sdf.format(cal.getTime());

            String[] dates = {tomorrow, dayAfterTomorrow};
            int dayAfterCount = 1;

            for(String date : dates) {
                if (groupedByDate.containsKey(date)) {
                    List<WeatherResponse.WeatherItem> dailyItems = groupedByDate.get(date);
                    // 최고/최저 기온 찾기
                    int maxTemp = dailyItems.stream().filter(i -> "TMX".equals(i.category)).mapToInt(i -> (int)Double.parseDouble(i.fcstValue)).findFirst().orElse(0);
                    int minTemp = dailyItems.stream().filter(i -> "TMN".equals(i.category)).mapToInt(i -> (int)Double.parseDouble(i.fcstValue)).findFirst().orElse(0);

                    // 오전/오후 날씨 찾기 (SKY, PTY, POP)
                    String amCondition = getWeatherConditionFromShortTerm(dailyItems, "0900");
                    String pmCondition = getWeatherConditionFromShortTerm(dailyItems, "1500");
                    int amRainChance = getRainChanceFromShortTerm(dailyItems, "0900");
                    int pmRainChance = getRainChanceFromShortTerm(dailyItems, "1500");

                    weeklyForecastList.add(new WeeklyForecastItem(dayAfterCount, minTemp, maxTemp, amCondition, pmCondition, amRainChance, pmRainChance));
                }
                dayAfterCount++;
            }

            // 2. 중기예보에서 3~7일 후 데이터 추출 및 추가
            JsonObject tempItem = weeklyTempData.getAsJsonObject("response").getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();
            JsonObject landItem = weeklyLandData.getAsJsonObject("response").getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();

            for (int i = 3; i <= 7; i++) {
                if (tempItem.has("taMin" + i) && landItem.has("wf" + i + "Am")) {
                    weeklyForecastList.add(new WeeklyForecastItem(
                            i,
                            tempItem.get("taMin" + i).getAsInt(),
                            tempItem.get("taMax" + i).getAsInt(),
                            landItem.get("wf" + i + "Am").getAsString(),
                            landItem.get("wf" + i + "Pm").getAsString(),
                            landItem.get("rnSt" + i + "Am").getAsInt(),
                            landItem.get("rnSt" + i + "Pm").getAsInt()
                    ));
                }
            }

            // 3. 어댑터에 변경 알림
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 날짜 순으로 정렬
                    weeklyForecastList.sort(Comparator.comparingInt(w -> w.dayAfter));
                    weeklyAdapter.notifyDataSetChanged();
                    Log.d(TAG, "통합 주간 예보 UI 업데이트 완료. " + weeklyForecastList.size() + "개 항목.");
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "주간예보 데이터 통합/파싱 에러: " + e.getMessage());
        }
    }

    // 단기예보 데이터에서 특정 시간대의 날씨 상태 문자열을 반환하는 헬퍼 메서드
    private String getWeatherConditionFromShortTerm(List<WeatherResponse.WeatherItem> items, String time) {
        String sky = items.stream().filter(i -> time.equals(i.fcstTime) && "SKY".equals(i.category)).findFirst().map(i -> i.fcstValue).orElse("1");
        String pty = items.stream().filter(i -> time.equals(i.fcstTime) && "PTY".equals(i.category)).findFirst().map(i -> i.fcstValue).orElse("0");
        return convertShortTermCodeToText(pty, sky);
    }

    // 단기예보 데이터에서 특정 시간대의 강수 확률을 반환하는 헬퍼 메서드
    private int getRainChanceFromShortTerm(List<WeatherResponse.WeatherItem> items, String time) {
        return items.stream().filter(i -> time.equals(i.fcstTime) && "POP".equals(i.category)).mapToInt(i -> Integer.parseInt(i.fcstValue)).findFirst().orElse(0);
    }

    private void displayCurrentWeather(Map<String, String> data) {
        textCurrentTemp.setText(data.getOrDefault("TMP", "--") + "°C");
        textPrecipitation.setText("강수확률 " + data.getOrDefault("POP", "--") + "%");
        textHumidity.setText("습도 " + data.getOrDefault("REH", "--") + "%");
        textWindSpeed.setText("바람 " + data.getOrDefault("WSD", "--") + "m/s");
        updateWeatherConditionAndIcon(data.getOrDefault("PTY", "0"), data.getOrDefault("SKY", "1"), textCurrentCondition, imageWeatherIcon);
    }

    private void updateWeatherConditionAndIcon(String pty, String sky, TextView conditionView, ImageView iconView) {
        String conditionText = convertShortTermCodeToText(pty, sky);
        conditionView.setText(conditionText);
        iconView.setImageResource(getWeatherIconResource(conditionText));
    }

    // --- Helper & Util Methods ---

    private String convertShortTermCodeToText(String pty, String sky) {
        int ptyCode = Integer.parseInt(pty);
        int skyCode = Integer.parseInt(sky);

        if (ptyCode == 0) { // 강수 없음
            switch (skyCode) {
                case 1: return "맑음";
                case 3: return "구름많음";
                case 4: return "흐림";
                default: return "정보 없음";
            }
        } else { // 강수 있음
            switch (ptyCode) {
                case 1: return "비";
                case 2: return "비/눈";
                case 3: return "눈";
                case 5: case 6: case 7: return "진눈깨비";
                default: return "강수";
            }
        }
    }

    private int getWeatherIconResource(String condition) {

        if (condition == null) return R.drawable.ic_launcher_foreground;
        if (condition.contains("맑음")) return android.R.drawable.ic_secure;
        if (condition.contains("구름")) return android.R.drawable.ic_menu_gallery;
        if (condition.contains("흐림")) return android.R.drawable.ic_menu_close_clear_cancel;
        if (condition.contains("비")) return android.R.drawable.ic_menu_send;
        if (condition.contains("눈")) return android.R.drawable.ic_menu_compass;

        return R.drawable.ic_launcher_foreground; // 기본 아이콘
    }

    private String getBaseTimeFor(String apiType) {
        Calendar cal = Calendar.getInstance();
        if ("midterm".equals(apiType)) {
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour < 6) {
                cal.add(Calendar.DATE, -1);
                return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()) + "1800";
            } else if (hour < 18) {
                return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()) + "0600";
            } else {
                return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()) + "1800";
            }
        } else { // village
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            if (hour < 2 || (hour == 2 && minute <= 10)) {
                cal.add(Calendar.DATE, -1); return "2300";
            }
            int[] baseTimes = {2, 5, 8, 11, 14, 17, 20, 23};
            for (int i = baseTimes.length - 1; i >= 0; i--) {
                if (hour >= baseTimes[i]) return String.format(Locale.getDefault(), "%02d00", baseTimes[i]);
            }
            return "2300";
        }
    }

    private String getMidTermTempCode(String adminArea) {
        if (adminArea.contains("서울") || adminArea.contains("경기") || adminArea.contains("인천")) return "11B00000";
        if (adminArea.contains("강원")) return "11D10000";
        if (adminArea.contains("충남") || adminArea.contains("대전") || adminArea.contains("세종")) return "11C20000";
        if (adminArea.contains("충북")) return "11C10000";
        if (adminArea.contains("광주") || adminArea.contains("전남")) return "11F20000";
        if (adminArea.contains("전북")) return "11F10000";
        if (adminArea.contains("대구") || adminArea.contains("경북")) return "11H10000";
        if (adminArea.contains("부산") || adminArea.contains("울산") || adminArea.contains("경남")) return "11H20000";
        if (adminArea.contains("제주")) return "11G00000";
        return "11C10000";
    }

    private String getMidTermLandCode(String adminArea) {
        if (adminArea.contains("서울")) return "11B10101";
        if (adminArea.contains("인천")) return "11B20201";
        if (adminArea.contains("경기")) return "11B20601";
        if (adminArea.contains("강원")) return "11D10301";
        if (adminArea.contains("충북")) return "11C10101";
        if (adminArea.contains("충남")) return "11C20101";
        if (adminArea.contains("대전")) return "11C20401";
        if (adminArea.contains("세종")) return "11C20404";
        if (adminArea.contains("광주")) return "11F20501";
        if (adminArea.contains("전북")) return "11F10201";
        if (adminArea.contains("전남")) return "21F20801";
        if (adminArea.contains("대구")) return "11H10701";
        if (adminArea.contains("경북")) return "11H10501";
        if (adminArea.contains("부산")) return "11H20201";
        if (adminArea.contains("울산")) return "11H20101";
        if (adminArea.contains("경남")) return "11H20301";
        if (adminArea.contains("제주")) return "11G00201";
        return "11C10101";
    }

    private void handleApiError(String apiName, Response<?> response) {
        Log.e(TAG, apiName + " API 에러: " + response.code() + " " + response.message());
        if(isAdded()) Toast.makeText(getContext(), apiName + " 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void handleApiFailure(String apiName, Throwable t) {
        Log.e(TAG, apiName + " API 네트워크 실패", t);
        if(isAdded()) Toast.makeText(getContext(), "네트워크 오류로 " + apiName + " 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    // --- Data Classes and Adapters ---

    public static class WeatherForecastItem {
        private String time, skyCondition, temperature;
        public WeatherForecastItem(String time, String sky, String temp) {
            this.time = time.substring(0, 2) + "시"; this.skyCondition = sky; this.temperature = temp;
        }
        public String getTime() { return time; }
        public String getSkyCondition() { return skyCondition; }
        public String getTemperature() { return temperature; }
    }

    public static class WeeklyForecastItem {
        int dayAfter; // 정렬을 위한 필드
        String day, date;
        int minTemp, maxTemp, amRainChance, pmRainChance;
        String amCondition, pmCondition;

        public WeeklyForecastItem(int dayAfter, int minTemp, int maxTemp, String amCondition, String pmCondition, int amRainChance, int pmRainChance) {
            this.dayAfter = dayAfter;
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, dayAfter);
            this.day = new SimpleDateFormat("E요일", Locale.KOREAN).format(cal.getTime());
            this.date = new SimpleDateFormat("M/d", Locale.KOREAN).format(cal.getTime());
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.amCondition = amCondition;
            this.pmCondition = pmCondition;
            this.amRainChance = amRainChance;
            this.pmRainChance = pmRainChance;
        }
    }

    private class HourlyForecastAdapter extends RecyclerView.Adapter<HourlyForecastAdapter.ForecastViewHolder> {
        private List<WeatherForecastItem> forecasts;
        public HourlyForecastAdapter(List<WeatherForecastItem> forecasts) { this.forecasts = forecasts; }
        @NonNull @Override
        public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather_forecast, parent, false);
            return new ForecastViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
            WeatherForecastItem forecast = forecasts.get(position);
            holder.textDay.setText(forecast.getTime());
            holder.textHighTemp.setText(forecast.getTemperature() + "°");
            holder.textLowTemp.setVisibility(View.GONE);
            updateWeatherConditionAndIcon("0", forecast.getSkyCondition(), holder.textCondition, holder.imageIcon);
        }
        @Override public int getItemCount() { return forecasts.size(); }
        class ForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay, textCondition, textHighTemp, textLowTemp; ImageView imageIcon;
            ForecastViewHolder(View itemView) {
                super(itemView);
                textDay = itemView.findViewById(R.id.textForecastDay);
                textCondition = itemView.findViewById(R.id.textForecastCondition);
                textHighTemp = itemView.findViewById(R.id.textForecastHigh);
                textLowTemp = itemView.findViewById(R.id.textForecastLow);
                imageIcon = itemView.findViewById(R.id.imageForecastIcon);
            }
        }
    }

    private class WeeklyForecastAdapter extends RecyclerView.Adapter<WeeklyForecastAdapter.WeeklyForecastViewHolder> {
        private List<WeeklyForecastItem> forecasts;
        public WeeklyForecastAdapter(List<WeeklyForecastItem> forecasts) { this.forecasts = forecasts; }
        @NonNull @Override
        public WeeklyForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather_weekly_forecast, parent, false);
            return new WeeklyForecastViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull WeeklyForecastViewHolder holder, int position) {
            WeeklyForecastItem forecast = forecasts.get(position);
            holder.textDay.setText(forecast.day);
            holder.textDate.setText(forecast.date);
            holder.textLowTemp.setText(forecast.minTemp + "°");
            holder.textHighTemp.setText(forecast.maxTemp + "°");
            holder.textAmCondition.setText(forecast.amCondition);
            holder.textAmRainChance.setText("강수: " + forecast.amRainChance + "%");
            holder.imageAmIcon.setImageResource(getWeatherIconResource(forecast.amCondition));
            holder.textPmCondition.setText(forecast.pmCondition);
            holder.textPmRainChance.setText("강수: " + forecast.pmRainChance + "%");
            holder.imagePmIcon.setImageResource(getWeatherIconResource(forecast.pmCondition));
        }
        @Override public int getItemCount() { return forecasts.size(); }
        class WeeklyForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay, textDate, textLowTemp, textHighTemp;
            TextView textAmCondition, textAmRainChance, textPmCondition, textPmRainChance;
            ImageView imageAmIcon, imagePmIcon;
            WeeklyForecastViewHolder(View itemView) {
                super(itemView);
                textDay = itemView.findViewById(R.id.textWeeklyDay);
                textDate = itemView.findViewById(R.id.textWeeklyDate);
                textLowTemp = itemView.findViewById(R.id.textWeeklyLow);
                textHighTemp = itemView.findViewById(R.id.textWeeklyHigh);
                textAmCondition = itemView.findViewById(R.id.textAmCondition);
                textAmRainChance = itemView.findViewById(R.id.textAmRainChance);
                imageAmIcon = itemView.findViewById(R.id.imageAmIcon);
                textPmCondition = itemView.findViewById(R.id.textPmCondition);
                textPmRainChance = itemView.findViewById(R.id.textPmRainChance);
                imagePmIcon = itemView.findViewById(R.id.imagePmIcon);
            }
        }
    }

    private LatXLngY convertToGrid(double lat, double lng) {
        double RE = 6371.00877; double GRID = 5.0; double SLAT1 = 30.0; double SLAT2 = 60.0;
        double OLON = 126.0; double OLAT = 38.0; double XO = 43; double YO = 136;
        double DEGRAD = Math.PI / 180.0;
        double re = RE / GRID; double slat1 = SLAT1 * DEGRAD; double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD; double olat = OLAT * DEGRAD;
        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);
        LatXLngY rs = new LatXLngY(); rs.lat = lat; rs.lng = lng;
        double ra = Math.tan(Math.PI * 0.25 + (lat) * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lng * DEGRAD - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;
        rs.x = Math.floor(ra * Math.sin(theta) + XO + 0.5);
        rs.y = Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return rs;
    }

    private static class LatXLngY { public double lat, lng, x, y; }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            getCurrentLocationAndFetchWeather();
        } else {
            Toast.makeText(getContext(), "위치 권한이 거부되어 기본 위치로 조회합니다.", Toast.LENGTH_LONG).show();
            setFallbackLocation();
            fetchWeatherData();
        }
    }
}