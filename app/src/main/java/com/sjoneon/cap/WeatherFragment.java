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
import java.util.Date;
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
 * 날씨 정보를 표시하는 프래그먼트 (오류 수정 최종본)
 *
 * @author Gemini
 * @version 1.3
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
    private FusedLocationProviderClient fusedLocationClient; // [수정] 변수명 원복
    private WeatherApiService weatherApiService;
    private Geocoder geocoder;
    private HourlyForecastAdapter hourlyAdapter;
    private WeeklyForecastAdapter weeklyAdapter;
    private final List<WeatherForecastItem> hourlyForecastList = new ArrayList<>();
    private final List<WeeklyForecastItem> weeklyForecastList = new ArrayList<>();
    private Gson lenientGson;

    // 위치 정보
    private int currentNx, currentNy;
    private String midTermTempCode, midTermLandCode;

    // 주간 예보 데이터 임시 저장 변수
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

    /**
     * XML 레이아웃의 View들을 초기화합니다.
     */
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

    /**
     * 서비스 관련 객체들을 초기화합니다.
     */
    private void initializeServices() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(KMA_API_BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        weatherApiService = retrofit.create(WeatherApiService.class);

        if (getActivity() != null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        }
        if (getContext() != null) {
            geocoder = new Geocoder(getContext(), Locale.KOREAN);
        }
        lenientGson = new GsonBuilder().setLenient().create();
    }

    /**
     * RecyclerView들을 설정합니다.
     */
    private void setupRecyclerViews() {
        if (getContext() == null) return;
        recyclerViewHourlyForecast.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        hourlyAdapter = new HourlyForecastAdapter(hourlyForecastList);
        recyclerViewHourlyForecast.setAdapter(hourlyAdapter);

        recyclerViewWeeklyForecast.setLayoutManager(new LinearLayoutManager(getContext()));
        weeklyAdapter = new WeeklyForecastAdapter(weeklyForecastList);
        recyclerViewWeeklyForecast.setAdapter(weeklyAdapter);
    }

    /**
     * 위치 권한을 확인하고 날씨 정보를 로드합니다.
     */
    private void checkPermissionAndLoadWeather() {
        if (getContext() != null && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocationAndFetchWeather();
        }
    }

    /**
     * 현재 위치를 가져와 날씨 정보를 요청합니다.
     */
    private void getCurrentLocationAndFetchWeather() {
        if (getContext() == null || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        if (getActivity() == null || fusedLocationClient == null) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                updateLocationInfo(location);
                fetchWeatherData();
            } else {
                if (getContext() != null) Toast.makeText(getContext(), "현재 위치를 가져올 수 없어 기본 위치(청주)로 조회합니다.", Toast.LENGTH_SHORT).show();
                setFallbackLocation();
                fetchWeatherData();
            }
        }).addOnFailureListener(e -> {
            if (getContext() != null) Toast.makeText(getContext(), "위치 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
            setFallbackLocation();
            fetchWeatherData();
        });
    }

    /**
     * 위치 정보를 UI와 API 요청에 맞게 업데이트합니다.
     */
    private void updateLocationInfo(Location location) {
        if (getContext() == null || geocoder == null) return;
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

    /**
     * 위치 정보를 가져올 수 없을 때 기본 위치(청주)로 설정합니다.
     */
    private void setFallbackLocation() {
        currentNx = 69;
        currentNy = 107;
        midTermTempCode = "11C10000";
        midTermLandCode = "11C10101";
        if(textCurrentLocation != null) textCurrentLocation.setText("충청북도 청주시");
    }

    /**
     * 모든 날씨 API를 순차적으로 호출합니다.
     */
    private void fetchWeatherData() {
        String apiKey = BuildConfig.KMA_API_KEY;
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().getTime());

        weeklyTempData = null;
        weeklyLandData = null;

        fetchVillageForecast(apiKey, currentDate, 0);
        fetchMidTermTemp(apiKey, 0);
        fetchMidLandForecast(apiKey, 0);
    }

    /**
     * 단기예보 API를 호출합니다.
     */
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

    /**
     * 중기기온예보 API를 호출합니다.
     */
    private void fetchMidTermTemp(String apiKey, int retryCount) {
        if (midTermTempCode == null) return;
        String baseTime = getBaseTimeFor("midterm");

        weatherApiService.getMidTermTemperature(apiKey, midTermTempCode, baseTime, "JSON")
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                weeklyTempData = lenientGson.fromJson(response.body(), JsonObject.class);
                                processAndDisplayWeeklyForecast();
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
     * 중기육상예보 API를 호출합니다.
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
                                weeklyLandData = lenientGson.fromJson(response.body(), JsonObject.class);
                                processAndDisplayWeeklyForecast();
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

    /**
     * 단기예보 API 응답을 파싱하여 UI에 표시합니다.
     */
    private void parseAndDisplayShortTermWeather(String responseBody) {
        try {
            JsonObject responseJson = lenientGson.fromJson(responseBody, JsonObject.class);
            JsonObject responseObj = responseJson.getAsJsonObject("response");
            if (responseObj == null) throw new IllegalStateException("Response object is null");

            JsonObject header = responseObj.getAsJsonObject("header");
            if(header == null || !"00".equals(header.get("resultCode").getAsString())){
                if (isAdded() && getContext() != null) Toast.makeText(getContext(), "기상청 오류(단기): " + header.get("resultMsg").getAsString(), Toast.LENGTH_SHORT).show();
                return;
            }

            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(
                    responseObj.getAsJsonObject("body").getAsJsonObject("items").get("item"),
                    new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType()
            );

            String currentHour = new SimpleDateFormat("HH", Locale.getDefault()).format(new Date());
            String currentTime = currentHour + "00";

            Map<String, List<WeatherResponse.WeatherItem>> groupedByTime = items.stream()
                    .collect(Collectors.groupingBy(item -> item.fcstTime));

            String targetTime = groupedByTime.keySet().stream()
                    .min(Comparator.comparing(time -> {
                        int diff = Integer.parseInt(currentTime.substring(0, 2)) - Integer.parseInt(time.substring(0,2));
                        return diff < 0 ? diff + 24 : diff;
                    }))
                    .orElse("");

            if (!targetTime.isEmpty()) {
                Map<String, String> currentWeatherData = groupedByTime.get(targetTime).stream()
                        .collect(Collectors.toMap(it -> it.category, it -> it.fcstValue, (v1, v2) -> v1));
                displayCurrentWeather(currentWeatherData);
            }

            hourlyForecastList.clear();
            groupedByTime.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Map<String, String> data = entry.getValue().stream()
                                .collect(Collectors.toMap(item -> item.category, item -> item.fcstValue, (v1, v2) -> v1));
                        if (data.containsKey("TMP") && data.containsKey("SKY") && data.containsKey("PTY")) {
                            hourlyForecastList.add(new WeatherForecastItem(entry.getKey(), data.get("SKY"), data.get("PTY"), data.get("TMP")));
                        }
                    });
            if (isAdded() && getActivity() != null) requireActivity().runOnUiThread(() -> hourlyAdapter.notifyDataSetChanged());

        } catch (JsonSyntaxException | IllegalStateException e) {
            Log.e(TAG, "단기예보 파싱 에러: " + e.getMessage());
        }
    }

    /**
     * 중기예보 데이터를 병합하여 UI에 표시합니다.
     */
    private void processAndDisplayWeeklyForecast() {
        if (weeklyTempData == null || weeklyLandData == null) return;

        try {
            JsonObject tempResponseObj = weeklyTempData.getAsJsonObject("response");
            if (tempResponseObj == null || !"00".equals(tempResponseObj.getAsJsonObject("header").get("resultCode").getAsString())) {
                Log.e(TAG, "주간 기온 API 응답 오류");
                return;
            }
            JsonObject tempItem = tempResponseObj.getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();

            JsonObject landResponseObj = weeklyLandData.getAsJsonObject("response");
            if (landResponseObj == null || !"00".equals(landResponseObj.getAsJsonObject("header").get("resultCode").getAsString())) {
                Log.e(TAG, "주간 날씨 상태 API 응답 오류");
                return;
            }
            JsonObject landItem = landResponseObj.getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item").get(0).getAsJsonObject();

            weeklyForecastList.clear();
            for (int i = 3; i <= 7; i++) {
                if (tempItem.has("taMin" + i) && tempItem.has("taMax" + i) && landItem.has("wf" + i + "Am")) {
                    int minTemp = tempItem.get("taMin" + i).getAsInt();
                    int maxTemp = tempItem.get("taMax" + i).getAsInt();
                    String weatherCondition = landItem.get("wf" + i + "Am").getAsString();
                    weeklyForecastList.add(new WeeklyForecastItem(i, minTemp, maxTemp, weatherCondition));
                }
            }

            if (isAdded() && getActivity() != null) {
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
     * 현재 날씨 데이터를 UI에 표시합니다.
     */
    private void displayCurrentWeather(Map<String, String> data) {
        if (!isAdded()) return;
        textCurrentTemp.setText(String.format("%s°C", data.getOrDefault("TMP", "--")));
        textPrecipitation.setText(String.format("강수확률 %s%%", data.getOrDefault("POP", "--")));
        textHumidity.setText(String.format("습도 %s%%", data.getOrDefault("REH", "--")));
        textWindSpeed.setText(String.format("바람 %sm/s", data.getOrDefault("WSD", "--")));
        updateWeatherConditionAndIcon(data.getOrDefault("PTY", "0"), data.getOrDefault("SKY", "1"), textCurrentCondition, imageWeatherIcon);
    }

    /**
     * 날씨 상태 코드에 따라 아이콘과 텍스트를 설정합니다.
     */
    private void updateWeatherConditionAndIcon(String pty, String sky, TextView conditionView, ImageView iconView) {
        int ptyCode = Integer.parseInt(pty);
        int skyCode = Integer.parseInt(sky);
        String conditionText;
        int iconRes = R.drawable.ic_launcher_foreground;

        if (ptyCode == 0) {
            switch (skyCode) {
                case 1: conditionText = "맑음"; break;
                case 3: conditionText = "구름많음"; break;
                case 4: conditionText = "흐림"; break;
                default: conditionText = "정보 없음"; break;
            }
        } else {
            switch (ptyCode) {
                case 1: conditionText = "비"; break;
                case 2: conditionText = "비/눈"; break;
                case 3: conditionText = "눈"; break;
                case 5: conditionText = "빗방울"; break;
                case 6: conditionText = "빗방울/눈날림"; break;
                case 7: conditionText = "눈날림"; break;
                default: conditionText = "강수"; break;
            }
        }
        if (isAdded()) {
            conditionView.setText(conditionText);
            iconView.setImageResource(iconRes);
        }
    }

    /**
     * API 종류에 따라 올바른 base_time을 계산하여 반환합니다.
     */
    private String getBaseTimeFor(String apiType) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if ("midterm".equals(apiType)) {
            if (hour < 6) {
                cal.add(Calendar.DATE, -1);
                return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()) + "1800";
            } else if (hour < 18) {
                return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()) + "0600";
            } else {
                return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.getTime()) + "1800";
            }
        } else {
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
        if (adminArea.contains("서울") || adminArea.contains("인천") || adminArea.contains("경기")) return "11B00000";
        if (adminArea.contains("강원")) return "11D10000";
        if (adminArea.contains("대전") || adminArea.contains("세종") || adminArea.contains("충남")) return "11C20000";
        if (adminArea.contains("충북")) return "11C10000";
        if (adminArea.contains("광주") || adminArea.contains("전남")) return "11F20000";
        if (adminArea.contains("전북")) return "11F10000";
        if (adminArea.contains("대구") || adminArea.contains("경북")) return "11H10000";
        if (adminArea.contains("부산") || adminArea.contains("울산") || adminArea.contains("경남")) return "11H20000";
        if (adminArea.contains("제주")) return "11G00000";
        return "11C10000";
    }

    private void handleApiError(String apiName, Response<?> response) {
        Log.e(TAG, apiName + " API 에러: " + response.code() + " " + response.message());
        if(isAdded() && getContext() != null) Toast.makeText(getContext(), apiName + " 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void handleApiFailure(String apiName, Throwable t) {
        Log.e(TAG, apiName + " API 네트워크 실패", t);
        if(isAdded() && getContext() != null) Toast.makeText(getContext(), "네트워크 오류로 " + apiName + " 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
    }

    public static class WeatherForecastItem {
        private final String time, skyCondition, ptyCondition, temperature;
        public WeatherForecastItem(String time, String sky, String pty, String temp) {
            this.time = time.substring(0, 2) + "시"; this.skyCondition = sky; this.ptyCondition = pty; this.temperature = temp;
        }
        public String getTime() { return time; }
        public String getSkyCondition() { return skyCondition; }
        public String getPtyCondition() { return ptyCondition; }
        public String getTemperature() { return temperature; }
    }

    public static class WeeklyForecastItem {
        private final String day; private final int minTemp; private final int maxTemp; private final String condition;
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
        private final List<WeatherForecastItem> forecasts;
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
            holder.textHighTemp.setText(String.format("%s°", forecast.getTemperature()));
            holder.textLowTemp.setVisibility(View.GONE);
            updateWeatherConditionAndIcon(forecast.getPtyCondition(), forecast.getSkyCondition(), holder.textCondition, holder.imageIcon);
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
            holder.textDay.setText(forecast.getDay());
            holder.textCondition.setText(forecast.getCondition());
            holder.textLowTemp.setText(String.format("%d°", forecast.getMinTemp()));
            holder.textHighTemp.setText(String.format("%d°", forecast.getMaxTemp()));
            holder.imageIcon.setImageResource(R.drawable.ic_launcher_foreground);
        }
        @Override public int getItemCount() { return forecasts.size(); }
        class WeeklyForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay, textLowTemp, textHighTemp, textCondition; ImageView imageIcon;
            WeeklyForecastViewHolder(View itemView) {
                super(itemView);
                textDay = itemView.findViewById(R.id.textWeeklyDay);
                textLowTemp = itemView.findViewById(R.id.textWeeklyLow);
                textHighTemp = itemView.findViewById(R.id.textWeeklyHigh);
                textCondition = itemView.findViewById(R.id.textWeeklyCondition);
                imageIcon = itemView.findViewById(R.id.imageWeeklyIcon);
            }
        }
    }

    private LatXLngY convertToGrid(double lat, double lng) {
        double RE = 6371.00877, GRID = 5.0, SLAT1 = 30.0, SLAT2 = 60.0,
                OLON = 126.0, OLAT = 38.0, XO = 43, YO = 136;
        double DEGRAD = Math.PI / 180.0;
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD, slat2 = SLAT2 * DEGRAD, olon = OLON * DEGRAD, olat = OLAT * DEGRAD;
        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);
        LatXLngY rs = new LatXLngY();
        rs.lat = lat; rs.lng = lng;
        double ra = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + (lat) * DEGRAD * 0.5), sn);
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
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndFetchWeather();
            } else {
                if (getContext() != null) Toast.makeText(getContext(), "위치 권한이 거부되어 기본 위치로 조회합니다.", Toast.LENGTH_LONG).show();
                setFallbackLocation();
                fetchWeatherData();
            }
        }
    }
}