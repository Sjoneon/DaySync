package com.sjoneon.cap.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
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
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioManager;

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
import com.sjoneon.cap.services.GeminiLiveApiService;
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
import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private View inputLayout;
    private View chatContainer;

    private GeminiLiveApiService geminiLiveService;
    private AudioTrack audioTrack;
    private boolean isLiveApiActive = false;
    private static final int AUDIO_SAMPLE_RATE = 24000;

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
        loadUserUuid();
        checkAndSyncUserWithServer();
        checkRecordAudioPermission();

        setupKeyboardHandling();
        checkPermissionsAfterDelay();

        loadChatHistory();

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

                    if (!PermissionHelper.hasNotificationPermission(MainActivity.this) ||
                            !PermissionHelper.hasExactAlarmPermission(MainActivity.this)) {
                        showPermissionInfoDialog();
                    }
                }
            }
        }, 3000);
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
                sendMessage(messageContent);
                editTextMessage.setText("");
            }
        });

        // 마이크 버튼 - 길게 누르면 Live API, 짧게 누르면 STT
        buttonVoice.setOnTouchListener(new View.OnTouchListener() {
            private boolean isLongPress = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isLongPress = false;
                        v.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isLongPress = true;
                                toggleLiveApiMode();
                            }
                        }, 2000);
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.removeCallbacks(null);
                        if (!isLongPress) {
                            toggleSpeechRecognition();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(null);
                        return true;
                }
                return false;
            }
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
                    editTextMessage.setText(text);
                    buttonVoice.setImageResource(R.drawable.ic_mic);
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

        initializeGeminiLiveService();
    }

    /**
     * Gemini Live API 서비스 초기화
     */
    private void initializeGeminiLiveService() {
        String geminiApiKey = BuildConfig.GEMINI_API_KEY;

        // 디버깅: API 키 확인 (키 값은 로그에 출력하지 않음)
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            Log.e(TAG, "❌ GEMINI_API_KEY가 설정되지 않았습니다!");
            Toast.makeText(this, "API 키가 설정되지 않았습니다", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "✅ API 키 로드 완료 (길이: " + geminiApiKey.length() + ")");

        geminiLiveService = new GeminiLiveApiService(this, geminiApiKey);
        geminiLiveService.setListener(new GeminiLiveApiService.LiveApiListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "✅ 음성 대화 연결 성공!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "✅ Gemini Live API 연결됨");
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    isLiveApiActive = false;
                    buttonVoice.setImageResource(R.drawable.ic_mic);
                    Toast.makeText(MainActivity.this,
                            "음성 대화가 종료되었습니다", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Gemini Live API 연결 해제됨");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "❌ 오류: " + error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "❌ Gemini Live API 오류: " + error);
                    isLiveApiActive = false;
                    buttonVoice.setImageResource(R.drawable.ic_mic);
                });
            }

            @Override
            public void onTextResponse(String text) {
                runOnUiThread(() -> {
                    Message aiMessage = new Message(text, false);
                    chatAdapter.addMessage(aiMessage);
                    scrollToBottom();
                    Log.d(TAG, "✅ AI 텍스트 응답: " + text);
                });
            }

            @Override
            public void onAudioResponse(byte[] audioData) {
                Log.d(TAG, "✅ 오디오 응답 수신: " + audioData.length + " bytes");
                playAudioResponse(audioData);
            }

            @Override
            public void onFunctionCall(String functionName, JSONObject parameters) {
                runOnUiThread(() -> {
                    Log.d(TAG, "✅ Function Call: " + functionName);
                    handleFunctionCall(functionName, parameters);
                });
            }
        });

        // 오디오 트랙 초기화
        int bufferSize = AudioTrack.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        Log.d(TAG, "AudioTrack 버퍼 크기: " + bufferSize);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
        );

        Log.d(TAG, "✅ Gemini Live 서비스 초기화 완료");
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
                        }

                        if (chatResponse.getSessionId() != null) {
                            sessionId = chatResponse.getSessionId();
                            saveUserInfo();
                            Log.d(TAG, "세션 ID 업데이트: " + sessionId);
                        }

                        if (chatResponse.getFunctionCalled() != null) {
                            String functionName = chatResponse.getFunctionCalled();
                            Log.d(TAG, "Function 호출됨: " + functionName);

                            if ("create_schedule".equals(functionName)) {
                                refreshCalendarIfVisible();
                            } else if ("create_alarm".equals(functionName)) {
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
            content = content.trim();
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

        if (geminiLiveService != null) {
            geminiLiveService.destroy();
            Log.d(TAG, "Gemini Live 서비스 정리 완료");
        }

        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
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
            showFragment(new CalendarFragment());
            toolbar.setTitle(R.string.menu_calendar);
            Log.d(TAG, "CalendarFragment 새로고침 완료");
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

    /**
     * Live API 모드 토글
     */
    private void toggleLiveApiMode() {
        if (!isLiveApiActive) {
            if (geminiLiveService != null) {
                geminiLiveService.connect();
                new android.os.Handler().postDelayed(() -> {
                    if (geminiLiveService.isConnected()) {
                        geminiLiveService.startRecording();
                        isLiveApiActive = true;
                        buttonVoice.setImageResource(R.drawable.ic_mic_active);
                    }
                }, 1000);
            }
        } else {
            if (geminiLiveService != null) {
                geminiLiveService.stopRecording();
                geminiLiveService.disconnect();
                isLiveApiActive = false;
                buttonVoice.setImageResource(R.drawable.ic_mic);
            }
        }
    }

    /**
     * 오디오 응답 재생
     */
    private void playAudioResponse(byte[] audioData) {
        if (audioTrack != null && audioData != null && audioData.length > 0) {
            try {
                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.play();
                    audioTrack.write(audioData, 0, audioData.length);
                    Log.d(TAG, "오디오 재생: " + audioData.length + " bytes");
                }
            } catch (Exception e) {
                Log.e(TAG, "오디오 재생 실패", e);
            }
        }
    }

    /**
     * Function Call 처리
     */
    private void handleFunctionCall(String functionName, org.json.JSONObject parameters) {
        Log.d(TAG, "함수 호출 처리: " + functionName);

        try {
            switch (functionName) {
                case "navigate_to_screen":
                    if (parameters != null && parameters.has("screen_name")) {
                        String screenName = parameters.getString("screen_name");
                        navigateToScreen(screenName);

                        String message = screenName + " 화면으로 이동했습니다.";
                        Message aiMessage = new Message(message, false);
                        chatAdapter.addMessage(aiMessage);
                        scrollToBottom();
                    }
                    break;

                default:
                    Log.w(TAG, "알 수 없는 함수: " + functionName);
            }
        } catch (Exception e) {
            Log.e(TAG, "함수 호출 처리 실패", e);
        }
    }

    /**
     * 화면 이동 처리
     */
    private void navigateToScreen(String screenName) {
        Fragment fragment = null;
        String title = "";

        switch (screenName) {
            case "calendar":
                fragment = new CalendarFragment();
                title = getString(R.string.menu_calendar);
                break;
            case "alarm":
                fragment = new AlarmFragment();
                title = getString(R.string.menu_alarm);
                break;
            case "route":
                fragment = new RouteFragment();
                title = "추천 경로 정보";
                break;
            case "map":
                fragment = new com.sjoneon.cap.fragments.MapFragment();
                title = getString(R.string.map_title);
                break;
            case "weather":
                fragment = new WeatherFragment();
                title = "날씨 정보";
                break;
            case "notifications":
                fragment = new NotificationsFragment();
                title = getString(R.string.menu_notifications);
                break;
            case "settings":
                fragment = new SettingsFragment();
                title = "설정";
                break;
            case "help":
                fragment = new HelpFragment();
                title = "도움말";
                break;
            case "chat":
                showChatInterface();
                toolbar.setTitle(R.string.menu_chat);
                return;
        }

        if (fragment != null) {
            showFragment(fragment);
            toolbar.setTitle(title);
        }
    }
}