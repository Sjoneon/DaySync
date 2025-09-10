package com.sjoneon.cap;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
 * 네비게이션 드로어와 채팅 인터페이스, 프로필 기능을 관리하며,
 * 지도 등 다양한 프래그먼트를 호스팅합니다.
 * 권한 관리 기능 추가됨.
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private ImageButton buttonVoice;
    private String userNickname;
    private ChatAdapter chatAdapter;

    // 네비게이션 헤더 요소들
    private ImageView profileImageView;
    private TextView textViewUsername;

    // 채팅 메시지 목록
    private List<Message> messageList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity 생성 시작");

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

        // 권한 체크 (약간의 지연 후 실행)
        checkPermissionsAfterDelay();

        Log.d(TAG, "MainActivity 생성 완료");
    }

    /**
     * 권한 체크 (지연 실행)
     */
    private void checkPermissionsAfterDelay() {
        // UI가 완전히 로드된 후 권한 체크 (3초 후)
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    Log.d(TAG, "권한 상태 체크 시작");
                    PermissionHelper.logPermissionStatus(MainActivity.this);

                    // 권한이 없는 경우에만 다이얼로그 표시
                    if (!PermissionHelper.hasNotificationPermission(MainActivity.this) ||
                            !PermissionHelper.hasExactAlarmPermission(MainActivity.this)) {
                        showPermissionInfoDialog();
                    }
                }
            }
        }, 3000); // 3초 후 실행
    }

    /**
     * 권한 안내 다이얼로그 표시
     */
    private void showPermissionInfoDialog() {
        new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("알림 기능 안내")
                .setMessage("DaySync의 일정 알림 기능을 완전히 사용하려면 알림 권한이 필요합니다.\n\n" +
                        "지금 설정하지 않아도 나중에 설정 메뉴에서 권한을 허용할 수 있습니다.")
                .setPositiveButton("지금 설정", (dialog, which) -> {
                    PermissionHelper.checkAndRequestAllPermissions(this);
                })
                .setNegativeButton("나중에", null)
                .setCancelable(true)
                .show();
    }

    /**
     * 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionHelper.REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "알림 권한이 허용되었습니다");
                Toast.makeText(this, "알림 권한이 허용되었습니다", Toast.LENGTH_SHORT).show();

                // 정확한 알람 권한도 체크
                if (!PermissionHelper.hasExactAlarmPermission(this)) {
                    PermissionHelper.requestExactAlarmPermission(this);
                }
            } else {
                Log.d(TAG, "알림 권한이 거부되었습니다");
                Toast.makeText(this, "알림 권한이 거부되었습니다. 설정에서 수동으로 허용할 수 있습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 액티비티 결과 처리
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PermissionHelper.REQUEST_EXACT_ALARM_PERMISSION) {
            if (PermissionHelper.hasExactAlarmPermission(this)) {
                Toast.makeText(this, "정확한 알람 권한이 허용되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "정확한 알람 권한이 필요합니다", Toast.LENGTH_LONG).show();
            }
        }
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

        // 네비게이션 헤더에서 프로필 요소들 찾기
        View headerView = navigationView.getHeaderView(0);
        profileImageView = headerView.findViewById(R.id.profileImageView);
        textViewUsername = headerView.findViewById(R.id.textViewUsername);

        // 네비게이션 헤더 초기 설정
        updateNavigationHeader();

        // 프로필 이미지 클릭 리스너 설정
        setupProfileImageClickListener();

        // 리사이클러 뷰 설정
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(messageList);
        recyclerView.setAdapter(chatAdapter);

        // 환영 메시지 추가
        addWelcomeMessage();
    }

    /**
     * 네비게이션 헤더를 업데이트하는 메서드
     */
    public void updateNavigationHeader() {
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userNickname = preferences.getString("nickname", "사용자");

        if (textViewUsername != null) {
            textViewUsername.setText(getString(R.string.welcome_user, userNickname));
        }
    }

    /**
     * 프로필 이미지 클릭 리스너를 설정하는 메서드
     */
    private void setupProfileImageClickListener() {
        if (profileImageView != null) {
            profileImageView.setOnClickListener(v -> {
                // 네비게이션 드로어 닫기
                drawerLayout.closeDrawer(GravityCompat.START);

                // 설정 Fragment로 이동
                showFragment(new SettingsFragment());
                toolbar.setTitle("개인설정");
            });
        }

        // 사용자 이름도 클릭 가능하게 설정
        if (textViewUsername != null) {
            textViewUsername.setOnClickListener(v -> {
                // 네비게이션 드로어 닫기
                drawerLayout.closeDrawer(GravityCompat.START);

                // 설정 Fragment로 이동
                showFragment(new SettingsFragment());
                toolbar.setTitle("개인설정");
            });
        }
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
        buttonSend.setOnClickListener(v -> {
            String messageContent = editTextMessage.getText().toString().trim();
            if (!messageContent.isEmpty()) {
                // 사용자 메시지 추가
                sendMessage(messageContent);
                editTextMessage.setText("");
            }
        });

        // 음성 입력 버튼 클릭 리스너
        buttonVoice.setOnClickListener(v -> {
            // 음성 입력 처리 (Speech-to-Text 구현 필요)
            Toast.makeText(MainActivity.this, getString(R.string.voice_input_not_ready), Toast.LENGTH_SHORT).show();
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
        recyclerView.postDelayed(() -> {
            Message aiMessage = new Message(aiResponse, false);
            chatAdapter.addMessage(aiMessage);
            scrollToBottom();
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
        } else if (id == R.id.nav_route_info) {
            // 경로 검색 화면으로 전환 (수정된 부분)
            showFragment(new RouteFragment());
            toolbar.setTitle("추천 경로 정보");
        } else if (id == R.id.nav_weather) {
            // 날씨 정보 화면으로 전환
            showFragment(new WeatherFragment());
            toolbar.setTitle("날씨 정보");
        } else if (id == R.id.nav_notifications) {
            // 알림 목록 화면으로 전환
            showFragment(new NotificationsFragment());
            toolbar.setTitle(R.string.menu_notifications);
        } else if (id == R.id.nav_settings) {
            // 설정 화면으로 전환
            showFragment(new SettingsFragment());
            toolbar.setTitle("설정");
        } else if (id == R.id.nav_help) {
            // 도움말 화면으로 전환 (Toast 대신 HelpFragment로 이동)
            showFragment(new HelpFragment());
            toolbar.setTitle("도움말");
        }

        // 네비게이션 드로어 닫기
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * 뒤로 가기 버튼 처리 (수정된 부분)
     */
    @Override
    public void onBackPressed() {
        // 뒤로 가기 버튼이 눌렸을 때 네비게이션 드로어가 열려있으면 닫기
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            // 현재 프래그먼트가 있으면 채팅 화면으로 돌아가기
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null) {
                // 백스택에 있는 Fragment가 있으면 이전으로 돌아가기
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();

                    // 백스택이 비어있으면 채팅 화면으로
                    if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                        showChatInterface();
                        toolbar.setTitle(R.string.app_name);
                    }
                } else {
                    showChatInterface();
                    toolbar.setTitle(R.string.app_name);
                }
            } else {
                super.onBackPressed();
            }
        }
    }

    /**
     * 프래그먼트를 화면에 표시하는 메서드
     * @param fragment 표시할 프래그먼트
     */
    public void showFragment(Fragment fragment) {
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
    protected void onResume() {
        super.onResume();
        // 화면이 다시 보여질 때 네비게이션 헤더 업데이트
        updateNavigationHeader();
    }
}