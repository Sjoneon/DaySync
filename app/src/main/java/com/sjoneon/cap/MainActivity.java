package com.sjoneon.cap;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

/**
 * 앱의 메인 액티비티
 * 네비게이션 드로어와 채팅 인터페이스를 관리합니다.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private ImageButton buttonVoice;
    private String userNickname;
    private ChatAdapter chatAdapter;

    // 채팅 메시지 목록
    private List<Message> messageList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 사용자 닉네임 가져오기
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userNickname = preferences.getString("nickname", getString(R.string.app_name));

        // 뷰 초기화
        initializeViews();

        // 툴바 설정
        setSupportActionBar(toolbar);

        // 액션바에 홈 버튼 표시 (네비게이션 드로어 열기용)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // 네비게이션 드로어 설정
        setupNavigationDrawer();

        // 채팅 입력 및 버튼 리스너 설정
        setupChatInterface();
    }

    /**
     * 뷰 초기화 메서드
     */
    private void initializeViews() {
        // 툴바 및 드로어 요소 찾기
        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // 채팅 인터페이스 요소 찾기
        recyclerView = findViewById(R.id.recyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        buttonVoice = findViewById(R.id.buttonVoice);

        // 네비게이션 헤더에서 사용자 이름 설정
        View headerView = navigationView.getHeaderView(0);
        TextView textViewUsername = headerView.findViewById(R.id.textViewUsername);
        textViewUsername.setText(getString(R.string.welcome_user, userNickname));

        // 리사이클러 뷰 설정
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(messageList);
        recyclerView.setAdapter(chatAdapter);

        // 환영 메시지 추가
        addWelcomeMessage();
    }

    /**
     * 네비게이션 드로어 설정 메서드
     */
    private void setupNavigationDrawer() {
        // 네비게이션 드로어 토글 설정
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        // 토글 아이콘(햄버거 메뉴) 색상 설정
        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.text_primary));

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // 네비게이션 뷰에 리스너 설정
        navigationView.setNavigationItemSelectedListener(this);

        // 전체 메뉴 아이템 및 카테고리 텍스트 스타일 적용
        NavigationCategoryHelper.styleNavigationMenu(this, navigationView);

        // 메뉴 아이콘 색상 설정
        navigationView.setItemIconTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_primary)));

        // 아이템 텍스트 색상 설정
        navigationView.setItemTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_primary)));
    }

    /**
     * 채팅 인터페이스 설정 메서드
     */
    private void setupChatInterface() {
        // 메시지 전송 버튼 클릭 리스너
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageContent = editTextMessage.getText().toString().trim();
                if (!messageContent.isEmpty()) {
                    // 사용자 메시지 추가
                    sendMessage(messageContent);
                    editTextMessage.setText("");
                }
            }
        });

        // 음성 입력 버튼 클릭 리스너
        buttonVoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 음성 입력 처리 (Speech-to-Text 구현 필요)
                Toast.makeText(MainActivity.this, getString(R.string.voice_input_not_ready), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 환영 메시지 추가 메서드
     */
    private void addWelcomeMessage() {
        String welcomeMessage = getString(R.string.welcome_user, userNickname) + "\n무엇을 도와드릴까요?";
        Message aiMessage = new Message(welcomeMessage, false);
        messageList.add(aiMessage);
        chatAdapter.notifyDataSetChanged();
        scrollToBottom();
    }

    /**
     * 사용자 메시지 전송 및 AI 응답 처리 메서드
     * @param content 메시지 내용
     */
    private void sendMessage(String content) {
        // 사용자 메시지 추가
        Message userMessage = new Message(content, true);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();

        // AI 응답 처리 (실제 구현에서는 비동기 처리 필요)
        processAiResponse(content);
    }

    /**
     * AI 응답 처리 메서드 (임시 구현)
     * @param userMessage 사용자 메시지
     */
    private void processAiResponse(String userMessage) {
        // 임시 응답 생성
        String aiResponse = getString(R.string.ai_response_not_ready, userMessage);

        // 실제 구현에서는 여기에 AI 처리 로직 추가

        // 약간의 지연 후 AI 응답 표시 (실제 대화처럼 보이게)
        recyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                Message aiMessage = new Message(aiResponse, false);
                chatAdapter.addMessage(aiMessage);
                scrollToBottom();
            }
        }, 1000); // 1초 지연
    }

    /**
     * 채팅 스크롤을 최신 메시지로 이동하는 메서드
     */
    private void scrollToBottom() {
        if (messageList.size() > 0) {
            recyclerView.smoothScrollToPosition(messageList.size() - 1);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // 네비게이션 메뉴 아이템 선택 처리
        int id = item.getItemId();

        if (id == R.id.nav_chat) {
            // 채팅 화면 (기본 화면)
            showChatInterface();
            toolbar.setTitle(R.string.menu_chat);
        } else if (id == R.id.nav_calendar) {
            // 일정 관리 화면으로 전환
            showFragment(new CalendarFragment());
            toolbar.setTitle(R.string.menu_calendar);
        } else if (id == R.id.nav_alarm) {
            // 알람 설정 화면으로 전환
            showFragment(new AlarmFragment());
            toolbar.setTitle(R.string.menu_alarm);
        } else if (id == R.id.nav_route) {
            // 경로 추천 화면으로 전환
            showFragment(new RouteFragment());
            toolbar.setTitle(R.string.menu_route);
        } else if (id == R.id.nav_notifications) {
            // 알림 목록 화면으로 전환
            showFragment(new NotificationsFragment());
            toolbar.setTitle(R.string.menu_notifications);
        } else if (id == R.id.nav_settings) {
            // 설정 화면으로 전환
            Toast.makeText(this, getString(R.string.feature_not_ready, getString(R.string.menu_settings)), Toast.LENGTH_SHORT).show();
            // 추후 SettingsFragment 구현 예정
        } else if (id == R.id.nav_help) {
            // 도움말 화면으로 전환
            Toast.makeText(this, getString(R.string.feature_not_ready, getString(R.string.menu_help)), Toast.LENGTH_SHORT).show();
            // 추후 HelpFragment 구현 예정
        }

        // 네비게이션 드로어 닫기
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * 프래그먼트를 화면에 표시하는 메서드
     * @param fragment 표시할 프래그먼트
     */
    private void showFragment(Fragment fragment) {
        View chatContainer = findViewById(R.id.chat_container);
        chatContainer.setVisibility(View.GONE);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * 채팅 인터페이스를 다시 표시하는 메서드
     */
    private void showChatInterface() {
        View chatContainer = findViewById(R.id.chat_container);
        chatContainer.setVisibility(View.VISIBLE);

        // 현재 표시 중인 프래그먼트 제거
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(currentFragment)
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        // 뒤로 가기 버튼이 눌렸을 때 네비게이션 드로어가 열려있으면 닫기
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}