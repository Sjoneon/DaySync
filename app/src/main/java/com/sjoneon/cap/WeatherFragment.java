package com.sjoneon.cap;

import android.Manifest;
import android.content.pm.PackageManager;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 날씨 정보를 표시하는 프래그먼트
 */
public class WeatherFragment extends Fragment {

    private static final String TAG = "WeatherFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI 요소들
    private TextView textCurrentLocation;
    private TextView textCurrentTemp;
    private TextView textCurrentCondition;
    private TextView textFeelsLike;
    private TextView textHumidity;
    private TextView textWindSpeed;
    private ImageView imageWeatherIcon;
    private RecyclerView recyclerViewForecast;

    // 데이터
    private FusedLocationProviderClient fusedLocationClient;
    private List<WeatherForecastItem> forecastList = new ArrayList<>();
    private WeatherForecastAdapter forecastAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather, container, false);

        // 뷰 초기화
        initializeViews(view);

        // 위치 서비스 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // 예측 날씨 리사이클러뷰 설정
        setupForecastRecyclerView();

        // 현재 위치의 날씨 정보 로드
        loadWeatherData();

        return view;
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
     * 날씨 예측 리사이클러뷰를 설정하는 메서드
     */
    private void setupForecastRecyclerView() {
        recyclerViewForecast.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        forecastAdapter = new WeatherForecastAdapter(forecastList);
        recyclerViewForecast.setAdapter(forecastAdapter);
    }

    /**
     * 날씨 데이터를 로드하는 메서드
     */
    private void loadWeatherData() {
        // 위치 권한 확인
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }

        // 현재 위치 가져오기
        getCurrentLocationAndWeather();
    }

    /**
     * 위치 권한을 요청하는 메서드
     */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    /**
     * 현재 위치를 가져와서 날씨 정보를 로드하는 메서드
     */
    private void getCurrentLocationAndWeather() {
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            // 실제로는 여기서 날씨 API를 호출해야 하지만,
                            // 현재는 더미 데이터로 구현
                            displayDummyWeatherData("청주시");
                        } else {
                            // 기본 위치로 날씨 정보 표시
                            displayDummyWeatherData("기본 위치");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "위치 정보 가져오기 실패: " + e.getMessage());
                        displayDummyWeatherData("위치 불명");
                    });

        } catch (SecurityException e) {
            Log.e(TAG, "위치 권한 오류: " + e.getMessage());
            displayDummyWeatherData("권한 필요");
        }
    }

    /**
     * 더미 날씨 데이터를 표시하는 메서드 (실제로는 API 호출 결과를 사용)
     */
    private void displayDummyWeatherData(String locationName) {
        // 현재 날씨 정보 표시
        textCurrentLocation.setText(locationName);
        textCurrentTemp.setText("22°C");
        textCurrentCondition.setText("맑음");
        textFeelsLike.setText("체감온도 24°C");
        textHumidity.setText("습도 45%");
        textWindSpeed.setText("바람 2.1m/s");

        // 날씨 아이콘 설정 (실제로는 날씨 상태에 따라 다른 아이콘 사용)
        imageWeatherIcon.setImageResource(android.R.drawable.ic_menu_day);

        // 일주일 예보 더미 데이터 생성
        generateDummyForecastData();
    }

    /**
     * 더미 일기예보 데이터를 생성하는 메서드
     */
    private void generateDummyForecastData() {
        forecastList.clear();

        // 일주일 예보 생성
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("E", Locale.KOREAN);

        String[] conditions = {"맑음", "구름", "비", "흐림", "맑음", "구름", "맑음"};
        int[] highs = {25, 23, 18, 20, 26, 24, 27};
        int[] lows = {15, 13, 12, 11, 16, 14, 17};

        for (int i = 0; i < 7; i++) {
            String dayName;
            if (i == 0) {
                dayName = "오늘";
            } else if (i == 1) {
                dayName = "내일";
            } else {
                dayName = dayFormat.format(calendar.getTime());
            }

            WeatherForecastItem item = new WeatherForecastItem(
                    dayName,
                    conditions[i],
                    highs[i],
                    lows[i],
                    android.R.drawable.ic_menu_day // 실제로는 날씨에 따라 다른 아이콘
            );

            forecastList.add(item);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        forecastAdapter.notifyDataSetChanged();
    }

    /**
     * 권한 요청 결과를 처리하는 메서드
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 권한이 허용된 경우 날씨 데이터 로드
                getCurrentLocationAndWeather();
            } else {
                // 권한이 거부된 경우 기본 위치로 날씨 표시
                Toast.makeText(getContext(), "위치 권한이 필요합니다. 기본 위치의 날씨를 표시합니다.",
                        Toast.LENGTH_LONG).show();
                displayDummyWeatherData("기본 위치");
            }
        }
    }

    /**
     * 날씨 예보 아이템 데이터 클래스
     */
    public static class WeatherForecastItem {
        private String day;
        private String condition;
        private int highTemp;
        private int lowTemp;
        private int iconResourceId;

        public WeatherForecastItem(String day, String condition, int highTemp, int lowTemp, int iconResourceId) {
            this.day = day;
            this.condition = condition;
            this.highTemp = highTemp;
            this.lowTemp = lowTemp;
            this.iconResourceId = iconResourceId;
        }

        // Getter 메서드들
        public String getDay() { return day; }
        public String getCondition() { return condition; }
        public int getHighTemp() { return highTemp; }
        public int getLowTemp() { return lowTemp; }
        public int getIconResourceId() { return iconResourceId; }
    }

    /**
     * 날씨 예보 어댑터
     */
    private class WeatherForecastAdapter extends RecyclerView.Adapter<WeatherForecastAdapter.ForecastViewHolder> {

        private List<WeatherForecastItem> forecasts;

        public WeatherForecastAdapter(List<WeatherForecastItem> forecasts) {
            this.forecasts = forecasts;
        }

        @NonNull
        @Override
        public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_weather_forecast, parent, false);
            return new ForecastViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
            WeatherForecastItem forecast = forecasts.get(position);

            holder.textDay.setText(forecast.getDay());
            holder.textCondition.setText(forecast.getCondition());
            holder.textHighTemp.setText(forecast.getHighTemp() + "°");
            holder.textLowTemp.setText(forecast.getLowTemp() + "°");
            holder.imageIcon.setImageResource(forecast.getIconResourceId());
        }

        @Override
        public int getItemCount() {
            return forecasts.size();
        }

        class ForecastViewHolder extends RecyclerView.ViewHolder {
            TextView textDay;
            TextView textCondition;
            TextView textHighTemp;
            TextView textLowTemp;
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