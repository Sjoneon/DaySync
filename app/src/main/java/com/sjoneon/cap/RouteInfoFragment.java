package com.sjoneon.cap;

import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.naver.maps.map.MapView;

import java.util.ArrayList;
import java.util.List;

/**
 * 경로 추천과 지도 기능을 통합한 프래그먼트
 */
public class RouteInfoFragment extends Fragment {

    // 탭 상태를 나타내는 열거형
    private enum TabState {
        ROUTE_SEARCH,  // 경로 검색
        MAP_VIEW       // 지도 보기
    }

    // UI 요소들
    private Button buttonRouteTab;
    private Button buttonMapTab;
    private LinearLayout layoutRouteSearch;
    private LinearLayout layoutMapView;

    // 경로 검색 관련 UI
    private EditText editStartLocation;
    private EditText editDestination;
    private Button buttonSearchRoute;
    private TextView textNoRoutes;
    private RecyclerView recyclerViewRoutes;
    private RouteAdapter routeAdapter;

    // 지도 관련 UI
    private MapView mapView;

    // 데이터
    private List<RouteItem> routeList = new ArrayList<>();
    private TabState currentTab = TabState.ROUTE_SEARCH;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route_info, container, false);

        // 뷰 초기화
        initializeViews(view);

        // 탭 설정
        setupTabs();

        // 경로 검색 설정
        setupRouteSearch();

        // 지도 설정
        setupMapView(savedInstanceState);

        // 초기 탭 설정
        switchToTab(TabState.ROUTE_SEARCH);

        return view;
    }

    /**
     * 뷰 요소들을 초기화하는 메서드
     */
    private void initializeViews(View view) {
        // 탭 버튼들
        buttonRouteTab = view.findViewById(R.id.buttonRouteTab);
        buttonMapTab = view.findViewById(R.id.buttonMapTab);

        // 레이아웃 컨테이너들
        layoutRouteSearch = view.findViewById(R.id.layoutRouteSearch);
        layoutMapView = view.findViewById(R.id.layoutMapView);

        // 경로 검색 관련 UI
        editStartLocation = view.findViewById(R.id.editStartLocation);
        editDestination = view.findViewById(R.id.editDestination);
        buttonSearchRoute = view.findViewById(R.id.buttonSearchRoute);
        textNoRoutes = view.findViewById(R.id.textNoRoutes);
        recyclerViewRoutes = view.findViewById(R.id.recyclerViewRoutes);

        // 지도 관련 UI
        mapView = view.findViewById(R.id.mapView);
    }

    /**
     * 탭 기능을 설정하는 메서드
     */
    private void setupTabs() {
        buttonRouteTab.setOnClickListener(v -> switchToTab(TabState.ROUTE_SEARCH));
        buttonMapTab.setOnClickListener(v -> switchToTab(TabState.MAP_VIEW));
    }

    /**
     * 경로 검색 기능을 설정하는 메서드
     */
    private void setupRouteSearch() {
        // 리사이클러뷰 설정
        recyclerViewRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
        routeAdapter = new RouteAdapter(routeList);
        recyclerViewRoutes.setAdapter(routeAdapter);

        // 검색 버튼 클릭 리스너
        buttonSearchRoute.setOnClickListener(v -> searchRoute());
    }

    /**
     * 지도 뷰를 설정하는 메서드
     */
    private void setupMapView(Bundle savedInstanceState) {
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            // 실제 지도 초기화는 MapFragment의 코드를 참조
        }
    }

    /**
     * 탭을 전환하는 메서드
     */
    private void switchToTab(TabState tabState) {
        currentTab = tabState;

        // 탭 버튼 상태 업데이트
        updateTabButtonStates();

        // 레이아웃 표시/숨김 처리
        switch (tabState) {
            case ROUTE_SEARCH:
                layoutRouteSearch.setVisibility(View.VISIBLE);
                layoutMapView.setVisibility(View.GONE);
                break;
            case MAP_VIEW:
                layoutRouteSearch.setVisibility(View.GONE);
                layoutMapView.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * 탭 버튼 상태를 업데이트하는 메서드
     */
    private void updateTabButtonStates() {
        // 선택된 탭과 선택되지 않은 탭의 스타일 변경
        if (currentTab == TabState.ROUTE_SEARCH) {
            buttonRouteTab.setBackgroundColor(getResources().getColor(R.color.background_button, null));
            buttonMapTab.setBackgroundColor(getResources().getColor(R.color.background_input, null));
        } else {
            buttonRouteTab.setBackgroundColor(getResources().getColor(R.color.background_input, null));
            buttonMapTab.setBackgroundColor(getResources().getColor(R.color.background_button, null));
        }
    }

    /**
     * 경로 검색을 수행하는 메서드
     */
    private void searchRoute() {
        String startLocation = editStartLocation.getText().toString().trim();
        String destination = editDestination.getText().toString().trim();

        if (startLocation.isEmpty() || destination.isEmpty()) {
            Toast.makeText(getContext(), R.string.empty_location_error, Toast.LENGTH_SHORT).show();
            return;
        }

        // 실제로는 여기서 API 호출을 통해 경로 정보를 가져와야 함
        // 임시로 더미 데이터 생성
        generateDummyRoutes(startLocation, destination);

        // 결과 표시
        updateRouteListVisibility();
    }

    /**
     * 임시 더미 경로 생성 (실제로는 API 호출 결과로 대체)
     */
    private void generateDummyRoutes(String startLocation, String destination) {
        routeList.clear();

        // 첫 번째 경로 옵션
        RouteItem route1 = new RouteItem(
                "버스 + 지하철",
                getString(R.string.departure_time_format, "08:15"),
                "08:15",
                new String[]{
                        "버스 102번 - 청주역 정류장 (8:15 출발)",
                        "지하철 1호선 - 시청역 방면 (8:30 출발)"
                }
        );

        // 두 번째 경로 옵션
        RouteItem route2 = new RouteItem(
                "버스",
                getString(R.string.departure_time_format, "08:20"),
                "08:20",
                new String[]{
                        "버스 202번 - 청주역 정류장 (8:20 출발)",
                        "도보 10분 - 시청역에서 목적지까지"
                }
        );

        // 세 번째 경로 옵션
        RouteItem route3 = new RouteItem(
                "도보 + 버스",
                getString(R.string.departure_time_format, "08:25"),
                "08:25",
                new String[]{
                        "도보 15분 - 가까운 정류장까지",
                        "버스 301번 - 시청역 정류장 (8:40 출발)"
                }
        );

        routeList.add(route1);
        routeList.add(route2);
        routeList.add(route3);

        routeAdapter.notifyDataSetChanged();
    }

    /**
     * 경로 목록 표시 상태 업데이트
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
     * 경로 데이터 클래스
     */
    public static class RouteItem {
        private String routeType;
        private String routeSummary;
        private String departureTime;
        private String[] routeSegments;
        private boolean isExpanded = false;

        public RouteItem(String routeType, String routeSummary, String departureTime, String[] routeSegments) {
            this.routeType = routeType;
            this.routeSummary = routeSummary;
            this.departureTime = departureTime;
            this.routeSegments = routeSegments;
        }

        public String getRouteType() {
            return routeType;
        }

        public String getRouteSummary() {
            return routeSummary;
        }

        public String getDepartureTime() {
            return departureTime;
        }

        public String[] getRouteSegments() {
            return routeSegments;
        }

        public boolean isExpanded() {
            return isExpanded;
        }

        public void setExpanded(boolean expanded) {
            isExpanded = expanded;
        }
    }

    /**
     * 경로 어댑터
     */
    private class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {

        private List<RouteItem> routes;

        public RouteAdapter(List<RouteItem> routes) {
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
            RouteItem route = routes.get(position);
            holder.textRouteType.setText(route.getRouteType());
            holder.textRouteSummary.setText(route.getRouteSummary());
            holder.textDepartureTime.setText(getString(R.string.departure_time_format, route.getDepartureTime()));

            // 경로 세부 정보 표시/숨기기
            holder.layoutRouteDetail.setVisibility(route.isExpanded() ? View.VISIBLE : View.GONE);

            // 경로 세부 정보 확장/축소 버튼
            holder.buttonExpandRoute.setText(route.isExpanded() ?
                    R.string.hide_details : R.string.show_details);
            holder.buttonExpandRoute.setOnClickListener(v -> {
                route.setExpanded(!route.isExpanded());
                notifyItemChanged(position);
            });

            // 지도에서 보기 버튼 (기존의 길 안내 시작 버튼을 변경)
            holder.buttonShowOnMap.setText("지도에서 보기");
            holder.buttonShowOnMap.setOnClickListener(v -> {
                // 지도 탭으로 전환
                switchToTab(TabState.MAP_VIEW);
                Toast.makeText(getContext(), "지도에서 경로를 확인하세요", Toast.LENGTH_SHORT).show();
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
            View layoutRouteDetail;
            Button buttonExpandRoute;
            Button buttonShowOnMap;

            RouteViewHolder(View itemView) {
                super(itemView);
                textRouteType = itemView.findViewById(R.id.textRouteType);
                textRouteSummary = itemView.findViewById(R.id.textRouteSummary);
                textDepartureTime = itemView.findViewById(R.id.textDepartureTime);
                layoutRouteDetail = itemView.findViewById(R.id.layoutRouteDetail);
                buttonExpandRoute = itemView.findViewById(R.id.buttonExpandRoute);
                buttonShowOnMap = itemView.findViewById(R.id.buttonStartNavigation); // 기존 버튼 재사용
            }
        }
    }

    // 생명주기 메서드들 - 지도뷰와 동기화
    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}
