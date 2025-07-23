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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
 * 날씨 정보를 표시하는 프래그먼트 (기상청 API 연동 최종 수정 버전)
 */
public class WeatherFragment extends Fragment {

    private static final String TAG = "WeatherFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String WEATHER_API_BASE_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/";

    // UI 요소
    private TextView textCurrentLocation, textCurrentTemp, textCurrentCondition, textFeelsLike, textHumidity, textWindSpeed;
    private ImageView imageWeatherIcon;
    private RecyclerView recyclerViewForecast;

    // 서비스 및 데이터
    private FusedLocationProviderClient fusedLocationClient;
    private WeatherApiService weatherApiService;
    private WeatherForecastAdapter forecastAdapter;
    private List<WeatherForecastItem> forecastList = new ArrayList<>();
    private Geocoder geocoder;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather, container, false);
        initializeViews(view);
        initializeServices();
        setupForecastRecyclerView();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissionAndLoadWeather();
    }

    /**
     * 뷰 요소들을 초기화하는 메서드
     */
    private void initializeViews(View view) {
        textCurrentLocation = view.findViewById(R.id.textCurrentLocation);
        textCurrentTemp = view.findViewById(R.id.textCurrentTemp);
        textCurrentCondition = view.findViewById(R.id.textCurrentCondition);
        textFeelsLike = view.findViewById(R.id.textFeelsLike);
        textHumidity = view.findViewById(R.id.textHumidity);
        textWindSpeed = view.findViewById(R.id.textWindSpeed);
        imageWeatherIcon = view.findViewById(R.id.imageWeatherIcon);
        recyclerViewForecast = view.findViewById(R.id.recyclerViewForecast);
    }

    /**
     * 서비스(API, 위치, 지오코더)를 초기화하는 메서드
     */
    private void initializeServices() {
        // [수정] Retrofit이 응답을 String으로 받도록 ScalarsConverterFactory를 사용
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(WEATHER_API_BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        weatherApiService = retrofit.create(WeatherApiService.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(getContext(), Locale.KOREAN);
    }

    /**
     * 시간별 예보 리사이클러뷰를 설정하는 메서드
     */
    private void setupForecastRecyclerView() {
        recyclerViewForecast.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        forecastAdapter = new WeatherForecastAdapter(forecastList);
        recyclerViewForecast.setAdapter(forecastAdapter);
    }

    /**
     * 위치 권한을 확인하고 날씨 정보를 로드하는 메서드
     */
    private void checkPermissionAndLoadWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocationAndFetchWeather();
        }
    }

    /**
     * 현재 위치를 가져와서 날씨 정보를 요청하는 메서드
     */
    private void getCurrentLocationAndFetchWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                updateLocationText(location);
                LatXLngY gridCoords = convertToGrid(location.getLatitude(), location.getLongitude());
                fetchWeatherData((int) gridCoords.x, (int) gridCoords.y);
            } else {
                Toast.makeText(getContext(), "현재 위치를 가져올 수 없어 기본 위치(서울)로 조회합니다.", Toast.LENGTH_SHORT).show();
                textCurrentLocation.setText("서울 특별시");
                fetchWeatherData(60, 127);
            }
        });
    }

    /**
     * 위경도를 주소 텍스트로 변환하여 UI에 표시하는 메서드
     */
    private void updateLocationText(Location location) {
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = (address.getAdminArea() != null ? address.getAdminArea() : "")
                        + " " + (address.getLocality() != null ? address.getLocality() : "")
                        + " " + (address.getThoroughfare() != null ? address.getThoroughfare() : "");
                textCurrentLocation.setText(addressText.trim());
            } else {
                textCurrentLocation.setText("주소 정보 없음");
            }
        } catch (IOException e) {
            Log.e(TAG, "지오코딩 실패", e);
            textCurrentLocation.setText("주소 변환 오류");
        }
    }

    /**
     * 기상청 API를 호출하여 날씨 정보를 가져오는 메서드
     */
    private void fetchWeatherData(int nx, int ny) {
        String apiKey = BuildConfig.KMA_API_KEY;
        String baseDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        String baseTime = getBaseTime();

        weatherApiService.getVillageForecast(apiKey, 200, 1, "JSON", baseDate, baseTime, nx, ny)
                .enqueue(new Callback<String>() { // 응답을 String으로 받음
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseBody = response.body();
                            try {
                                // 응답에서 JSON 시작 부분 찾기
                                int jsonStartIndex = responseBody.indexOf("{");
                                if (jsonStartIndex != -1) {
                                    String jsonResponse = responseBody.substring(jsonStartIndex);
                                    Gson gson = new GsonBuilder().create();
                                    WeatherResponse weatherResponse = gson.fromJson(jsonResponse, WeatherResponse.class);
                                    parseAndDisplayWeather(weatherResponse);
                                } else {
                                    Toast.makeText(getContext(), "응답에서 JSON 데이터를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Invalid Response: " + responseBody);
                                }
                            } catch (Exception e) {
                                Toast.makeText(getContext(), "데이터 파싱 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "JSON Parsing Error", e);
                            }
                        } else {
                            Toast.makeText(getContext(), "날씨 정보를 가져오는 데 실패했습니다 (서버 응답 오류).", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "네트워크 실패", t);
                    }
                });
    }

    /**
     * API 응답 데이터를 파싱하여 UI에 표시하는 메서드
     */
    private void parseAndDisplayWeather(WeatherResponse weatherResponse) {
        if (weatherResponse == null || !"00".equals(weatherResponse.response.header.resultCode)) {
            String errorMsg = weatherResponse != null ? weatherResponse.response.header.resultMsg : "응답 없음";
            Toast.makeText(getContext(), "기상청 오류: " + errorMsg, Toast.LENGTH_SHORT).show();
            return;
        }

        List<WeatherResponse.WeatherItem> items = weatherResponse.response.body.items.item;

        String currentFcstTime = items.stream().map(it -> it.fcstTime).findFirst().orElse("");
        Map<String, String> currentWeatherData = items.stream()
                .filter(it -> it.fcstTime.equals(currentFcstTime))
                .collect(Collectors.toMap(it -> it.category, it -> it.fcstValue, (v1, v2) -> v1));

        displayCurrentWeather(currentWeatherData);

        forecastList.clear();
        Map<String, Map<String, String>> hourlyData = new HashMap<>();
        for(WeatherResponse.WeatherItem item : items) {
            hourlyData.computeIfAbsent(item.fcstTime, k -> new HashMap<>()).put(item.category, item.fcstValue);
        }

        List<String> sortedTimes = new ArrayList<>(hourlyData.keySet());
        Collections.sort(sortedTimes);

        for(String time : sortedTimes) {
            Map<String, String> data = hourlyData.get(time);
            if(data != null && data.containsKey("TMP") && data.containsKey("SKY")) {
                String temp = data.get("TMP") + "°";
                String sky = data.get("SKY");
                forecastList.add(new WeatherForecastItem(time, sky, temp));
            }
        }
        forecastAdapter.notifyDataSetChanged();
    }

    /**
     * 현재 날씨 정보를 UI에 표시하는 메서드
     */
    private void displayCurrentWeather(Map<String, String> data) {
        textCurrentTemp.setText(data.getOrDefault("TMP", "N/A") + "°");
        textHumidity.setText("습도 " + data.getOrDefault("REH", "N/A") + "%");
        textWindSpeed.setText("바람 " + data.getOrDefault("WSD", "N/A") + "m/s");
        updateWeatherCondition(data.getOrDefault("PTY", "0"), data.getOrDefault("SKY", "0"));
    }

    /**
     * 날씨 상태 코드에 따라 아이콘과 텍스트를 설정하는 메서드
     */
    private void updateWeatherCondition(String pty, String sky) {
        int ptyCode = Integer.parseInt(pty);
        int skyCode = Integer.parseInt(sky);

        if (ptyCode == 0) { // 강수 없음
            switch (skyCode) {
                case 1:
                    textCurrentCondition.setText("맑음");
                    imageWeatherIcon.setImageResource(android.R.drawable.ic_menu_day);
                    break;
                case 3:
                    textCurrentCondition.setText("구름많음");
                    imageWeatherIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                    break;
                case 4:
                    textCurrentCondition.setText("흐림");
                    imageWeatherIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    break;
                default:
                    textCurrentCondition.setText("정보 없음");
            }
        } else { // 강수 있음
            switch (ptyCode) {
                case 1: textCurrentCondition.setText("비"); break;
                case 2: textCurrentCondition.setText("비/눈"); break;
                case 3: textCurrentCondition.setText("눈"); break;
                case 5: textCurrentCondition.setText("빗방울"); break;
                case 6: textCurrentCondition.setText("빗방울/눈날림"); break;
                case 7: textCurrentCondition.setText("눈날림"); break;
            }
            imageWeatherIcon.setImageResource(android.R.drawable.ic_dialog_info);
        }
    }

    /**
     * API 명세에 맞는 발표 시각을 계산하는 메서드
     */
    private String getBaseTime() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        if (hour < 2 || (hour == 2 && minute <= 10)) {
            cal.add(Calendar.DATE, -1);
            return "2300";
        }
        if (hour < 5 || (hour == 5 && minute <= 10)) return "0200";
        if (hour < 8 || (hour == 8 && minute <= 10)) return "0500";
        if (hour < 11 || (hour == 11 && minute <= 10)) return "0800";
        if (hour < 14 || (hour == 14 && minute <= 10)) return "1100";
        if (hour < 17 || (hour == 17 && minute <= 10)) return "1400";
        if (hour < 20 || (hour == 20 && minute <= 10)) return "1700";
        if (hour < 23 || (hour == 23 && minute <= 10)) return "2000";
        return "2300";
    }

    /**
     * 위경도를 기상청 격자 좌표로 변환하는 메서드
     */
    private LatXLngY convertToGrid(double lat, double lng) {
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

        LatXLngY rs = new LatXLngY();
        rs.lat = lat;
        rs.lng = lng;
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

    private static class LatXLngY {
        public double lat, lng, x, y;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndFetchWeather();
            } else {
                Toast.makeText(getContext(), "위치 권한이 거부되었습니다. 앱을 사용하려면 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public static class WeatherForecastItem {
        private String time, skyCondition, temperature;

        public WeatherForecastItem(String time, String skyCondition, String temperature) {
            this.time = time.substring(0, 2) + "시";
            this.skyCondition = skyCondition;
            this.temperature = temperature;
        }

        public String getTime() { return time; }
        public String getSkyCondition() { return skyCondition; }
        public String getTemperature() { return temperature; }
    }

    private class WeatherForecastAdapter extends RecyclerView.Adapter<WeatherForecastAdapter.ForecastViewHolder> {
        private List<WeatherForecastItem> forecasts;

        public WeatherForecastAdapter(List<WeatherForecastItem> forecasts) {
            this.forecasts = forecasts;
        }

        @NonNull
        @Override
        public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather_forecast, parent, false);
            return new ForecastViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
            WeatherForecastItem forecast = forecasts.get(position);
            holder.textDay.setText(forecast.getTime());
            holder.textHighTemp.setText(forecast.getTemperature());
            holder.textLowTemp.setVisibility(View.GONE);

            int skyCode = Integer.parseInt(forecast.getSkyCondition());
            switch (skyCode) {
                case 1:
                    holder.textCondition.setText("맑음");
                    holder.imageIcon.setImageResource(android.R.drawable.ic_menu_day);
                    break;
                case 3:
                    holder.textCondition.setText("구름많음");
                    holder.imageIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                    break;
                case 4:
                    holder.textCondition.setText("흐림");
                    holder.imageIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    break;
                default:
                    holder.textCondition.setText("-");
            }
        }

        @Override
        public int getItemCount() {
            return forecasts.size();
        }

        class ForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay, textCondition, textHighTemp, textLowTemp;
            ImageView imageIcon;

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
}