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

import androidx.annotation.DrawableRes;
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
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 * 날씨 정보를 표시하는 프래그먼트 (단기/중기 예보 조합 로직 최종 수정)
 *
 * @author DaySync
 * @version 2.5
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
    private final List<HourlyForecastItem> hourlyForecastList = new ArrayList<>();
    private final List<WeeklyForecastItem> weeklyForecastList = new ArrayList<>();
    private Gson lenientGson;

    // 위치 정보
    private int currentNx, currentNy;
    private String midTermTempCode, midTermLandCode;

    // API 응답 데이터 저장 변수
    private JsonObject weeklyTempData = null;
    private JsonObject weeklyLandData = null;
    private boolean midTermForecastAppended = false;


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
        textPrecipitation = view.findViewById(R.id.textFeelsLike);
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
        recyclerViewWeeklyForecast.setNestedScrollingEnabled(false);
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
                setMidTermCodes(adminArea, locality);
            }
        } catch (IOException e) {
            Log.e(TAG, "지오코딩 실패", e);
            setFallbackLocation();
        }
    }

    private void setMidTermCodes(String adminArea, String locality) {
        midTermLandCode = getMidTermLandCode(adminArea);
        midTermTempCode = getMidTermTempCode(locality);
    }

    private void setFallbackLocation() {
        currentNx = 69;
        currentNy = 107;
        midTermLandCode = "11C10000"; // 충청북도 (육상)
        midTermTempCode = "11C10301";  // 청주 (기온)
        textCurrentLocation.setText("충청북도 청주시");
    }

    private void fetchWeatherData() {
        String apiKey = BuildConfig.KMA_API_KEY;
        // 데이터 로드 시작 시 상태 초기화
        weeklyTempData = null;
        weeklyLandData = null;
        midTermForecastAppended = false;
        weeklyForecastList.clear();

        fetchVillageForecast(apiKey, 0);
        fetchMidTermTemp(apiKey, 0);
        fetchMidLandForecast(apiKey, 0);
    }

    // --- API Fetching Methods ---

    private void fetchVillageForecast(String apiKey, int retryCount) {
        Map<String, String> baseDateTime = getBaseTimeFor("village");
        weatherApiService.getVillageForecast(apiKey, 1000, 1, "JSON", baseDateTime.get("date"), baseDateTime.get("time"), currentNx, currentNy)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            parseAndDisplayShortTermWeather(response.body());
                        } else if (retryCount < MAX_RETRY_COUNT) {
                            fetchVillageForecast(apiKey, retryCount + 1);
                        } else {
                            handleApiError("단기예보", response);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            fetchVillageForecast(apiKey, retryCount + 1);
                        } else {
                            handleApiFailure("단기예보", t);
                        }
                    }
                });
    }

    private void fetchMidTermTemp(String apiKey, int retryCount) {
        if (midTermTempCode == null) return;
        String baseTime = getBaseTimeFor("midterm").get("time");
        weatherApiService.getMidTermTemperature(apiKey, midTermTempCode, baseTime, "JSON")
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            weeklyTempData = lenientGson.fromJson(response.body(), JsonObject.class);
                            appendMidTermForecast();
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
        String baseTime = getBaseTimeFor("midterm").get("time");
        weatherApiService.getMidLandForecast(apiKey, midTermLandCode, baseTime, "JSON")
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            weeklyLandData = lenientGson.fromJson(response.body(), JsonObject.class);
                            appendMidTermForecast();
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

    // --- Data Parsing and UI Update Methods ---

    private void parseAndDisplayShortTermWeather(String responseBody) {
        try {
            JsonObject responseJson = lenientGson.fromJson(responseBody, JsonObject.class);
            JsonObject responseObj = responseJson.getAsJsonObject("response");
            if (responseObj == null || !"00".equals(responseObj.getAsJsonObject("header").get("resultCode").getAsString())) {
                Toast.makeText(getContext(), "단기예보 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(responseObj.getAsJsonObject("body").getAsJsonObject("items").get("item"), new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType());

            items.stream().min(Comparator.comparing(item -> item.fcstDate + item.fcstTime))
                    .ifPresent(firstItem -> {
                        Map<String, String> currentWeatherData = items.stream()
                                .filter(it -> it.fcstDate.equals(firstItem.fcstDate) && it.fcstTime.equals(firstItem.fcstTime))
                                .collect(Collectors.toMap(it -> it.category, it -> it.fcstValue, (v1, v2) -> v1));
                        displayCurrentWeather(currentWeatherData);
                    });

            hourlyForecastList.clear();
            items.stream()
                    .collect(Collectors.groupingBy(item -> item.fcstDate + item.fcstTime, LinkedHashMap::new, Collectors.toList()))
                    .entrySet().stream()
                    .filter(entry -> Integer.parseInt(entry.getKey().substring(8, 10)) % 3 == 0)
                    .limit(8)
                    .forEach(entry -> {
                        Map<String, String> data = entry.getValue().stream().collect(Collectors.toMap(it -> it.category, it -> it.fcstValue, (v1, v2) -> v1));
                        if (data.containsKey("TMP") && data.containsKey("SKY")) {
                            hourlyForecastList.add(new HourlyForecastItem(entry.getKey().substring(8), data.get("SKY"), data.get("TMP")));
                        }
                    });

            weeklyForecastList.clear();
            Map<String, List<WeatherResponse.WeatherItem>> dailyItems = items.stream().collect(Collectors.groupingBy(item -> item.fcstDate));
            dailyItems.keySet().stream().sorted().limit(3).forEach(date -> {
                List<WeatherResponse.WeatherItem> dayItems = dailyItems.get(date);
                Optional<String> minTempOpt = dayItems.stream().filter(it -> "TMN".equals(it.category)).map(it -> it.fcstValue).findFirst();
                Optional<String> maxTempOpt = dayItems.stream().filter(it -> "TMX".equals(it.category)).map(it -> it.fcstValue).findFirst();

                if (minTempOpt.isPresent() && maxTempOpt.isPresent()) {
                    String amCond = getWeatherConditionFromHourly(dayItems, "0900");
                    String pmCond = getWeatherConditionFromHourly(dayItems, "1500");
                    int amPrecip = getPrecipFromHourly(dayItems, "0900");
                    int pmPrecip = getPrecipFromHourly(dayItems, "1500");

                    try {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(new SimpleDateFormat("yyyyMMdd", Locale.KOREAN).parse(date));
                        weeklyForecastList.add(new WeeklyForecastItem(
                                new SimpleDateFormat("M/d", Locale.KOREAN).format(cal.getTime()),
                                new SimpleDateFormat("E요일", Locale.KOREAN).format(cal.getTime()),
                                amCond, pmCond, amPrecip, pmPrecip,
                                (int) Double.parseDouble(minTempOpt.get()),
                                (int) Double.parseDouble(maxTempOpt.get())
                        ));
                    } catch (ParseException e) {
                        Log.e(TAG, "단기예보 기반 주간 데이터 파싱 오류", e);
                    }
                }
            });

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    hourlyAdapter.notifyDataSetChanged();
                    weeklyAdapter.notifyDataSetChanged();
                    appendMidTermForecast();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "단기예보 파싱 에러", e);
        }
    }

    private void appendMidTermForecast() {
        if (weeklyTempData == null || weeklyLandData == null || weeklyForecastList.isEmpty() || midTermForecastAppended) {
            return;
        }

        try {
            JsonObject tempItem = weeklyTempData.getAsJsonObject("response").getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();
            JsonObject landItem = weeklyLandData.getAsJsonObject("response").getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();
            midTermForecastAppended = true;

            for (int i = 3; i < 8; i++) { // 3일부터 7일까지 (총 5일) 추가하여 7일 예보 완성
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DATE, i);

                int minTemp = tempItem.has("taMin" + i) ? tempItem.get("taMin" + i).getAsInt() : 0;
                int maxTemp = tempItem.has("taMax" + i) ? tempItem.get("taMax" + i).getAsInt() : 0;
                String amCondition = landItem.has("wf" + i + "Am") ? landItem.get("wf" + i + "Am").getAsString() : "정보 없음";
                String pmCondition = landItem.has("wf" + i + "Pm") ? landItem.get("wf" + i + "Pm").getAsString() : "정보 없음";
                int amPrecip = landItem.has("rnSt" + i + "Am") ? landItem.get("rnSt" + i + "Am").getAsInt() : 0;
                int pmPrecip = landItem.has("rnSt" + i + "Pm") ? landItem.get("rnSt" + i + "Pm").getAsInt() : 0;

                String dateStr = new SimpleDateFormat("M/d", Locale.KOREAN).format(calendar.getTime());
                if (weeklyForecastList.stream().noneMatch(item -> item.getDate().equals(dateStr))) {
                    weeklyForecastList.add(new WeeklyForecastItem(
                            dateStr,
                            new SimpleDateFormat("E요일", Locale.KOREAN).format(calendar.getTime()),
                            amCondition, pmCondition, amPrecip, pmPrecip, minTemp, maxTemp
                    ));
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    weeklyAdapter.notifyDataSetChanged();
                    Log.d(TAG, "중기 예보 데이터 추가 완료. 총 " + weeklyForecastList.size() + "일 예보.");
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "중기예보 데이터 병합/파싱 에러: " + e.getMessage());
        }
    }

    private void displayCurrentWeather(Map<String, String> data) {
        textCurrentTemp.setText(String.format("%s°C", data.getOrDefault("TMP", "--")));
        textPrecipitation.setText(String.format("강수확률 %s%%", data.getOrDefault("POP", "--")));
        textHumidity.setText(String.format("습도 %s%%", data.getOrDefault("REH", "--")));
        textWindSpeed.setText(String.format("바람 %sm/s", data.getOrDefault("WSD", "--")));
        updateWeatherConditionAndIcon(data.getOrDefault("PTY", "0"), data.getOrDefault("SKY", "1"), textCurrentCondition, imageWeatherIcon);
    }

    // --- Helper & Util Methods ---

    private String getWeatherConditionFromHourly(List<WeatherResponse.WeatherItem> items, String time) {
        String pty = items.stream().filter(it -> "PTY".equals(it.category) && time.equals(it.fcstTime)).map(it->it.fcstValue).findFirst().orElse("0");
        String sky = items.stream().filter(it -> "SKY".equals(it.category) && time.equals(it.fcstTime)).map(it->it.fcstValue).findFirst().orElse("1");
        return skyPtyToCondition(pty, sky);
    }

    private int getPrecipFromHourly(List<WeatherResponse.WeatherItem> items, String time) {
        return items.stream().filter(it -> "POP".equals(it.category) && time.equals(it.fcstTime)).mapToInt(it -> Integer.parseInt(it.fcstValue)).findFirst().orElse(0);
    }

    private String skyPtyToCondition(String pty, String sky) {
        int ptyCode = Integer.parseInt(pty);
        int skyCode = Integer.parseInt(sky);
        if (ptyCode > 0) {
            switch (ptyCode) {
                case 1: return "비";
                case 2: return "비/눈";
                case 3: return "눈";
                case 5: return "빗방울";
                case 6: return "빗방울눈날림";
                case 7: return "눈날림";
            }
        }
        switch (skyCode) {
            case 1: return "맑음";
            case 3: return "구름많음";
            case 4: return "흐림";
        }
        return "정보 없음";
    }

    private void updateWeatherConditionAndIcon(String pty, String sky, TextView conditionView, ImageView iconView) {
        String conditionText = skyPtyToCondition(pty, sky);
        conditionView.setText(conditionText);
        iconView.setImageResource(getWeatherIconResource(conditionText));
    }

    private Map<String, String> getBaseTimeFor(String apiType) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN);
        if ("midterm".equals(apiType)) {
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            String time;
            if (hour < 6) {
                cal.add(Calendar.DATE, -1);
                time = "1800";
            } else if (hour < 18) {
                time = "0600";
            } else {
                time = "1800";
            }
            return Map.of("time", dateFormat.format(cal.getTime()) + time);
        } else { // village
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            String time = "2300";
            int[] baseTimes = {2, 5, 8, 11, 14, 17, 20, 23};
            for(int i = baseTimes.length - 1; i >= 0; i--) {
                if (hour >= baseTimes[i]) {
                    time = String.format(Locale.KOREAN, "%02d00", baseTimes[i]);
                    break;
                }
            }
            if (hour < 2) {
                cal.add(Calendar.DATE, -1);
            }
            return Map.of("date", dateFormat.format(cal.getTime()), "time", time);
        }
    }

    private String getMidTermLandCode(String adminArea) {
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

    private String getMidTermTempCode(String locality) {
        if (locality == null) return "11C10301";
        if (locality.contains("서울")) return "11B10101";
        if (locality.contains("인천")) return "11B20201";
        if (locality.contains("수원")) return "11B20601";
        if (locality.contains("파주")) return "11B20305";
        if (locality.contains("춘천")) return "11D10301";
        if (locality.contains("원주")) return "11D10401";
        if (locality.contains("강릉")) return "11D20501";
        if (locality.contains("청주")) return "11C10301";
        if (locality.contains("대전")) return "11C20401";
        if (locality.contains("세종")) return "11C20404";
        if (locality.contains("서산")) return "11C20101";
        if (locality.contains("전주")) return "11F10201";
        if (locality.contains("군산")) return "21F10501";
        if (locality.contains("광주")) return "11F20501";
        if (locality.contains("목포")) return "21F20801";
        if (locality.contains("여수")) return "11F20401";
        if (locality.contains("대구")) return "11H10701";
        if (locality.contains("안동")) return "11H10501";
        if (locality.contains("포항")) return "11H10201";
        if (locality.contains("부산")) return "11H20201";
        if (locality.contains("울산")) return "11H20101";
        if (locality.contains("창원")) return "11H20301";
        if (locality.contains("제주")) return "11G00201";
        if (locality.contains("서귀포")) return "11G00401";
        return "11C10301";
    }

    private void handleApiError(String apiName, Response<?> response) {
        Log.e(TAG, apiName + " API 에러: " + response.code() + " " + response.message());
        if (isAdded()) Toast.makeText(getContext(), apiName + " 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void handleApiFailure(String apiName, Throwable t) {
        Log.e(TAG, apiName + " API 네트워크 실패", t);
        if (isAdded()) Toast.makeText(getContext(), "네트워크 오류로 " + apiName + " 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    @DrawableRes
    private int getWeatherIconResource(String condition) {
        if (condition == null) return R.drawable.ic_launcher_foreground;
        if (condition.contains("비")) return android.R.drawable.ic_secure;
        if (condition.contains("눈")) return android.R.drawable.stat_sys_warning;
        if (condition.contains("맑음")) return android.R.drawable.ic_menu_day;
        if (condition.contains("구름")) return android.R.drawable.ic_menu_gallery;
        if (condition.contains("흐림")) return android.R.drawable.ic_menu_close_clear_cancel;
        return R.drawable.ic_launcher_foreground;
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

    // --- Inner Classes ---

    private static class LatXLngY { public double lat, lng, x, y; }

    public static class HourlyForecastItem {
        private final String time;
        private final String skyCondition;
        private final String temperature;
        public HourlyForecastItem(String time, String sky, String temp) {
            this.time = time.substring(0, 2) + "시";
            this.skyCondition = sky;
            this.temperature = temp;
        }
        public String getTime() { return time; }
        public String getSkyCondition() { return skyCondition; }
        public String getTemperature() { return temperature; }
    }

    public static class WeeklyForecastItem {
        private final String date;
        private final String dayOfWeek;
        private final String amCondition;
        private final String pmCondition;
        private final int amPrecipitation;
        private final int pmPrecipitation;
        private final int minTemp;
        private final int maxTemp;

        public WeeklyForecastItem(String date, String dayOfWeek, String amCondition, String pmCondition,
                                  int amPrecipitation, int pmPrecipitation, int minTemp, int maxTemp) {
            this.date = date;
            this.dayOfWeek = dayOfWeek;
            this.amCondition = amCondition;
            this.pmCondition = pmCondition;
            this.amPrecipitation = amPrecipitation;
            this.pmPrecipitation = pmPrecipitation;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
        }

        public String getDate() { return date; }
        public String getDayOfWeek() { return dayOfWeek; }
        public String getAmCondition() { return amCondition; }
        public String getPmCondition() { return pmCondition; }
        public int getAmPrecipitation() { return amPrecipitation; }
        public int getPmPrecipitation() { return pmPrecipitation; }
        public int getMinTemp() { return minTemp; }
        public int getMaxTemp() { return maxTemp; }
    }


    private class HourlyForecastAdapter extends RecyclerView.Adapter<HourlyForecastAdapter.ForecastViewHolder> {
        private final List<HourlyForecastItem> forecasts;
        public HourlyForecastAdapter(List<HourlyForecastItem> forecasts) { this.forecasts = forecasts; }
        @NonNull @Override
        public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather_forecast, parent, false);
            return new ForecastViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
            HourlyForecastItem forecast = forecasts.get(position);
            holder.textDay.setText(forecast.getTime());
            holder.textHighTemp.setText(String.format("%s°", forecast.getTemperature()));
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
        private final List<WeeklyForecastItem> forecasts;
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
            holder.imageAmIcon.setImageResource(getWeatherIconResource(forecast.getAmCondition()));
            holder.textAmCondition.setText(forecast.getAmCondition());
            holder.textAmPrecip.setText(String.format(Locale.KOREAN, "강수: %d%%", forecast.getAmPrecipitation()));
            holder.imagePmIcon.setImageResource(getWeatherIconResource(forecast.getPmCondition()));
            holder.textPmCondition.setText(forecast.getPmCondition());
            holder.textPmPrecip.setText(String.format(Locale.KOREAN, "강수: %d%%", forecast.getPmPrecipitation()));
            holder.textHighTemp.setText(String.format(Locale.KOREAN, "%d°", forecast.getMaxTemp()));
            holder.textLowTemp.setText(String.format(Locale.KOREAN, "%d°", forecast.getMinTemp()));

            holder.textHighTemp.setTextColor(ContextCompat.getColor(getContext(), R.color.temp_max_red));
            holder.textLowTemp.setTextColor(ContextCompat.getColor(getContext(), R.color.temp_min_blue));
        }
        @Override public int getItemCount() { return forecasts.size(); }
        class WeeklyForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay, textDate, textAmCondition, textPmCondition, textAmPrecip, textPmPrecip, textLowTemp, textHighTemp;
            ImageView imageAmIcon, imagePmIcon;
            WeeklyForecastViewHolder(View itemView) {
                super(itemView);
                textDay = itemView.findViewById(R.id.textWeeklyDay);
                textDate = itemView.findViewById(R.id.textWeeklyDate);
                imageAmIcon = itemView.findViewById(R.id.imageAmIcon);
                textAmCondition = itemView.findViewById(R.id.textAmCondition);
                textAmPrecip = itemView.findViewById(R.id.textAmPrecip);
                imagePmIcon = itemView.findViewById(R.id.imagePmIcon);
                textPmCondition = itemView.findViewById(R.id.textPmCondition);
                textPmPrecip = itemView.findViewById(R.id.textPmPrecip);
                textLowTemp = itemView.findViewById(R.id.textWeeklyLow);
                textHighTemp = itemView.findViewById(R.id.textWeeklyHigh);
            }
        }
    }

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