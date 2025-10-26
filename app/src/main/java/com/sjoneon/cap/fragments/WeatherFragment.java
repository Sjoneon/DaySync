// /app/src/main/java/com/sjoneon/cap/WeatherFragment.java

package com.sjoneon.cap.fragments;

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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * 날씨 정보를 표시하는 프래그먼트 (단기예보 전용 최종 수정본)
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
    private FusedLocationProviderClient fusedLocationProviderClient;
    private WeatherApiService weatherApiService;
    private Geocoder geocoder;
    private HourlyForecastAdapter hourlyAdapter;
    private WeeklyForecastAdapter weeklyAdapter;
    private List<WeatherForecastItem> hourlyForecastList = new ArrayList<>();
    private List<WeeklyForecastItem> weeklyForecastList = new ArrayList<>();
    private Gson lenientGson;
    private FusedLocationProviderClient fusedLocationClient;

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
        String apiKey = BuildConfig.KMA_API_KEY;
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        shortTermData = null;
        weeklyForecastList.clear();
        fetchVillageForecast(apiKey, currentDate, 0);
    }

    private void fetchVillageForecast(String apiKey, String baseDate, int retryCount) {
        String baseTime = getBaseTimeFor("village");
        weatherApiService.getVillageForecast(apiKey, 500, 1, "JSON", baseDate, baseTime, currentNx, currentNy)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            shortTermData = lenientGson.fromJson(response.body(), JsonObject.class);
                            parseAndDisplayShortTermWeather();
                            parseAndDisplayWeeklyForecast();
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
            JsonArray itemsArray = shortTermData.getAsJsonObject("response").getAsJsonObject("body").getAsJsonObject("items").getAsJsonArray("item");
            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(itemsArray, new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType());
            Map<String, List<WeatherResponse.WeatherItem>> groupedByDate = items.stream().collect(Collectors.groupingBy(item -> item.fcstDate));

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.KOREAN);

            for (int i = 0; i <= 2; i++) { // 오늘(0), 내일(1), 모레(2)
                cal.setTime(Calendar.getInstance().getTime());
                cal.add(Calendar.DATE, i);
                String date = sdf.format(cal.getTime());

                if (groupedByDate.containsKey(date)) {
                    List<WeatherResponse.WeatherItem> dailyItems = groupedByDate.get(date);

                    int maxTemp = dailyItems.stream().filter(item -> "TMX".equals(item.category)).mapToInt(item -> (int)Double.parseDouble(item.fcstValue)).findFirst()
                            .orElse((int) dailyItems.stream().filter(item->"TMP".equals(item.category)).mapToDouble(item->Double.parseDouble(item.fcstValue)).max().orElse(0.0));
                    int minTemp = dailyItems.stream().filter(item -> "TMN".equals(item.category)).mapToInt(item -> (int)Double.parseDouble(item.fcstValue)).findFirst()
                            .orElse((int) dailyItems.stream().filter(item->"TMP".equals(item.category)).mapToDouble(item->Double.parseDouble(item.fcstValue)).min().orElse(0.0));

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
        if (condition == null) return android.R.drawable.ic_menu_help;
        if (condition.contains("맑음")) return android.R.drawable.ic_menu_day;
        if (condition.contains("구름")) return android.R.drawable.ic_menu_gallery;
        if (condition.contains("흐림")) return android.R.drawable.ic_menu_close_clear_cancel;
        if (condition.contains("비")) return android.R.drawable.ic_menu_send;
        if (condition.contains("눈")) return android.R.drawable.ic_menu_compass;
        return android.R.drawable.ic_menu_help;
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