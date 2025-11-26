package com.sjoneon.cap.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Menu;
import android.provider.Settings;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.sjoneon.cap.fragments.AlarmFragment;
import com.sjoneon.cap.fragments.CalendarFragment;
import com.sjoneon.cap.adapters.ChatAdapter;
import com.sjoneon.cap.fragments.HelpFragment;
import com.sjoneon.cap.fragments.NotificationsFragment;
import com.sjoneon.cap.R;
import com.sjoneon.cap.fragments.RouteFragment;
import com.sjoneon.cap.fragments.SettingsFragment;
import com.sjoneon.cap.fragments.WeatherFragment;
import com.sjoneon.cap.helpers.NavigationCategoryHelper;
import com.sjoneon.cap.helpers.PermissionHelper;
import com.sjoneon.cap.models.local.Message;
import com.sjoneon.cap.services.SpeechToTextService;
import com.sjoneon.cap.services.DaySyncApiService;
import com.sjoneon.cap.models.api.ChatRequest;
import com.sjoneon.cap.models.api.ChatResponse;
import com.sjoneon.cap.models.api.UserCreateRequest;
import com.sjoneon.cap.models.api.UserCreateResponse;
import com.sjoneon.cap.models.api.UserResponse;
import com.sjoneon.cap.models.api.SessionListResponse;
import com.sjoneon.cap.models.api.MessageListResponse;
import com.sjoneon.cap.models.api.SessionInfo;
import com.sjoneon.cap.models.api.MessageInfo;
import com.sjoneon.cap.utils.ApiClient;
import com.sjoneon.cap.fragments.SessionListBottomSheet;
import com.sjoneon.cap.BuildConfig;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.sjoneon.cap.models.api.WeatherResponse;
import com.sjoneon.cap.services.WeatherApiService;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Collections;

/**
 * 앱의 메인 액티비티
 * 네비게이션 드로어와 채팅 인터페이스, 프로필 기능을 관리
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 101;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private ImageButton buttonVoice;
    private String userNickname;
    private ChatAdapter chatAdapter;

    private ImageView profileImageView;
    private TextView textViewUsername;

    private List<Message> messageList = new ArrayList<>();

    private SpeechToTextService speechToTextService;
    private DaySyncApiService apiService;
    private String userUuid;
    private Integer sessionId;
    private WeatherApiService weatherApiService;
    private Gson lenientGson;
    private JsonObject cachedWeatherData = null;
    private int currentNx = 69;  // 청주시 기본 좌표
    private int currentNy = 107;
    private View inputLayout;
    private View chatContainer;

    private Handler autoListenHandler;
    private Runnable autoListenRunnable;
    private Runnable autoListenTimeoutRunnable;
    private static final long AUTO_LISTEN_DELAY = 1000;
    private boolean lastInputWasVoice = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity 생성 시작");

        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userNickname = preferences.getString("nickname", getString(R.string.app_name));

        initializeViews();

        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        setupNavigationDrawer();
        setupChatInterface();

        initializeAiServices();
        initializeWeatherService();
        loadUserUuid();
        checkAndSyncUserWithServer();
        checkRecordAudioPermission();

        setupKeyboardHandling();
        checkPermissionsAfterDelay();

        loadChatHistory();

        autoListenHandler = new Handler();

        Log.d(TAG, "MainActivity 생성 완료");
    }

    /**
     * 권한 체크 (지연 실행)
     */
    private void checkPermissionsAfterDelay() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    Log.d(TAG, "권한 상태 체크 시작");
                    PermissionHelper.logPermissionStatus(MainActivity.this);

                    // Overlay 권한부터 체크 (내부에서 알림 권한도 순차 체크)
                    checkOverlayPermission();
                }
            }
        }, 2000);
    }

    /**
     * 다른 앱 위에 표시 권한 체크 및 요청
     */
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new AlertDialog.Builder(this, R.style.DialogTheme)
                        .setTitle("알람 화면 표시 권한")
                        .setMessage("알람이 울릴 때 화면을 표시하려면 '다른 앱 위에 표시' 권한이 필요합니다.")
                        .setPositiveButton("설정으로 이동", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, 1234);
                        })
                        .setNegativeButton("나중에", (dialog, which) -> {
                            // 나중에 눌러도 알림 권한 체크
                            checkNotificationPermission();
                        })
                        .setCancelable(false)
                        .show();
            } else {
                // Overlay 권한 있으면 알림 권한 체크
                checkNotificationPermission();
            }
        } else {
            checkNotificationPermission();
        }
    }

    /**
     * 알림 권한 체크
     */
    private void checkNotificationPermission() {
        if (!PermissionHelper.hasNotificationPermission(this) ||
                !PermissionHelper.hasExactAlarmPermission(this)) {
            showPermissionInfoDialog();
        }
    }

    /**
     * 권한 안내 다이얼로그
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

                if (!PermissionHelper.hasExactAlarmPermission(this)) {
                    PermissionHelper.requestExactAlarmPermission(this);
                }
            } else {
                Log.d(TAG, "알림 권한이 거부되었습니다");
                Toast.makeText(this, "알림 권한이 거부되었습니다. 설정에서 수동으로 허용할 수 있습니다.", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "음성 인식 권한 허용됨");
                Toast.makeText(this, "음성 인식을 사용할 수 있습니다", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "음성 인식 권한 거부됨");
                Toast.makeText(this, "음성 인식 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 액티비티 결과 처리
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Overlay 권한 설정 후 알림 권한 체크
        if (requestCode == 1234) {
            // 약간 지연 후 알림 권한 체크
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                checkNotificationPermission();
            }, 500);
        }

        if (requestCode == PermissionHelper.REQUEST_EXACT_ALARM_PERMISSION) {
            if (PermissionHelper.hasExactAlarmPermission(this)) {
                Toast.makeText(this, "정확한 알람 권한이 허용되었습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "정확한 알람 권한이 필요합니다", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 뷰 초기화
     */
    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        recyclerView = findViewById(R.id.recyclerView);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        buttonVoice = findViewById(R.id.buttonVoice);

        inputLayout = findViewById(R.id.inputLayout);
        chatContainer = findViewById(R.id.chat_container);

        View headerView = navigationView.getHeaderView(0);
        profileImageView = headerView.findViewById(R.id.profileImageView);
        textViewUsername = headerView.findViewById(R.id.textViewUsername);

        updateNavigationHeader();

        setupProfileImageClickListener();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(messageList);
        recyclerView.setAdapter(chatAdapter);

        addWelcomeMessage();
    }

    /**
     * 네비게이션 헤더 업데이트
     */
    public void updateNavigationHeader() {
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userNickname = preferences.getString("nickname", "사용자");

        if (textViewUsername != null) {
            textViewUsername.setText(getString(R.string.welcome_user, userNickname));
        }
    }

    /**
     * 키보드 처리 설정
     */
    private void setupKeyboardHandling() {
        if (chatContainer == null || inputLayout == null) {
            Log.e(TAG, "chatContainer 또는 inputLayout이 null입니다");
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            chatContainer.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets imeInsets = insets.getInsets(android.view.WindowInsets.Type.ime());
                int imeHeight = imeInsets.bottom;

                if (imeHeight > 0) {
                    inputLayout.setTranslationY(-imeHeight);

                    recyclerView.setPadding(
                            recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            imeHeight + inputLayout.getHeight()
                    );

                    scrollToBottom();
                } else {
                    inputLayout.setTranslationY(0);
                    recyclerView.setPadding(
                            recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            getResources().getDimensionPixelSize(R.dimen.padding_xlarge)
                    );
                }

                return insets;
            });
        } else {
            chatContainer.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                android.graphics.Rect r = new android.graphics.Rect();
                chatContainer.getWindowVisibleDisplayFrame(r);
                int screenHeight = chatContainer.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                if (keypadHeight > screenHeight * 0.15) {
                    inputLayout.setTranslationY(-keypadHeight);

                    recyclerView.setPadding(
                            recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            keypadHeight + inputLayout.getHeight()
                    );

                    scrollToBottom();
                } else {
                    inputLayout.setTranslationY(0);
                    recyclerView.setPadding(
                            recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            getResources().getDimensionPixelSize(R.dimen.padding_xlarge)
                    );
                }
            });
        }
    }

    /**
     * 프로필 이미지 클릭 리스너
     */
    private void setupProfileImageClickListener() {
        if (profileImageView != null) {
            profileImageView.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showFragment(new SettingsFragment());
                toolbar.setTitle("개인설정");
            });
        }

        if (textViewUsername != null) {
            textViewUsername.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showFragment(new SettingsFragment());
                toolbar.setTitle("개인설정");
            });
        }
    }

    /**
     * 네비게이션 드로어 설정
     */
    private void setupNavigationDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);

        toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, R.color.text_primary));

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        NavigationCategoryHelper.styleNavigationMenu(this, navigationView);

        navigationView.setItemIconTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_primary)));

        navigationView.setItemTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_primary)));
    }

    /**
     * 채팅 인터페이스 설정
     */
    private void setupChatInterface() {
        buttonSend.setOnClickListener(v -> {
            String messageContent = editTextMessage.getText().toString().trim();
            if (!messageContent.isEmpty()) {
                // 텍스트로 입력했으므로 플래그를 false로 설정
                lastInputWasVoice = false;

                sendMessage(messageContent);
                editTextMessage.setText("");
            }
        });

        // 마이크 버튼 - 단순 클릭으로 STT 실행
        buttonVoice.setOnClickListener(v -> {
            toggleSpeechRecognition();
        });
    }

    /**
     * AI 서비스 초기화
     */
    private void initializeAiServices() {
        speechToTextService = new SpeechToTextService(this);
        speechToTextService.setListener(new SpeechToTextService.SpeechRecognitionListener() {
            @Override
            public void onSpeechResult(String text) {
                runOnUiThread(() -> {
                    //editTextMessage.setText(text); //이거 추가하면 텍스트 필드에 텍스트 표시 (단, 삭제 안되고 유지 되어 있음.)
                    buttonVoice.setImageResource(R.drawable.ic_mic);

                    // STT 결과를 자동으로 전송
                    if (!text.trim().isEmpty()) {
                        // 음성으로 입력했으므로 플래그를 true로 설정
                        lastInputWasVoice = true;

                        sendMessage(text);
                    }
                });
            }

            @Override
            public void onSpeechError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                    buttonVoice.setImageResource(R.drawable.ic_mic);
                });
            }

            @Override
            public void onSpeechStarted() {
                runOnUiThread(() -> {
                    buttonVoice.setImageResource(R.drawable.ic_mic_active);
                    cancelAutoListenTimeout();
                });
            }

            @Override
            public void onSpeechEnded() {
                runOnUiThread(() -> {
                    buttonVoice.setImageResource(R.drawable.ic_mic);
                });
            }
        });

        apiService = ApiClient.getInstance().getApiService();
        Log.d(TAG, "AI 서비스 초기화 완료");
    }

    /**
     * 사용자 UUID 로드
     */
    private void loadUserUuid() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userUuid = prefs.getString("user_uuid", null);

        int savedSessionId = prefs.getInt("session_id", -1);
        sessionId = (savedSessionId == -1) ? null : savedSessionId;

        Log.d(TAG, "사용자 UUID: " + userUuid);
        Log.d(TAG, "세션 ID: " + sessionId);
    }

    /**
     * 사용자 정보 저장
     */
    private void saveUserInfo() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (userUuid != null) {
            editor.putString("user_uuid", userUuid);
        }

        if (sessionId != null) {
            editor.putInt("session_id", sessionId);
        } else {
            editor.putInt("session_id", -1);
        }

        editor.apply();
        Log.d(TAG, "사용자 정보 저장 완료");
    }

    /**
     * 음성 인식 권한 체크
     */
    private void checkRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    /**
     * 음성 인식 토글
     */
    private void toggleSpeechRecognition() {
        // 사용자가 수동으로 마이크 누르면 자동 인식 취소
        cancelAutoListening();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "음성 인식 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            checkRecordAudioPermission();
            return;
        }

        if (speechToTextService != null) {
            if (speechToTextService.isListening()) {
                speechToTextService.stopListening();
                Log.d(TAG, "음성 인식 중지");
            } else {
                speechToTextService.startListening();
                Log.d(TAG, "음성 인식 시작");
            }
        }
    }

    /**
     * 최근 대화 내역 불러오기
     */
    private void loadChatHistory() {
        if (userUuid == null || userUuid.isEmpty()) {
            Log.w(TAG, "UUID가 없어 대화 내역을 불러올 수 없습니다");
            return;
        }

        if (apiService == null) {
            Log.e(TAG, "API 서비스가 초기화되지 않았습니다");
            return;
        }

        Log.d(TAG, "대화 내역 불러오기 시작: " + userUuid);

        apiService.getUserSessions(userUuid).enqueue(new Callback<SessionListResponse>() {
            @Override
            public void onResponse(Call<SessionListResponse> call, Response<SessionListResponse> response) {
                if (response == null || !response.isSuccessful()) {
                    Log.w(TAG, "세션 목록 조회 실패: " + (response != null ? response.code() : "null response"));
                    return;
                }

                SessionListResponse sessionListResponse = response.body();
                if (sessionListResponse == null || !sessionListResponse.isSuccess()) {
                    Log.w(TAG, "세션 응답이 null이거나 실패");
                    return;
                }

                List<SessionInfo> sessions = sessionListResponse.getSessions();
                if (sessions != null && !sessions.isEmpty()) {
                    SessionInfo latestSession = sessions.get(0);
                    sessionId = latestSession.getId();
                    Log.d(TAG, "최근 세션 발견: " + sessionId);
                    loadSessionMessages(sessionId);
                } else {
                    Log.d(TAG, "저장된 대화 내역이 없습니다");
                }
            }

            @Override
            public void onFailure(Call<SessionListResponse> call, Throwable t) {
                Log.e(TAG, "세션 목록 조회 중 오류 발생", t);
            }
        });
    }

    /**
     * 세션의 메시지 불러오기
     */
    private void loadSessionMessages(int sessionId) {
        if (apiService == null) {
            Log.e(TAG, "API 서비스가 초기화되지 않았습니다");
            return;
        }

        apiService.getSessionMessages(sessionId).enqueue(new Callback<MessageListResponse>() {
            @Override
            public void onResponse(Call<MessageListResponse> call, Response<MessageListResponse> response) {
                if (response == null || !response.isSuccessful()) {
                    Log.w(TAG, "메시지 목록 조회 실패: " + (response != null ? response.code() : "null response"));
                    return;
                }

                MessageListResponse messageListResponse = response.body();
                if (messageListResponse == null || !messageListResponse.isSuccess()) {
                    Log.w(TAG, "메시지 응답이 null이거나 실패");
                    return;
                }

                List<MessageInfo> messages = messageListResponse.getMessages();
                if (messages != null && !messages.isEmpty()) {
                    messageList.clear();

                    for (MessageInfo messageInfo : messages) {
                        long timestamp = parseTimestamp(messageInfo.getCreatedAt());
                        Message message = new Message(
                                messageInfo.getContent(),
                                messageInfo.isUser(),
                                timestamp
                        );
                        messageList.add(message);
                    }

                    runOnUiThread(() -> {
                        chatAdapter.notifyDataSetChanged();
                        scrollToBottom();
                    });

                    Log.d(TAG, "메시지 " + messages.size() + "개 불러오기 완료");
                } else {
                    Log.d(TAG, "세션에 메시지가 없습니다");
                }
            }

            @Override
            public void onFailure(Call<MessageListResponse> call, Throwable t) {
                Log.e(TAG, "메시지 목록 조회 중 오류 발생", t);
            }
        });
    }

    /**
     * ISO 8601 타임스탬프를 밀리초로 변환
     */
    private long parseTimestamp(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) {
            return System.currentTimeMillis();
        }

        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = format.parse(isoTimestamp);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            Log.e(TAG, "타임스탬프 파싱 실패: " + isoTimestamp, e);
            return System.currentTimeMillis();
        }
    }

    /**
     * API로 메시지 전송
     */
    private void sendMessageToApi(String messageText) {
        if (userUuid == null) {
            Log.w(TAG, "사용자 UUID가 없습니다. API 호출 중단");
            Toast.makeText(this, "사용자 정보를 불러오는 중입니다", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatRequest request = new ChatRequest(userUuid, messageText, sessionId);
        Log.d(TAG, "API 호출 시작 - UUID: " + userUuid + ", SessionID: " + sessionId);

        Call<ChatResponse> call = apiService.sendChatMessage(request);
        call.enqueue(new Callback<ChatResponse>() {
            // sendMessageToApi() 메서드 내부의 onResponse 콜백 수정
            @Override
            public void onResponse(@NonNull Call<ChatResponse> call,
                                   @NonNull Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ChatResponse chatResponse = response.body();

                    if (chatResponse.isSuccess()) {
                        String aiMessage = chatResponse.getAiResponse();

                        if (aiMessage != null && !aiMessage.isEmpty()) {
                            addAiMessage(aiMessage);
                            Log.d(TAG, "AI 응답 수신: " + aiMessage);

                            // 경로 탐색 요청 처리
                            if (chatResponse.getRouteSearchRequested() != null &&
                                    chatResponse.getRouteSearchRequested()) {
                                handleRouteSearchRequest(
                                        chatResponse.getStartLocation(),
                                        chatResponse.getDestination()
                                );
                                return;
                            }

                            // 날씨 조회 요청 처리
                            if (chatResponse.getWeatherRequested() != null &&
                                    chatResponse.getWeatherRequested()) {
                                handleWeatherRequest(chatResponse.getWeatherTargetDate());
                                return;
                            }

                            // AI가 질문으로 끝나고 마지막 입력이 음성일 때만 자동 음성 인식 시작
                            if (isQuestion(aiMessage) && lastInputWasVoice) {
                                Log.d(TAG, "후속 질문 감지 + 이전 입력이 음성 → 자동 음성인식 시작");
                                startAutoListening();
                            } else if (isQuestion(aiMessage)) {
                                Log.d(TAG, "후속 질문 감지되었으나 이전 입력이 텍스트 → 자동 음성인식 취소");
                            }
                        }

                        if (chatResponse.getSessionId() != null) {
                            sessionId = chatResponse.getSessionId();
                            saveUserInfo();
                            Log.d(TAG, "세션 ID 업데이트: " + sessionId);
                        }

                        if (chatResponse.getFunctionCalled() != null) {
                            String functionName = chatResponse.getFunctionCalled();
                            Log.d(TAG, "Function 호출됨: " + functionName);

                            // 일정 관련 함수 처리
                            if ("create_schedule".equals(functionName) ||
                                    "update_schedule".equals(functionName) ||
                                    "delete_schedule".equals(functionName)) {
                                refreshCalendarIfVisible();
                            }
                            // 알람 관련 함수 처리
                            else if ("create_alarm".equals(functionName) ||
                                    "update_alarm".equals(functionName) ||
                                    "delete_alarm".equals(functionName)) {
                                refreshAlarmIfVisible();
                            }
                        }
                    } else {
                        String error = chatResponse.getError();
                        if (error != null && !error.isEmpty()) {
                            addAiMessage("오류: " + error);
                            Log.e(TAG, "AI 응답 오류: " + error);
                        } else {
                            addAiMessage("알 수 없는 오류가 발생했습니다");
                            Log.e(TAG, "AI 응답 실패 (오류 메시지 없음)");
                        }
                    }
                } else {
                    addAiMessage("서버 응답 오류가 발생했습니다");
                    Log.e(TAG, "서버 응답 실패 - 코드: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponse> call, @NonNull Throwable t) {
                addAiMessage("네트워크 오류가 발생했습니다");
                Log.e(TAG, "API 호출 실패", t);
                Toast.makeText(MainActivity.this,
                        "네트워크 연결을 확인하세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * AI 메시지를 채팅에 추가
     */
    private void addAiMessage(String content) {
        if (content != null) {
            // 앞뒤 공백과 줄바꿈 완전히 제거
            content = content.trim();
            // 끝에 있는 줄바꿈 문자 제거
            content = content.replaceAll("[\\n\\r]+$", "");
        }

        Message aiMessage = new Message(content, false);
        chatAdapter.addMessage(aiMessage);
        scrollToBottom();
    }

    /**
     * 환영 메시지 추가 (이전 대화가 없을 때만)
     */
    private void addWelcomeMessage() {
        if (messageList.isEmpty()) {
            String welcomeMessage = getString(R.string.welcome_user, userNickname) + "\n무엇을 도와드릴까요?";
            Message aiMessage = new Message(welcomeMessage, false);
            messageList.add(aiMessage);
            chatAdapter.notifyDataSetChanged();
            scrollToBottom();
        }
    }

    /**
     * 사용자 메시지 전송
     */
    private void sendMessage(String content) {
        Message userMessage = new Message(content, true);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();

        sendMessageToApi(content);
    }

    /**
     * 채팅 스크롤을 최신 메시지로 이동
     */
    private void scrollToBottom() {
        if (messageList.size() > 0) {
            recyclerView.smoothScrollToPosition(messageList.size() - 1);
        }
    }

    /**
     * AI 메시지가 질문인지 확인
     */
    private boolean isQuestion(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        String trimmed = message.trim();

        // 물음표로 끝나는 경우
        if (trimmed.endsWith("?") || trimmed.endsWith("?")) {
            return true;
        }

        // 요청/질문 패턴
        String[] questionPatterns = {
                "알려주세요", "말씀해주세요", "말해주세요", "이야기해주세요",
                "입력해주세요", "선택해주세요", "확인해주세요",
                "뭐", "무엇", "어디", "언제", "누구", "왜", "어떻게",
                "어느", "몇", "얼마",
                "할까요", "하시겠어요", "주시겠어요", "드릴까요",
                "원하시나요", "필요하신가요", "있으신가요",
                "어떤", "어떠", "무슨"
        };

        for (String pattern : questionPatterns) {
            if (trimmed.contains(pattern)) {
                return true;
            }
        }

        // 문장이 짧고 "주세요"로 끝나는 경우
        if (trimmed.length() < 50 && trimmed.endsWith("주세요")) {
            return true;
        }

        return false;
    }

    /**
     * 자동 음성 인식 시작
     */
    private void startAutoListening() {
        // 이미 음성 인식 중이면 무시
        if (speechToTextService != null && speechToTextService.isListening()) {
            Log.d(TAG, "이미 음성 인식 중 - 자동 시작 취소");
            return;
        }

        // 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "음성 인식 권한 없음 - 자동 시작 취소");
            return;
        }

        // 기존 예약된 작업 취소
        if (autoListenRunnable != null) {
            autoListenHandler.removeCallbacks(autoListenRunnable);
        }

        // 1초 후 자동으로 음성 인식 시작
        autoListenRunnable = new Runnable() {
            @Override
            public void run() {
                if (speechToTextService != null && !speechToTextService.isListening()) {
                    Log.d(TAG, "자동 음성 인식 시작");
                    buttonVoice.setImageResource(R.drawable.ic_mic_active);
                    speechToTextService.startListening();

                    // 5초 후 자동으로 중지 (사용자가 말하지 않으면)
                    autoListenTimeoutRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (speechToTextService != null && speechToTextService.isListening()) {
                                Log.d(TAG, "자동 음성 인식 타임아웃 - 중지");
                                speechToTextService.stopListening();
                                buttonVoice.setImageResource(R.drawable.ic_mic);
                            }
                        }
                    };
                    autoListenHandler.postDelayed(autoListenTimeoutRunnable, 5000);
                }
            }
        };

        autoListenHandler.postDelayed(autoListenRunnable, AUTO_LISTEN_DELAY);
        Log.d(TAG, "자동 음성 인식 예약됨 (1초 후 시작)");
    }

    /**
     * 자동 음성 인식 취소
     */
    private void cancelAutoListening() {
        if (autoListenRunnable != null && autoListenHandler != null) {
            autoListenHandler.removeCallbacks(autoListenRunnable);
            Log.d(TAG, "자동 음성 인식 취소");
        }
        cancelAutoListenTimeout();
    }

    /**
     * 자동 음성 인식 타임아웃 취소
     */
    private void cancelAutoListenTimeout() {
        if (autoListenTimeoutRunnable != null && autoListenHandler != null) {
            autoListenHandler.removeCallbacks(autoListenTimeoutRunnable);
            Log.d(TAG, "자동 음성 인식 타임아웃 취소");
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_chat) {
            showChatInterface();
            toolbar.setTitle(R.string.menu_chat);
        } else if (id == R.id.nav_calendar) {
            showFragment(new CalendarFragment());
            toolbar.setTitle(R.string.menu_calendar);
        } else if (id == R.id.nav_alarm) {
            showFragment(new AlarmFragment());
            toolbar.setTitle(R.string.menu_alarm);
        } else if (id == R.id.nav_route_info) {
            showFragment(new RouteFragment());
            toolbar.setTitle("추천 경로 정보");
        } else if (id == R.id.nav_weather) {
            showFragment(new WeatherFragment());
            toolbar.setTitle("날씨 정보");
        } else if (id == R.id.nav_notifications) {
            showFragment(new NotificationsFragment());
            toolbar.setTitle(R.string.menu_notifications);
        } else if (id == R.id.nav_settings) {
            showFragment(new SettingsFragment());
            toolbar.setTitle("설정");
        } else if (id == R.id.nav_help) {
            showFragment(new HelpFragment());
            toolbar.setTitle("도움말");
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * 뒤로 가기 버튼 처리
     */
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null) {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();

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
     * 프래그먼트 표시
     */
    public void showFragment(Fragment fragment) {
        View chatContainer = findViewById(R.id.chat_container);
        chatContainer.setVisibility(View.GONE);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * 채팅 인터페이스 표시
     */
    private void showChatInterface() {
        View chatContainer = findViewById(R.id.chat_container);
        chatContainer.setVisibility(View.VISIBLE);

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
        updateNavigationHeader();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (speechToTextService != null) {
            speechToTextService.destroy();
            Log.d(TAG, "STT 서비스 정리 완료");
        }

        // 자동 음성 인식 정리
        if (autoListenHandler != null) {
            if (autoListenRunnable != null) {
                autoListenHandler.removeCallbacks(autoListenRunnable);
            }
            if (autoListenTimeoutRunnable != null) {
                autoListenHandler.removeCallbacks(autoListenTimeoutRunnable);
            }
        }
    }

    /**
     * 툴바 제목 설정
     */
    public void setToolbarTitle(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    /**
     * CalendarFragment 새로고침
     */
    private void refreshCalendarIfVisible() {
        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof CalendarFragment) {
            // 현재 CalendarFragment가 보이면 즉시 동기화
            ((CalendarFragment) currentFragment).syncFromServer();
            Log.d(TAG, "CalendarFragment 서버 동기화 요청");
        } else {
            // CalendarFragment가 안 보이면 플래그 설정
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("calendar_needs_sync", true).apply();
            Log.d(TAG, "일정 변경 플래그 설정");
        }
    }

    /**
     * AlarmFragment 새로고침
     */
    private void refreshAlarmIfVisible() {
        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof AlarmFragment) {
            showFragment(new AlarmFragment());
            toolbar.setTitle(R.string.menu_alarm);
            Log.d(TAG, "AlarmFragment 새로고침 완료");
        }
    }

    /**
     * 날씨 데이터 API 호출
     */
    private void fetchWeatherDataForChat(String targetDate) {
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        String baseTime = getWeatherBaseTime();

        Log.d(TAG, "날씨 API 호출 - baseDate: " + currentDate + ", baseTime: " + baseTime +
                ", nx: " + currentNx + ", ny: " + currentNy);

        weatherApiService.getVillageForecast(
                BuildConfig.KMA_API_HUB_KEY,
                500,
                1,
                "JSON",
                currentDate,
                baseTime,
                currentNx,
                currentNy
        ).enqueue(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<String> call,
                                   @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "날씨 API 응답 성공");

                    try {
                        cachedWeatherData = lenientGson.fromJson(response.body(), JsonObject.class);
                        String weatherInfo = formatWeatherInfoFromData(cachedWeatherData, targetDate);

                        if (weatherInfo != null && !weatherInfo.isEmpty()) {
                            sendWeatherContextToAi(weatherInfo);
                        } else {
                            addAiMessage("날씨 정보를 처리하는 중 문제가 발생했어요");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "날씨 데이터 파싱 실패", e);
                        addAiMessage("날씨 정보를 처리하는 중 문제가 발생했어요");
                    }
                } else {
                    Log.e(TAG, "날씨 API 응답 실패: " + response.code());
                    addAiMessage("날씨 정보를 가져오는데 실패했어요");
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<String> call, @NonNull Throwable t) {
                Log.e(TAG, "날씨 API 호출 실패", t);
                addAiMessage("네트워크 오류로 날씨 정보를 가져올 수 없어요");
            }
        });
    }

    /**
     * 날씨 API 기준 시각 계산
     */
    private String getWeatherBaseTime() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        if (hour < 2 || (hour == 2 && minute <= 10)) {
            cal.add(Calendar.DATE, -1);
            return "2300";
        }

        int[] baseTimes = {2, 5, 8, 11, 14, 17, 20, 23};
        for (int i = baseTimes.length - 1; i >= 0; i--) {
            if (hour >= baseTimes[i]) {
                return String.format(Locale.getDefault(), "%02d00", baseTimes[i]);
            }
        }

        return "2300";
    }

    /**
     * 날씨 데이터를 자연스러운 문장으로 포맷팅
     */
    private String formatWeatherInfoFromData(JsonObject weatherData, String targetDate) {
        if (weatherData == null) {
            return null;
        }

        try {
            JsonObject responseObj = weatherData.getAsJsonObject("response");
            if (responseObj == null) return null;

            JsonElement bodyElement = responseObj.get("body");
            if (bodyElement == null || !bodyElement.isJsonObject()) return null;

            JsonElement itemsElement = bodyElement.getAsJsonObject().get("items");
            if (itemsElement == null || !itemsElement.isJsonObject()) return null;

            JsonElement itemElement = itemsElement.getAsJsonObject().get("item");
            if (itemElement == null || !itemElement.isJsonArray()) return null;

            List<WeatherResponse.WeatherItem> items = lenientGson.fromJson(
                    itemElement.getAsJsonArray(),
                    new com.google.gson.reflect.TypeToken<List<WeatherResponse.WeatherItem>>() {}.getType()
            );

            // 대상 날짜 계산
            Calendar targetCal = Calendar.getInstance();
            if ("tomorrow".equals(targetDate)) {
                targetCal.add(Calendar.DATE, 1);
            } else if ("day_after_tomorrow".equals(targetDate)) {
                targetCal.add(Calendar.DATE, 2);
            }

            String targetDateStr = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    .format(targetCal.getTime());
            int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean isToday = "today".equals(targetDate);

            // 대상 날짜 데이터 필터링
            List<WeatherResponse.WeatherItem> targetDateItems = items.stream()
                    .filter(item -> targetDateStr.equals(item.fcstDate))
                    .collect(Collectors.toList());

            if (targetDateItems.isEmpty()) {
                return null;
            }

            // 시간대별 날씨 정보 그룹화
            Map<String, Map<String, String>> hourlyData = targetDateItems.stream()
                    .collect(Collectors.groupingBy(
                            item -> item.fcstTime,
                            Collectors.toMap(
                                    item -> item.category,
                                    item -> item.fcstValue,
                                    (v1, v2) -> v1
                            )
                    ));

            List<String> sortedHours = new ArrayList<>(hourlyData.keySet());
            Collections.sort(sortedHours);

            int startHour = isToday ? currentHour : 0;
            String startTimeStr = String.format(Locale.getDefault(), "%02d00", startHour);

            StringBuilder weatherInfo = new StringBuilder();

            if (isToday) {
                weatherInfo.append(String.format("현재 %d시, ", currentHour));
            }

            String prevCondition = null;
            int prevHour = -1;
            int sameConditionStartHour = -1;

            for (String hour : sortedHours) {
                if (hour.compareTo(startTimeStr) < 0) continue;

                Map<String, String> data = hourlyData.get(hour);

                // 빈 문자열 방어: 기본값 설정
                String sky = data.getOrDefault("SKY", "1");
                String pty = data.getOrDefault("PTY", "0");

                // 추가 방어: null이나 빈 문자열 체크
                if (sky == null || sky.trim().isEmpty()) {
                    sky = "1";
                }
                if (pty == null || pty.trim().isEmpty()) {
                    pty = "0";
                }

                String condition = getWeatherConditionText(sky, pty);
                int hourInt = Integer.parseInt(hour.substring(0, 2));

                if (prevCondition == null) {
                    sameConditionStartHour = hourInt;
                    prevCondition = condition;
                } else if (!condition.equals(prevCondition)) {
                    if (sameConditionStartHour == prevHour) {
                        weatherInfo.append(String.format("%d시에는 %s, ", prevHour, prevCondition));
                    } else {
                        weatherInfo.append(String.format("%d시부터 %d시까지는 %s, ",
                                sameConditionStartHour, prevHour, prevCondition));
                    }

                    sameConditionStartHour = hourInt;
                    prevCondition = condition;
                }

                prevHour = hourInt;
            }

            if (prevCondition != null && sameConditionStartHour != -1) {
                if (sameConditionStartHour == prevHour) {
                    weatherInfo.append(String.format("%d시에는 %s 소식이 있어요", prevHour, prevCondition));
                } else {
                    weatherInfo.append(String.format("%d시부터는 %s 소식이 있어요",
                            sameConditionStartHour, prevCondition));
                }
            }

            // 최고/최저 기온 추가
            String maxTemp = getMaxTemp(targetDateItems);
            String minTemp = getMinTemp(targetDateItems);
            if (maxTemp != null && minTemp != null) {
                weatherInfo.append(String.format(". 최고 기온 %s도, 최저 기온 %s도예요", maxTemp, minTemp));
            }

            return weatherInfo.toString();

        } catch (Exception e) {
            Log.e(TAG, "날씨 정보 포맷팅 실패", e);
            return null;
        }
    }

    /**
     * 날씨 상태 코드를 텍스트로 변환
     */
    private String getWeatherConditionText(String sky, String pty) {
        // 빈 문자열이거나 null이면 기본값 사용
        if (pty == null || pty.trim().isEmpty()) {
            pty = "0";
        }
        if (sky == null || sky.trim().isEmpty()) {
            sky = "1";
        }

        int ptyValue;
        try {
            ptyValue = Integer.parseInt(pty.trim());
        } catch (NumberFormatException e) {
            Log.w(TAG, "PTY 값 파싱 실패: " + pty);
            ptyValue = 0;
        }

        if (ptyValue > 0) {
            switch (ptyValue) {
                case 1: return "비";
                case 2: return "비/눈";
                case 3: return "눈";
                case 4: return "소나기";
                default: return "강수";
            }
        }

        int skyValue;
        try {
            skyValue = Integer.parseInt(sky.trim());
        } catch (NumberFormatException e) {
            Log.w(TAG, "SKY 값 파싱 실패: " + sky);
            skyValue = 1;
        }

        switch (skyValue) {
            case 1: return "맑음";
            case 3: return "구름많음";
            case 4: return "흐림";
            default: return "알 수 없음";
        }
    }

    /**
     * 최고 기온 조회
     */
    private String getMaxTemp(List<WeatherResponse.WeatherItem> items) {
        for (WeatherResponse.WeatherItem item : items) {
            if ("TMX".equals(item.category)) {
                return item.fcstValue;
            }
        }
        return null;
    }

    /**
     * 최저 기온 조회
     */
    private String getMinTemp(List<WeatherResponse.WeatherItem> items) {
        for (WeatherResponse.WeatherItem item : items) {
            if ("TMN".equals(item.category)) {
                return item.fcstValue;
            }
        }
        return null;
    }

    /**
     * 날씨 조회 요청 처리
     */
    private void handleWeatherRequest(String targetDate) {
        if (targetDate == null) {
            Log.w(TAG, "날씨 조회 대상 날짜가 null");
            return;
        }

        Log.d(TAG, "날씨 조회 요청: " + targetDate);

        // 캐시된 날씨 데이터가 있으면 바로 사용
        if (cachedWeatherData != null) {
            String weatherInfo = formatWeatherInfoFromData(cachedWeatherData, targetDate);

            if (weatherInfo != null && !weatherInfo.isEmpty()) {
                sendWeatherContextToAi(weatherInfo);
                return;
            }
        }

        // 캐시된 데이터가 없으면 API 호출
        Log.d(TAG, "날씨 데이터 API 호출 시작");
        fetchWeatherDataForChat(targetDate);
    }

    /**
     * 날씨 정보를 AI에게 전달
     */
    private void sendWeatherContextToAi(String weatherInfo) {
        if (userUuid == null) {
            Log.w(TAG, "사용자 UUID 없음");
            return;
        }

        Map<String, Object> context = new HashMap<>();
        context.put("weather_data", weatherInfo);

        ChatRequest request = new ChatRequest(userUuid, "날씨 정보를 받았습니다", sessionId, context);
        Log.d(TAG, "날씨 Context 전송");

        Call<ChatResponse> call = apiService.sendChatMessage(request);
        call.enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(@NonNull Call<ChatResponse> call,
                                   @NonNull Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ChatResponse chatResponse = response.body();

                    if (chatResponse.isSuccess()) {
                        String aiMessage = chatResponse.getAiResponse();
                        if (aiMessage != null && !aiMessage.isEmpty()) {
                            addAiMessage(aiMessage);
                            Log.d(TAG, "날씨 기반 AI 응답 완료");
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ChatResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "날씨 Context 전송 실패", t);
                addAiMessage("네트워크 오류가 발생했어요");
            }
        });
    }

    /**
     * 날씨 API 서비스 초기화
     */
    private void initializeWeatherService() {
        lenientGson = new GsonBuilder().setLenient().create();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://apihub.kma.go.kr/api/typ02/openApi/")
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
                .build();

        weatherApiService = retrofit.create(WeatherApiService.class);

        Log.d(TAG, "날씨 API 서비스 초기화 완료");
    }

    /**
     * 경로 탐색 요청 처리
     */
    private void handleRouteSearchRequest(String startLocation, String destination) {
        if (destination == null || destination.isEmpty()) {
            Log.e(TAG, "도착지 정보가 없습니다");
            Toast.makeText(this, "도착지 정보를 확인할 수 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "경로 탐색 시작 - 출발지: " + startLocation + ", 도착지: " + destination);

        // 로딩 다이얼로그 표시
        ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("경로를 탐색 중입니다...");
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        // RouteFragment로 이동하여 경로 탐색 수행
        RouteFragment routeFragment = new RouteFragment();
        Bundle args = new Bundle();
        args.putString("destination", destination);
        args.putString("start_location", startLocation);
        args.putBoolean("auto_search", true);
        routeFragment.setArguments(args);

        // 다이얼로그를 0.5초 후에 닫고 화면 전환
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            loadingDialog.dismiss();

            // 채팅 컨테이너 숨기기
            View chatContainer = findViewById(R.id.chat_container);
            chatContainer.setVisibility(View.GONE);

            // RouteFragment로 전환
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, routeFragment)
                    .addToBackStack(null)
                    .commit();

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("추천 경로 정보");
            }

            Toast.makeText(this, "경로 탐색이 완료되면 결과가 표시됩니다", Toast.LENGTH_LONG).show();
        }, 500);
    }

    /**
     * 서버 동기화 확인
     */
    private void checkAndSyncUserWithServer() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean needsSync = prefs.getBoolean("needs_server_sync", false);

        if (!needsSync) {
            Log.d(TAG, "서버 동기화 불필요");
            return;
        }

        if (userUuid == null || userUuid.isEmpty()) {
            Log.w(TAG, "UUID가 없어 서버 동기화 불가");
            return;
        }

        Log.d(TAG, "백그라운드에서 서버 동기화 시작");

        new Thread(() -> {
            try {
                Thread.sleep(2000);

                runOnUiThread(() -> {
                    verifyOrCreateUserOnServer();
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "서버 동기화 대기 중 인터럽트", e);
            }
        }).start();
    }

    /**
     * 서버에 사용자 확인 및 생성
     */
    private void verifyOrCreateUserOnServer() {
        if (apiService == null || userUuid == null) {
            Log.e(TAG, "API 서비스 또는 UUID가 없습니다");
            return;
        }

        Log.d(TAG, "서버에 사용자 존재 확인 중: " + userUuid);

        apiService.getUser(userUuid).enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserResponse> call, @NonNull Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "사용자가 서버에 이미 등록되어 있음");
                    updateSyncFlag(false);
                } else if (response.code() == 404) {
                    Log.w(TAG, "서버에 사용자 없음 - 자동 생성 시도");
                    createUserOnServerBackground();
                } else {
                    Log.e(TAG, "사용자 조회 실패 - 응답 코드: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "서버 사용자 조회 실패", t);
            }
        });
    }

    /**
     * 백그라운드에서 서버에 사용자 생성
     */
    private void createUserOnServerBackground() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String nickname = prefs.getString("nickname", "사용자");

        UserCreateRequest request = new UserCreateRequest(nickname, 1800);

        Log.d(TAG, "백그라운드에서 서버에 사용자 생성 중...");

        apiService.createUser(request).enqueue(new Callback<UserCreateResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserCreateResponse> call, @NonNull Response<UserCreateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String serverUuid = response.body().getUuid();

                    if (serverUuid != null && !serverUuid.isEmpty()) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("user_uuid", serverUuid);
                        editor.putBoolean("needs_server_sync", false);
                        editor.apply();

                        userUuid = serverUuid;

                        Log.d(TAG, "백그라운드 서버 등록 성공: " + serverUuid);

                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                    "서버 연결 완료",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e(TAG, "백그라운드 서버 등록 실패 - 응답 코드: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserCreateResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "백그라운드 서버 등록 실패", t);
            }
        });
    }

    /**
     * 서버 동기화 플래그 업데이트
     */
    private void updateSyncFlag(boolean needsSync) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("needs_server_sync", needsSync);
        editor.apply();

        Log.d(TAG, "서버 동기화 플래그 업데이트: " + needsSync);
    }

    /**
     * Toolbar 메뉴 생성
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    /**
     * Toolbar 메뉴 아이템 클릭 처리
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_session_list) {
            showSessionListBottomSheet();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 세션 목록 바텀시트 표시
     */
    private void showSessionListBottomSheet() {
        if (userUuid == null) {
            Toast.makeText(this, "사용자 정보를 불러올 수 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        SessionListBottomSheet bottomSheet = SessionListBottomSheet.newInstance(userUuid, sessionId);
        bottomSheet.setOnSessionActionListener(new SessionListBottomSheet.OnSessionActionListener() {
            @Override
            public void onSessionSelected(int selectedSessionId) {
                switchToSession(selectedSessionId);
            }

            @Override
            public void onNewChatRequested() {
                startNewChat();
            }

            @Override
            public void onCurrentSessionDeleted() {
                startNewChat();
            }
        });

        bottomSheet.show(getSupportFragmentManager(), "SessionListBottomSheet");
    }

    /**
     * 다른 세션으로 전환
     */
    private void switchToSession(int newSessionId) {
        sessionId = newSessionId;
        saveUserInfo();

        messageList.clear();
        chatAdapter.notifyDataSetChanged();

        loadSessionMessages(newSessionId);

        Log.d(TAG, "세션 전환 완료: " + newSessionId);
    }

    /**
     * 새 대화 시작
     */
    private void startNewChat() {
        sessionId = null;
        saveUserInfo();

        messageList.clear();
        chatAdapter.notifyDataSetChanged();

        Toast.makeText(this, "새 대화를 시작합니다", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "새 대화 시작");
    }
}