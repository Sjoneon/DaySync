package com.sjoneon.cap;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 도움말 화면을 관리하는 프래그먼트
 * 앱의 각 기능에 대한 상세한 설명을 제공합니다.
 */
public class HelpFragment extends Fragment {

    private RecyclerView recyclerView;
    private HelpAdapter helpAdapter;
    private List<HelpItem> helpItems;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_help, container, false);

        // RecyclerView 초기화
        recyclerView = view.findViewById(R.id.recyclerViewHelp);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 도움말 아이템 데이터 생성
        createHelpItems();

        // 어댑터 설정
        helpAdapter = new HelpAdapter(helpItems);
        recyclerView.setAdapter(helpAdapter);

        return view;
    }

    /**
     * 도움말 아이템들을 생성하는 메서드
     */
    private void createHelpItems() {
        helpItems = new ArrayList<>();

        // 채팅 기능
        helpItems.add(new HelpItem(
                "채팅",
                "AI 비서와 대화하여 일정 관리, 경로 검색 등의 도움을 받을 수 있습니다.",
                "• 자연스러운 대화로 요청사항 전달\n" +
                        "• 음성 입력 지원 (준비 중)\n" +
                        "• 개인화된 추천 서비스\n" +
                        "• 실시간 응답"
        ));

        // 일정 관리
        helpItems.add(new HelpItem(
                "일정 관리",
                "캘린더를 통해 일정을 추가, 수정, 삭제할 수 있습니다.",
                "• 월별/주별 캘린더 보기\n" +
                        "• 일정 추가 및 편집\n" +
                        "• 일정 알림 설정\n" +
                        "• 반복 일정 지원\n" +
                        "• 일정 카테고리 분류"
        ));

        // 알람 설정
        helpItems.add(new HelpItem(
                "알람 설정",
                "원하는 시간에 알람을 설정하여 중요한 일정을 놓치지 않을 수 있습니다.",
                "• 다중 알람 설정\n" +
                        "• 커스텀 알람 레이블\n" +
                        "• 반복 알람 (일, 주, 월)\n" +
                        "• 스누즈 기능\n" +
                        "• 알람 음량 조절"
        ));

        // 추천 경로 정보
        helpItems.add(new HelpItem(
                "추천 경로 정보",
                "출발지와 도착지를 입력하면 최적의 대중교통 경로를 추천해드립니다.",
                "• 버스 노선 기반 경로 검색\n" +
                        "• 실시간 교통 정보 반영\n" +
                        "• 다양한 경로 옵션 제공\n" +
                        "• 예상 소요 시간 및 요금 안내\n" +
                        "• 지도에서 경로 확인"
        ));

        // 날씨 정보
        helpItems.add(new HelpItem(
                "날씨 정보",
                "현재 위치 기반으로 실시간 날씨 정보와 일기예보를 제공합니다.",
                "• 현재 날씨 상태\n" +
                        "• 시간별/일별 날씨 예보\n" +
                        "• 기온, 습도, 바람 정보\n" +
                        "• 날씨 알림 설정\n" +
                        "• 지역별 날씨 검색"
        ));

        // 알림 목록
        helpItems.add(new HelpItem(
                "알림 목록",
                "앱에서 받은 모든 알림을 한 곳에서 확인하고 관리할 수 있습니다.",
                "• 수신된 알림 이력 확인\n" +
                        "• 읽음/안읽음 상태 관리\n" +
                        "• 알림 삭제 기능\n" +
                        "• 알림 카테고리별 분류\n" +
                        "• 일괄 관리 기능"
        ));

        // 설정
        helpItems.add(new HelpItem(
                "설정",
                "앱의 다양한 설정을 개인화하여 더 편리하게 사용할 수 있습니다.",
                "• 사용자 이름 변경\n" +
                        "• 위치 권한 관리\n" +
                        "• 푸시 알림 설정\n" +
                        "• 앱 정보 확인\n" +
                        "• 개인정보 관리"
        ));

        // 권한 안내
        helpItems.add(new HelpItem(
                "권한 안내",
                "앱이 원활히 작동하기 위해 필요한 권한들에 대한 설명입니다.",
                "• 위치 권한: 현재 위치 기반 서비스 제공\n" +
                        "• 알림 권한: 일정 및 알람 알림 발송\n" +
                        "• 인터넷 권한: 실시간 정보 업데이트\n" +
                        "• 정확한 알람 권한: 정시 알람 제공\n" +
                        "• 진동 권한: 알람 진동 기능"
        ));

        // 사용 팁
        helpItems.add(new HelpItem(
                "사용 팁",
                "DaySync를 더 효과적으로 사용하기 위한 유용한 팁들입니다.",
                "• 채팅에서 '내일 오전 10시 회의 알림 설정해줘'와 같이 자연스럽게 요청하세요\n" +
                        "• 자주 가는 장소는 즐겨찾기로 등록하면 편리합니다\n" +
                        "• 알림 권한을 허용해야 모든 기능을 사용할 수 있습니다\n" +
                        "• 정기적으로 일정을 확인하여 놓치는 일이 없도록 하세요\n" +
                        "• 날씨 정보를 참고하여 외출 계획을 세우세요"
        ));

        // 문제 해결
        helpItems.add(new HelpItem(
                "문제 해결",
                "앱 사용 중 발생할 수 있는 문제들과 해결 방법입니다.",
                "• 알림이 오지 않는 경우: 설정에서 알림 권한을 확인하세요\n" +
                        "• 위치를 찾을 수 없는 경우: GPS를 켜고 위치 권한을 허용하세요\n" +
                        "• 앱이 느린 경우: 네트워크 연결 상태를 확인하세요\n" +
                        "• 일정이 저장되지 않는 경우: 앱을 재시작해 보세요\n" +
                        "• 기타 문제: 앱을 최신 버전으로 업데이트하세요"
        ));
    }

    /**
     * 도움말 아이템 데이터 클래스
     */
    public static class HelpItem {
        private String title;
        private String summary;
        private String details;
        private boolean isExpanded;

        public HelpItem(String title, String summary, String details) {
            this.title = title;
            this.summary = summary;
            this.details = details;
            this.isExpanded = false;
        }

        // Getter 메서드들
        public String getTitle() { return title; }
        public String getSummary() { return summary; }
        public String getDetails() { return details; }
        public boolean isExpanded() { return isExpanded; }
        public void setExpanded(boolean expanded) { isExpanded = expanded; }
    }
}