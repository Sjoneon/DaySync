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
import android.widget.Button;
import android.widget.EditText;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 경로 검색 및 추천 기능을 제공하는 프래그먼트
 */
public class RouteFragment extends Fragment {

    private static final String TAG = "RouteFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI 요소들
    private EditText editStartLocation;
    private EditText editEndLocation;
    private Button buttonSearchRoute;
    private Button buttonMapView;
    private TextView textNoRoutes;
    private RecyclerView recyclerViewRoutes;

    // 데이터 및 서비스
    private FusedLocationProviderClient fusedLocationClient;
    private List<RouteInfo> routeList = new ArrayList<>();
    private RouteAdapter routeAdapter;
    private Geocoder geocoder;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        // 뷰 초기화
        initializeViews(view);

        // 서비스 초기화
        initializeServices();

        // 리사이클러뷰 설정
        setupRecyclerView();

        // 클릭 리스너 설정
        setupClickListeners();

        // 현재 위치 자동 입력
        loadCurrentLocation();

        return view;
    }

    /**
     * 뷰 요소들을 초기화하는 메서드
     */
    private void initializeViews(View view) {
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editEndLocation = view.findViewById(R.id.editEndLocation);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);
        buttonMapView = view.findViewById(R.id.buttonMapView);
        textNoRoutes = view.findViewById(R.id.textNoRoutes);
        recyclerViewRoutes = view.findViewById(R.id.recyclerViewRoutes);
    }

    /**
     * 서비스들을 초기화하는 메서드
     */
    private void initializeServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(requireContext(), Locale.getDefault());
    }

    /**
     * 리사이클러뷰를 설정하는 메서드
     */
    private void setupRecyclerView() {
        recyclerViewRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
        routeAdapter = new RouteAdapter(routeList);
        recyclerViewRoutes.setAdapter(routeAdapter);
    }

    /**
     * 클릭 리스너들을 설정하는 메서드
     */
    private void setupClickListeners() {
        // 경로 검색 버튼
        buttonSearchRoute.setOnClickListener(v -> searchRoutes());

        // 지도 보기 버튼
        buttonMapView.setOnClickListener(v -> openMapView());
    }

    /**
     * 현재 위치를 가져와서 출발지에 자동 입력하는 메서드
     */
    private void loadCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        getAddressFromLocation(location.getLatitude(), location.getLongitude());
                    } else {
                        Log.w(TAG, "현재 위치를 가져올 수 없습니다");
                        editStartLocation.setHint("현재 위치");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "위치 정보 가져오기 실패", e);
                    editStartLocation.setHint("현재 위치");
                });
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
     * 좌표를 주소로 변환하는 메서드
     */
    private void getAddressFromLocation(double latitude, double longitude) {
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = getShortAddress(address);
                editStartLocation.setText(addressText);
                editStartLocation.setHint("현재 위치");
            } else {
                editStartLocation.setHint("현재 위치");
            }
        } catch (IOException e) {
            Log.e(TAG, "주소 변환 실패", e);
            editStartLocation.setHint("현재 위치");
        }
    }

    /**
     * 주소를 간단한 형태로 변환하는 메서드
     */
    private String getShortAddress(Address address) {
        StringBuilder sb = new StringBuilder();

        if (address.getThoroughfare() != null) { // 도로명/번지
            sb.append(address.getThoroughfare());
        } else if (address.getSubLocality() != null) { // 동/읍/면
            sb.append(address.getSubLocality());
        }

        if (address.getLocality() != null) { // 시/군/구
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getLocality());
        }

        return sb.length() > 0 ? sb.toString() : "현재 위치";
    }

    /**
     * 경로를 검색하는 메서드
     */
    private void searchRoutes() {
        String startLocation = editStartLocation.getText().toString().trim();
        String endLocation = editEndLocation.getText().toString().trim();

        if (startLocation.isEmpty() || endLocation.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.empty_location_error),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 실제로는 여기서 공공 API나 네이버 경로 API를 호출해야 함
        // 현재는 더미 데이터로 구현
        generateDummyRoutes(startLocation, endLocation);
    }

    /**
     * 더미 경로 데이터를 생성하는 메서드 (실제로는 API 호출)
     */
    private void generateDummyRoutes(String start, String end) {
        routeList.clear();

        // 더미 경로 데이터 생성
        routeList.add(new RouteInfo(
                "버스 + 지하철",
                "총 이동 시간: 45분",
                "08:15 출발",
                "버스 102번 → 지하철 1호선",
                45,
                true
        ));

        routeList.add(new RouteInfo(
                "버스",
                "총 이동 시간: 52분",
                "08:20 출발",
                "버스 716번 직행",
                52,
                false
        ));

        routeList.add(new RouteInfo(
                "버스 + 도보",
                "총 이동 시간: 38분",
                "08:10 출발",
                "버스 502번 → 도보 5분",
                38,
                false
        ));

        routeAdapter.notifyDataSetChanged();
        updateRouteListVisibility();

        Toast.makeText(getContext(),
                String.format("'%s'에서 '%s'까지의 경로를 검색했습니다.", start, end),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * 경로 목록 표시 상태를 업데이트하는 메서드
     */
    private void updateRouteListVisibility() {
        if (routeList.isEmpty()) {
            textNoRoutes.setVisibility(View.VISIBLE);
            recyclerViewRoutes.setVisibility(View.GONE);
        } else {
            textNoRoutes.setVisibility(View.GONE);
            recyclerViewRoutes.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 지도 화면을 여는 메서드
     */
    private void openMapView() {
        // MapFragment로 전환
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.showMapWithRoute(
                    editStartLocation.getText().toString(),
                    editEndLocation.getText().toString()
            );
        }
    }

    /**
     * 권한 요청 결과를 처리하는 메서드
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCurrentLocation();
            } else {
                Toast.makeText(getContext(), getString(R.string.location_permission_denied),
                        Toast.LENGTH_LONG).show();
                editStartLocation.setHint("출발지를 입력하세요");
            }
        }
    }

    /**
     * 경로 정보를 담는 데이터 클래스
     */
    public static class RouteInfo {
        private String routeType;
        private String routeSummary;
        private String departureTime;
        private String routeDetail;
        private int duration;
        private boolean isRecommended;

        public RouteInfo(String routeType, String routeSummary, String departureTime,
                         String routeDetail, int duration, boolean isRecommended) {
            this.routeType = routeType;
            this.routeSummary = routeSummary;
            this.departureTime = departureTime;
            this.routeDetail = routeDetail;
            this.duration = duration;
            this.isRecommended = isRecommended;
        }

        // Getter 메서드들
        public String getRouteType() { return routeType; }
        public String getRouteSummary() { return routeSummary; }
        public String getDepartureTime() { return departureTime; }
        public String getRouteDetail() { return routeDetail; }
        public int getDuration() { return duration; }
        public boolean isRecommended() { return isRecommended; }
    }

    /**
     * 경로 정보를 표시하는 어댑터
     */
    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {
        private List<RouteInfo> routes;

        public RouteAdapter(List<RouteInfo> routes) {
            this.routes = routes;
        }

        @NonNull
        @Override
        public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_route, parent, false);
            return new RouteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
            RouteInfo route = routes.get(position);

            holder.textRouteType.setText(route.getRouteType());
            holder.textRouteSummary.setText(route.getRouteSummary());
            holder.textDepartureTime.setText(route.getDepartureTime());

            // 추천 경로 표시
            if (route.isRecommended()) {
                holder.textRouteType.setTextColor(getResources().getColor(R.color.notification_unread));
            } else {
                holder.textRouteType.setTextColor(getResources().getColor(R.color.text_primary));
            }

            // 상세 보기 버튼
            holder.buttonExpandRoute.setOnClickListener(v -> {
                Toast.makeText(getContext(),
                        "경로 상세: " + route.getRouteDetail(),
                        Toast.LENGTH_SHORT).show();
            });

            // 길 안내 시작 버튼
            holder.buttonStartNavigation.setOnClickListener(v -> {
                Toast.makeText(getContext(),
                        getString(R.string.navigation_not_ready),
                        Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return routes.size();
        }

        class RouteViewHolder extends RecyclerView.ViewHolder {
            TextView textRouteType;
            TextView textRouteSummary;
            TextView textDepartureTime;
            Button buttonExpandRoute;
            Button buttonStartNavigation;

            RouteViewHolder(View itemView) {
                super(itemView);
                textRouteType = itemView.findViewById(R.id.textRouteType);
                textRouteSummary = itemView.findViewById(R.id.textRouteSummary);
                textDepartureTime = itemView.findViewById(R.id.textDepartureTime);
                buttonExpandRoute = itemView.findViewById(R.id.buttonExpandRoute);
                buttonStartNavigation = itemView.findViewById(R.id.buttonStartNavigation);
            }
        }
    }
}