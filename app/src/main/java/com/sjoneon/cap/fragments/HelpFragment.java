package com.sjoneon.cap.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sjoneon.cap.adapters.HelpAdapter;
import com.sjoneon.cap.R;

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
                "Gemini 2.5 Flash AI 비서와 대화하여 일정 관리, 경로 검색 등의 도움을 받을 수 있습니다.",
                "• 자연스러운 대화로 요청사항 전달\n" +
                        "• 음성 입력 지원 (마이크 버튼 클릭)\n" +
                        "• Android STT 기반 음성 인식\n" +
                        "• 실시간 AI 응답"
        ));

        // 일정 관리
        helpItems.add(new HelpItem(
                "일정 관리",
                "캘린더를 통해 일정을 추가, 수정, 삭제할 수 있습니다.",
                "• 월별/주별 캘린더 보기\n" +
                        "• 일정 추가 및 편집\n" +
                        "• 일정 알림 설정\n" +
                        "• 반복 일정 지원"
        ));

        // 알람 설정
        helpItems.add(new HelpItem(
                "알람 설정",
                "원하는 시간에 알람을 설정하여 중요한 일정을 놓치지 않을 수 있습니다.",
                "• 다중 알람 설정\n" +
                        "• 커스텀 알람 레이블"
        ));

        // 추천 경로 정보
        helpItems.add(new HelpItem(
                "추천 경로 정보",
                "출발지와 도착지를 입력하면 최적의 대중교통 경로를 추천해드립니다.",
                "• TMAP API 기반 경로 검색\n" +
                        "• 국토교통부 실시간 교통 정보 연동\n" +
                        "• 버스 경로 옵션 제공\n" +
                        "• 예상 소요 시간 안내\n" +
                        "• 네이버 지도에서 경로 확인"
        ));

        // 날씨 정보
        helpItems.add(new HelpItem(
                "날씨 정보",
                "현재 위치 기반으로 실시간 날씨 정보와 일기예보를 제공합니다.",
                "• 기상청 API 기반 정확한 날씨 정보\n" +
                        "• 현재 날씨 상태\n" +
                        "• 시간별/일별 날씨 예보\n" +
                        "• 기온, 습도, 바람 정보\n" +
                        "• 오늘, 내일, 모레 날씨 제공"
        ));

        // 알림 목록
        helpItems.add(new HelpItem(
                "알림 목록",
                "앱에서 받은 모든 알림을 한 곳에서 확인하고 관리할 수 있습니다.",
                "• 수신된 알림 이력 확인\n" +
                        "• 읽음/안읽음 상태 관리\n" +
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
                        "• UUID 기반 개인정보 관리"
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