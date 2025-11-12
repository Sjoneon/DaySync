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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sjoneon.cap.BuildConfig;
import com.sjoneon.cap.R;
import com.sjoneon.cap.models.api.WeatherResponse;
import com.sjoneon.cap.models.local.WeeklyForecastItem;
import com.sjoneon.cap.services.WeatherApiService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.Date;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import okhttp3.OkHttpClient;

/**
 * 날씨 정보를 표시하는 프래그먼트
 */
public class WeatherFragment extends Fragment {

    private static final String TAG = "WeatherFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String KMA_API_BASE_URL = "https://apihub.kma.go.kr/api/typ02/openApi/";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 2000;

    // UI 요소
    private TextView textCurrentLocation, textCurrentTemp, textCurrentCondition, textPrecipitation, textHumidity, textWindSpeed;
    private ImageView imageWeatherIcon;
    private RecyclerView recyclerViewHourlyForecast, recyclerViewWeeklyForecast;

    // 서비스 및 데이터
    private FusedLocationProviderClient fusedLocationProviderClient;
    private WeatherApiService weatherApiService;
    private Geocoder geocoder;
    private HourlyForecastAdapter hourlyAdapter;
    private WeeklyForecastAdapter weeklyAdapter;
    private List<WeatherForecastItem> hourlyForecastList = new ArrayList<>();
    private List<WeeklyForecastItem> weeklyForecastList = new ArrayList<>();
    private Gson lenientGson;
    private Handler retryHandler;

    // 위치 정보
    private int currentNx, currentNy;

    // API 응답 데이터 임시 저장 변수
    private JsonObject shortTermData = null;

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }
    }

    private void initializeViews(View view) {
        textCurrentLocation = view.findViewById(R.id.textCurrentLocation);
        textCurrentTemp = view.findViewById(R.id.textCurrentTemp);
        textCurrentCondition = view.findViewById(R.id.textCurrentCondition);
        textPrecipitation = view.findViewById(R.id.textFeelsLike);
        textHumidity = view.findViewById(R.id.textHumidity);
        textWindSpeed = view.findViewById(R.id.textWindSpeed);
        imageWeatherIcon = view.findViewById(R.id.imageWeatherIcon);
        recyclerViewHourlyForecast = view.findViewById(R.id.recyclerViewHourlyForecast);
        recyclerViewWeeklyForecast = view.findViewById(R.id.recyclerViewWeeklyForecast);
    }

    private void initializeServices() {
        lenientGson = new GsonBuilder().setLenient().create();
        retryHandler = new Handler(Looper.getMainLooper());

        // 타임아웃 60초로 증가
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(KMA_API_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        weatherApiService = retrofit.create(WeatherApiService.class);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext());
        geocoder = new Geocoder(requireContext(), Locale.KOREA);
    }

    private void setupRecyclerViews() {
        recyclerViewHourlyForecast.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerViewWeeklyForecast.setLayoutManager(new LinearLayoutManager(getContext()));
        hourlyAdapter = new HourlyForecastAdapter(hourlyForecastList);
        weeklyAdapter = new WeeklyForecastAdapter(weeklyForecastList);
        recyclerViewHourlyForecast.setAdapter(hourlyAdapter);
        recyclerViewWeeklyForecast.setAdapter(weeklyAdapter);
    }

    private void checkPermissionAndLoadWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        loadCurrentLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCurrentLocation();
        } else {
            setFallbackLocation();
            fetchWeatherData();
        }
    }

    private void loadCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            setFallbackLocation();
            fetchWeatherData();
            return;
        }
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                convertLatLngToGrid(location.getLatitude(), location.getLongitude());
                updateLocationText(location.getLatitude(), location.getLongitude());
                fetchWeatherData();
            } else {
                setFallbackLocation();
                fetchWeatherData();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "위치 가져오기 실패", e);
            setFallbackLocation();
            fetchWeatherData();
        });
    }

    private void convertLatLngToGrid(double lat, double lon) {
        double RE = 6371.00877;
        double GRID = 5.0;
        double SLAT1 = 30.0;
        double SLAT2 = 60.0;
        double OLON = 126.0;
        double OLAT = 38.0;
        double XO = 43;
        double YO = 136;

        double DEGRAD = Math.PI / 180.0;
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + (lat) * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lon * DEGRAD - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;

        currentNx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        currentNy = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
    }

    private void updateLocationText(double lat, double lon) {
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String adminArea = address.getAdminArea() != null ? address.getAdminArea() : "";
                String locality = address.getLocality() != null ? address.getLocality() : "";
                textCurrentLocation.setText(String.format("%s %s", adminArea, locality).trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "지오코딩 실패", e);
            setFallbackLocation();
        }
    }

    private void setFallbackLocation() {
        currentNx = 69;
        currentNy = 107;
        textCurrentLocation.setText("충청북도 청주시");
    }

    private void fetchWeatherData() {
        shortTermData = null;
        weeklyForecastList.clear();

        // baseTime과 baseDate를 함께 계산
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // 02:10 이전이면 전날 2300시 데이터 사용
        if (hour < 2 || (hour == 2 && minute <= 10)) {
            cal.add(Calendar.DATE, -1);
        }

        String baseDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime());
        fetchVillageForecast(baseDate, 0);
    }

    private void fetchVillageForecast(String baseDate, int retryCount) {
        String baseTime = getBaseTimeFor("village");
        Log.d(TAG, "날씨 API 호출 시도 " + (retryCount + 1) + "/" + MAX_RETRY_COUNT +
                " - baseDate: " + baseDate + ", baseTime: " + baseTime +
                ", nx: " + currentNx + ", ny: " + currentNy);

        weatherApiService.getVillageForecast(BuildConfig.KMA_API_HUB_KEY, 500, 1, "JSON", baseDate, baseTime, currentNx, currentNy)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "날씨 API 응답 성공");

                            shortTermData = lenientGson.fromJson(response.body(), JsonObject.class);

                            // API 에러 코드 확인
                            if (shortTermData != null && shortTermData.has("response")) {
                                JsonObject responseObj = shortTermData.getAsJsonObject("response");
                                if (responseObj.has("header")) {
                                    JsonObject header = responseObj.getAsJsonObject("header");
                                    String resultCode = header.has("resultCode") ? header.get("resultCode").getAsString() : "00";

                                    if (!"00".equals(resultCode)) {
                                        String resultMsg = header.has("resultMsg") ? header.get("resultMsg").getAsString() : "알 수 없는 오류";
                                        Log.e(TAG, "API 에러: " + resultCode + " - " + resultMsg);

                                        if (isAdded()) {
                                            getActivity().runOnUiThread(() -> {
                                                Toast.makeText(getContext(),
                                                        "날씨 정보를 불러올 수 없습니다",
                                                        Toast.LENGTH_SHORT).show();
                                            });
                                        }
                                        return;
                                    }
                                }
                            }

                            parseAndDisplayShortTermWeather();
                            parseAndDisplayWeeklyForecast();
                        } else if (retryCount < MAX_RETRY_COUNT) {
                            Log.w(TAG, "날씨 API 응답 실패 (코드: " + response.code() + "), " + RETRY_DELAY_MS + "ms 후 재시도");
                            retryHandler.postDelayed(() -> fetchVillageForecast(baseDate, retryCount + 1), RETRY_DELAY_MS);
                        } else {
                            handleApiError("단기예보", response);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            Log.w(TAG, "날씨 API 네트워크 실패, " + RETRY_DELAY_MS + "ms 후 재시도: " + t.getMessage());
                            retryHandler.postDelayed(() -> fetchVillageForecast(baseDate, retryCount + 1), RETRY_DELAY_MS);
                        } else {
                            handleApiFailure("단기예보", t);
                        }
                    }
                });
    }

    private void parseAndDisplayShortTermWeather() {
        if (shortTermData == null) return;
        try {
            JsonObject responseObj = shortTermData.getAsJsonObject("response");
            if (responseObj == null) throw new IllegalStateException("Response object is null");
            JsonElement headerElement = responseObj.get("header");
            if(headerElement == null || !headerElement.isJsonObject()) return;
            String resultCode = headerElement.getAsJsonObject().get("resultCode").getAsString();
            if (!"00".equals(resultCode)) return;

            JsonElement bodyElement = responseObj.get("body");
            if(bodyElement == null || !bodyElement.isJsonObject()) return;
            JsonElement itemsElement = bodyElement.getAsJsonObject().get("items");
            if(itemsElement == null || !itemsElement.isJsonObject()) return;
            JsonElement itemElement = itemsElement.getAsJsonObject().get("item");
            if(itemElement == null || !itemElement.isJsonArray()) return;

            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(itemElement.getAsJsonArray(), new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType());
            String currentFcstTime = items.stream().map(it -> it.fcstTime).findFirst().orElse("");
            Map<String, String> currentWeatherData = items.stream()
                    .filter(it -> it.fcstTime.equals(currentFcstTime))
                    .collect(Collectors.toMap(it -> it.category, it -> it.fcstValue, (v1, v2) -> v1));
            displayCurrentWeather(currentWeatherData);

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
        } catch (Exception e) {
            Log.e(TAG, "단기예보 파싱 에러: " + e.getMessage());
        }
    }

    private void parseAndDisplayWeeklyForecast() {
        if (shortTermData == null) return;

        List<WeeklyForecastItem> dailyList = new ArrayList<>();
        try {
            // 안전한 JSON 파싱 - 각 단계별 null 체크
            JsonObject response = shortTermData.getAsJsonObject("response");
            if (response == null) {
                Log.e(TAG, "response 객체가 null입니다.");
                return;
            }

            JsonObject body = response.getAsJsonObject("body");
            if (body == null) {
                Log.e(TAG, "body 객체가 null입니다.");
                return;
            }

            JsonObject items = body.getAsJsonObject("items");
            if (items == null) {
                Log.e(TAG, "items 객체가 null입니다.");
                return;
            }

            JsonArray itemsArray = items.getAsJsonArray("item");
            if (itemsArray == null || itemsArray.size() == 0) {
                Log.e(TAG, "item 배열이 null이거나 비어있습니다.");
                return;
            }

            List<WeatherResponse.WeatherItem> itemsList = lenientGson.fromJson(
                    itemsArray,
                    new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType()
            );

            Map<String, List<WeatherResponse.WeatherItem>> groupedByDate = itemsList.stream()
                    .collect(Collectors.groupingBy(item -> item.fcstDate));

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN);

            for (int i = 0; i <= 2; i++) {
                cal.setTime(Calendar.getInstance().getTime());
                cal.add(Calendar.DATE, i);
                String date = sdf.format(cal.getTime());

                if (groupedByDate.containsKey(date)) {
                    List<WeatherResponse.WeatherItem> dailyItems = groupedByDate.get(date);

                    int maxTemp = dailyItems.stream()
                            .filter(item -> "TMX".equals(item.category))
                            .mapToInt(item -> (int)Double.parseDouble(item.fcstValue))
                            .findFirst()
                            .orElse((int) dailyItems.stream()
                                    .filter(item->"TMP".equals(item.category))
                                    .mapToDouble(item->Double.parseDouble(item.fcstValue))
                                    .max()
                                    .orElse(0.0));

                    int minTemp = dailyItems.stream()
                            .filter(item -> "TMN".equals(item.category))
                            .mapToInt(item -> (int)Double.parseDouble(item.fcstValue))
                            .findFirst()
                            .orElse((int) dailyItems.stream()
                                    .filter(item->"TMP".equals(item.category))
                                    .mapToDouble(item->Double.parseDouble(item.fcstValue))
                                    .min()
                                    .orElse(0.0));

                    String amCondition = getWeatherConditionFromShortTerm(dailyItems, "0900");
                    String pmCondition = getWeatherConditionFromShortTerm(dailyItems, "1500");
                    int amRainChance = getRainChanceFromShortTerm(dailyItems, "0900");
                    int pmRainChance = getRainChanceFromShortTerm(dailyItems, "1500");

                    String dateStr = new SimpleDateFormat("M/d", Locale.KOREAN).format(cal.getTime());
                    String dayOfWeekStr = (i == 0) ? "오늘" : new SimpleDateFormat("E요일", Locale.KOREAN).format(cal.getTime());

                    dailyList.add(new WeeklyForecastItem(dateStr, dayOfWeekStr, amCondition, pmCondition, amRainChance, pmRainChance, minTemp, maxTemp));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "단기예보->주간예보 변환 에러", e);
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                weeklyForecastList.clear();
                weeklyForecastList.addAll(dailyList);
                weeklyForecastList.sort(Comparator.comparing(w -> w.getDate()));
                weeklyAdapter.notifyDataSetChanged();
            });
        }
    }

    private String getWeatherConditionFromShortTerm(List<WeatherResponse.WeatherItem> items, String time) {
        String sky = items.stream().filter(i -> time.equals(i.fcstTime) && "SKY".equals(i.category)).findFirst().map(i -> i.fcstValue).orElse("1");
        String pty = items.stream().filter(i -> time.equals(i.fcstTime) && "PTY".equals(i.category)).findFirst().map(i -> i.fcstValue).orElse("0");
        return convertShortTermCodeToText(pty, sky);
    }

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

    private String convertShortTermCodeToText(String pty, String sky) {
        int ptyCode = Integer.parseInt(pty);
        int skyCode = Integer.parseInt(sky);
        if (ptyCode == 0) {
            switch (skyCode) {
                case 1: return "맑음";
                case 3: return "구름많음";
                case 4: return "흐림";
                default: return "정보 없음";
            }
        } else {
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
        if (condition == null) {
            return R.drawable.ic_weather_default;
        }

        // 맑음
        if (condition.contains("맑음")) {
            return R.drawable.ic_weather_sunny;
        }
        // 구름많음
        else if (condition.contains("구름")) {
            return R.drawable.ic_weather_cloudy;
        }
        // 흐림
        else if (condition.contains("흐림")) {
            return R.drawable.ic_weather_overcast;
        }
        // 비 (비/눈 혼합 체크)
        else if (condition.contains("비")) {
            if (condition.contains("눈")) {
                return R.drawable.ic_weather_sleet;
            }
            return R.drawable.ic_weather_rainy;
        }
        // 눈
        else if (condition.contains("눈")) {
            return R.drawable.ic_weather_snowy;
        }
        // 진눈깨비
        else if (condition.contains("진눈깨비")) {
            return R.drawable.ic_weather_sleet;
        }

        // 기본값 (정보 없음)
        return R.drawable.ic_weather_default;
    }

    private String getBaseTimeFor(String apiType) {
        Calendar cal = Calendar.getInstance();
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

    private void handleApiError(String apiName, Response<?> response) {
        Log.e(TAG, apiName + " API 에러: " + response.code() + " " + response.message());
        if(isAdded()) Toast.makeText(getContext(), apiName + " 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void handleApiFailure(String apiName, Throwable t) {
        Log.e(TAG, apiName + " API 네트워크 실패", t);
        if(isAdded()) Toast.makeText(getContext(), "네트워크 오류로 " + apiName + " 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    /**
     * 특정 날짜의 날씨 정보를 자연스러운 형식으로 반환
     */
    public String getFormattedWeatherInfo(Calendar targetCal) {
        if (shortTermData == null) {
            return null;
        }

        try {
            JsonObject responseObj = shortTermData.getAsJsonObject("response");
            if (responseObj == null) return null;

            JsonElement bodyElement = responseObj.get("body");
            if (bodyElement == null || !bodyElement.isJsonObject()) return null;

            JsonElement itemsElement = bodyElement.getAsJsonObject().get("items");
            if (itemsElement == null || !itemsElement.isJsonObject()) return null;

            JsonElement itemElement = itemsElement.getAsJsonObject().get("item");
            if (itemElement == null || !itemElement.isJsonArray()) return null;

            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(
                    itemElement.getAsJsonArray(),
                    new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType()
            );

            String targetDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(targetCal.getTime());
            int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean isToday = targetDate.equals(new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()));

            List<WeatherResponse.WeatherItem> targetDateItems = items.stream()
                    .filter(item -> targetDate.equals(item.fcstDate))
                    .collect(Collectors.toList());

            if (targetDateItems.isEmpty()) {
                return null;
            }

            Map<String, Map<String, String>> hourlyData = targetDateItems.stream()
                    .collect(Collectors.groupingBy(
                            item -> item.fcstTime,
                            Collectors.toMap(item -> item.category, item -> item.fcstValue, (v1, v2) -> v1)
                    ));

            List<String> sortedHours = new ArrayList<>(hourlyData.keySet());
            Collections.sort(sortedHours);

            int startHour = isToday ? currentHour : 0;
            String startTimeStr = String.format(Locale.getDefault(), "%02d00", startHour);

            StringBuilder weatherInfo = new StringBuilder();

            if (isToday) {
                weatherInfo.append(String.format("현재 %d시, ", currentHour));
            }

            String prevCondition = null;
            int prevHour = -1;
            int sameConditionStartHour = -1;

            for (String hour : sortedHours) {
                if (hour.compareTo(startTimeStr) < 0) continue;

                Map<String, String> data = hourlyData.get(hour);
                String sky = data.getOrDefault("SKY", "");
                String pty = data.getOrDefault("PTY", "0");

                String condition = getWeatherCondition(sky, pty);
                int hourInt = Integer.parseInt(hour.substring(0, 2));

                if (prevCondition == null) {
                    sameConditionStartHour = hourInt;
                    prevCondition = condition;
                } else if (!condition.equals(prevCondition)) {
                    if (sameConditionStartHour == prevHour) {
                        weatherInfo.append(String.format("%d시에는 %s, ", prevHour, prevCondition));
                    } else {
                        weatherInfo.append(String.format("%d시부터 %d시까지는 %s, ", sameConditionStartHour, prevHour, prevCondition));
                    }

                    sameConditionStartHour = hourInt;
                    prevCondition = condition;
                }

                prevHour = hourInt;
            }

            if (prevCondition != null && sameConditionStartHour != -1) {
                if (sameConditionStartHour == prevHour) {
                    weatherInfo.append(String.format("%d시에는 %s 소식이 있어요", prevHour, prevCondition));
                } else {
                    weatherInfo.append(String.format("%d시부터는 %s 소식이 있어요", sameConditionStartHour, prevCondition));
                }
            }

            String maxTemp = getMaxTemp(targetDateItems);
            String minTemp = getMinTemp(targetDateItems);
            if (maxTemp != null && minTemp != null) {
                weatherInfo.append(String.format(". 최고 기온 %s도, 최저 기온 %s도예요", maxTemp, minTemp));
            }

            return weatherInfo.toString();

        } catch (Exception e) {
            Log.e(TAG, "날씨 정보 포맷팅 실패", e);
            return null;
        }
    }

    /**
     * 날씨 상태를 문자열로 변환
     */
    private String getWeatherCondition(String sky, String pty) {
        int ptyValue = Integer.parseInt(pty);

        if (ptyValue > 0) {
            switch (ptyValue) {
                case 1: return "비";
                case 2: return "비/눈";
                case 3: return "눈";
                case 4: return "소나기";
                default: return "강수";
            }
        }

        int skyValue = Integer.parseInt(sky);
        switch (skyValue) {
            case 1: return "맑음";
            case 3: return "구름많음";
            case 4: return "흐림";
            default: return "알 수 없음";
        }
    }

    /**
     * 최고 기온 조회
     */
    private String getMaxTemp(List<WeatherResponse.WeatherItem> items) {
        for (WeatherResponse.WeatherItem item : items) {
            if ("TMX".equals(item.category)) {
                return item.fcstValue;
            }
        }
        return null;
    }

    /**
     * 최저 기온 조회
     */
    private String getMinTemp(List<WeatherResponse.WeatherItem> items) {
        for (WeatherResponse.WeatherItem item : items) {
            if ("TMN".equals(item.category)) {
                return item.fcstValue;
            }
        }
        return null;
    }

    public static class WeatherForecastItem {
        private String time, skyCondition, temperature;
        public WeatherForecastItem(String time, String sky, String temp) {
            this.time = time.substring(0, 2) + "시"; this.skyCondition = sky; this.temperature = temp;
        }
        public String getTime() { return time; }
        public String getSkyCondition() { return skyCondition; }
        public String getTemperature() { return temperature; }
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
            TextView textDay, textHighTemp, textLowTemp, textCondition;
            ImageView imageIcon;
            public ForecastViewHolder(@NonNull View itemView) {
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
            holder.textDay.setText(forecast.getDayOfWeek());
            holder.textDate.setText(forecast.getDate());
            holder.textLowTemp.setText(forecast.getMinTemp() + "°");
            holder.textHighTemp.setText(forecast.getMaxTemp() + "°");
            holder.textAmCondition.setText(forecast.getAmCondition());
            holder.textAmRainChance.setText("강수: " + forecast.getAmPrecipitation() + "%");
            holder.imageAmIcon.setImageResource(getWeatherIconResource(forecast.getAmCondition()));
            holder.textPmCondition.setText(forecast.getPmCondition());
            holder.textPmRainChance.setText("강수: " + forecast.getPmPrecipitation() + "%");
            holder.imagePmIcon.setImageResource(getWeatherIconResource(forecast.getPmCondition()));
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
}