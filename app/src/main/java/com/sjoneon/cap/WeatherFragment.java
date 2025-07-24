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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * 날씨 정보를 표시하는 프래그먼트 (주간예보 기능 완성 버전)
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

    // [추가] 주간 예보 데이터 임시 저장 변수
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
        textPrecipitation = view.findViewById(R.id.textFeelsLike); // ID는 그대로 사용, 변수명 변경
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
        midTermTempCode = "11C10000"; // 충청북도 (중기기온)
        midTermLandCode = "11C10101"; // 청주 (중기육상)
        textCurrentLocation.setText("충청북도 청주시");
    }

    /**
     * 모든 날씨 API를 순차적으로 호출하는 메서드
     */
    private void fetchWeatherData() {
        String apiKey = BuildConfig.KMA_API_KEY;
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().getTime());

        // 데이터 리셋
        weeklyTempData = null;
        weeklyLandData = null;

        fetchVillageForecast(apiKey, currentDate, 0);
        fetchMidTermTemp(apiKey, 0);
        fetchMidLandForecast(apiKey, 0); // [추가] 중기육상예보 호출
    }

    // --- API Fetching and Parsing ---

    private void fetchVillageForecast(String apiKey, String baseDate, int retryCount) {
        String baseTime = getBaseTimeFor("village");
        weatherApiService.getVillageForecast(apiKey, 500, 1, "JSON", baseDate, baseTime, currentNx, currentNy)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            parseAndDisplayShortTermWeather(response.body());
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
                            try {
                                JsonObject responseJson = lenientGson.fromJson(response.body(), JsonObject.class);
                                weeklyTempData = responseJson;
                                processAndDisplayWeeklyForecast(); // 데이터 병합 시도
                            } catch (Exception e) {
                                Log.e(TAG, "중기기온 파싱 중 에러", e);
                            }
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

    /**
     * [추가] 중기육상예보 API 호출
     */
    private void fetchMidLandForecast(String apiKey, int retryCount) {
        if (midTermLandCode == null) return;
        String baseTime = getBaseTimeFor("midterm");

        weatherApiService.getMidLandForecast(apiKey, midTermLandCode, baseTime, "JSON")
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JsonObject responseJson = lenientGson.fromJson(response.body(), JsonObject.class);
                                weeklyLandData = responseJson;
                                processAndDisplayWeeklyForecast(); // 데이터 병합 시도
                            } catch (Exception e) {
                                Log.e(TAG, "중기육상 파싱 중 에러", e);
                            }
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

    private void parseAndDisplayShortTermWeather(String responseBody) {
        try {
            JsonObject responseJson = lenientGson.fromJson(responseBody, JsonObject.class);
            JsonObject responseObj = responseJson.getAsJsonObject("response");
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
        } catch (JsonSyntaxException | IllegalStateException e) {
            Log.e(TAG, "단기예보 파싱 에러: " + e.getMessage());
            Log.d(TAG, "비정상 응답(단기): " + responseBody);
        }
    }

    /**
     * [수정] 두 개의 주간예보 데이터를 병합하여 UI에 표시
     */
    private void processAndDisplayWeeklyForecast() {
        // 기온과 날씨 상태 데이터가 모두 도착했을 때만 실행
        if (weeklyTempData == null || weeklyLandData == null) {
            return;
        }

        try {
            // 기온 데이터 파싱
            JsonObject tempResponseObj = weeklyTempData.getAsJsonObject("response");
            if (tempResponseObj == null || !"00".equals(tempResponseObj.getAsJsonObject("header").get("resultCode").getAsString())) {
                Log.e(TAG, "주간 기온 API 응답 오류");
                return;
            }
            JsonObject tempItem = tempResponseObj.getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();

            // 날씨 상태 데이터 파싱
            JsonObject landResponseObj = weeklyLandData.getAsJsonObject("response");
            if (landResponseObj == null || !"00".equals(landResponseObj.getAsJsonObject("header").get("resultCode").getAsString())) {
                Log.e(TAG, "주간 날씨 상태 API 응답 오류");
                return;
            }
            JsonObject landItem = landResponseObj.getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();

            weeklyForecastList.clear();
            for (int i = 3; i <= 7; i++) {
                if (tempItem.has("taMin" + i) && tempItem.has("taMax" + i) && landItem.has("wf" + i)) {
                    int minTemp = tempItem.get("taMin" + i).getAsInt();
                    int maxTemp = tempItem.get("taMax" + i).getAsInt();
                    String weatherCondition = landItem.get("wf" + i).getAsString();
                    weeklyForecastList.add(new WeeklyForecastItem(i, minTemp, maxTemp, weatherCondition));
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    weeklyAdapter.notifyDataSetChanged();
                    Log.d(TAG, "주간 예보 UI 업데이트 완료. " + weeklyForecastList.size() + "개 항목.");
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "주간예보 데이터 병합/파싱 에러: " + e.getMessage());
        }
    }

    /**
     * 현재 날씨 정보를 UI에 표시하는 메서드 (강수확률 표시로 수정)
     */
    private void displayCurrentWeather(Map<String, String> data) {
        textCurrentTemp.setText(data.getOrDefault("TMP", "--") + "°C");
        textPrecipitation.setText("강수확률 " + data.getOrDefault("POP", "--") + "%");
        textHumidity.setText("습도 " + data.getOrDefault("REH", "--") + "%");
        textWindSpeed.setText("바람 " + data.getOrDefault("WSD", "--") + "m/s");
        updateWeatherConditionAndIcon(data.getOrDefault("PTY", "0"), data.getOrDefault("SKY", "1"), textCurrentCondition, imageWeatherIcon);
    }

    /**
     * 날씨 상태 코드에 따라 아이콘과 텍스트를 설정하는 공용 메서드
     */
    private void updateWeatherConditionAndIcon(String pty, String sky, TextView conditionView, ImageView iconView) {
        int ptyCode = Integer.parseInt(pty);
        int skyCode = Integer.parseInt(sky);
        String conditionText = "정보 없음";
        int iconRes = R.drawable.ic_launcher_foreground;

        if (ptyCode == 0) { // 강수 없음
            switch (skyCode) {
                case 1: conditionText = "맑음"; break;
                case 3: conditionText = "구름많음"; break;
                case 4: conditionText = "흐림"; break;
            }
        } else { // 강수 있음
            switch (ptyCode) {
                case 1: conditionText = "비"; break;
                case 2: conditionText = "비/눈"; break;
                case 3: conditionText = "눈"; break;
                case 5: case 6: case 7:
                    conditionText = "진눈깨비"; break;
            }
        }
        conditionView.setText(conditionText);
        iconView.setImageResource(iconRes);
    }

    // --- Helper & Util Methods ---

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
        if (adminArea.contains("서울")) return "11B00000";
        if (adminArea.contains("경기")) return "11B00000";
        if (adminArea.contains("인천")) return "11B00000";
        if (adminArea.contains("강원")) return "11D10000";
        if (adminArea.contains("충남")) return "11C20000";
        if (adminArea.contains("충북")) return "11C10000";
        if (adminArea.contains("대전")) return "11C20000";
        if (adminArea.contains("세종")) return "11C20000";
        if (adminArea.contains("광주")) return "11F20000";
        if (adminArea.contains("전남")) return "11F20000";
        if (adminArea.contains("전북")) return "11F10000";
        if (adminArea.contains("대구")) return "11H10000";
        if (adminArea.contains("경북")) return "11H10000";
        if (adminArea.contains("부산")) return "11H20201";
        if (adminArea.contains("울산")) return "11H20101";
        if (adminArea.contains("경남")) return "11H20000";
        if (adminArea.contains("제주")) return "11G00000";
        return "11C10000"; // 기본값: 충북
    }

    /**
     * [추가] 행정구역명으로 중기육상예보 지역 코드를 반환하는 메서드
     */
    private String getMidTermLandCode(String adminArea) {
        if (adminArea.contains("서울")) return "11B10101";
        if (adminArea.contains("인천")) return "11B20201";
        if (adminArea.contains("경기")) return "11B20601"; // 수원 기준
        if (adminArea.contains("강원")) return "11D10301"; // 춘천 기준
        if (adminArea.contains("충북")) return "11C10101"; // 청주 기준
        if (adminArea.contains("충남")) return "11C20101"; // 홍성 기준
        if (adminArea.contains("대전")) return "11C20401";
        if (adminArea.contains("세종")) return "11C20404";
        if (adminArea.contains("광주")) return "11F20501";
        if (adminArea.contains("전북")) return "11F10201"; // 전주 기준
        if (adminArea.contains("전남")) return "21F20801"; // 목포 기준
        if (adminArea.contains("대구")) return "11H10701";
        if (adminArea.contains("경북")) return "11H10501"; // 안동 기준
        if (adminArea.contains("부산")) return "11H20201";
        if (adminArea.contains("울산")) return "11H20101";
        if (adminArea.contains("경남")) return "11H20301"; // 창원 기준
        if (adminArea.contains("제주")) return "11G00201";
        return "11C10101"; // 기본값: 청주
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
        private String day; private int minTemp; private int maxTemp; private String condition;
        public WeeklyForecastItem(int dayAfter, int minTemp, int maxTemp, String condition) {
            Calendar cal = Calendar.getInstance(); cal.add(Calendar.DATE, dayAfter);
            this.day = new SimpleDateFormat("E요일", Locale.KOREAN).format(cal.getTime());
            this.minTemp = minTemp; this.maxTemp = maxTemp; this.condition = condition;
        }
        public String getDay() { return day; }
        public int getMinTemp() { return minTemp; }
        public int getMaxTemp() { return maxTemp; }
        public String getCondition() { return condition; }
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
            holder.textDay.setText(forecast.getDay());
            holder.textLowTemp.setText(forecast.getMinTemp() + "°");
            holder.textHighTemp.setText(forecast.getMaxTemp() + "°");
            holder.imageIcon.setImageResource(R.drawable.ic_launcher_foreground);
        }
        @Override public int getItemCount() { return forecasts.size(); }
        class WeeklyForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay, textLowTemp, textHighTemp; ImageView imageIcon;
            WeeklyForecastViewHolder(View itemView) {
                super(itemView);
                textDay = itemView.findViewById(R.id.textWeeklyDay);
                textLowTemp = itemView.findViewById(R.id.textWeeklyLow);
                textHighTemp = itemView.findViewById(R.id.textWeeklyHigh);
                imageIcon = itemView.findViewById(R.id.imageWeeklyIcon);
            }
        }
    }

    // --- 위치 변환 관련 내부 클래스 및 메서드 ---
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