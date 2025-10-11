package com.sjoneon.cap.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sjoneon.cap.R;
import com.sjoneon.cap.models.api.UserCreateRequest;
import com.sjoneon.cap.models.api.UserCreateResponse;
import com.sjoneon.cap.utils.ApiClient;

import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 사용자 닉네임 설정을 위한 로그인 액티비티
 * 앱 첫 실행 시 또는 닉네임이 설정되지 않은 경우 실행됩니다.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etNickname;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            Log.d(TAG, "onCreate 시작됨");
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_login);
            Log.d(TAG, "setContentView 완료");

            // 뷰 초기화
            initializeViews();

            // 버튼 클릭 리스너 설정
            setupClickListeners();

        } catch (Exception e) {
            Log.e(TAG, "onCreate 오류: ", e);
            Toast.makeText(this, getString(R.string.login_screen_error), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 뷰 요소들을 초기화하는 메서드
     */
    private void initializeViews() {
        etNickname = findViewById(R.id.etNickname);
        btnSubmit = findViewById(R.id.btnSubmit);

        if (etNickname == null || btnSubmit == null) {
            Log.e(TAG, "View가 null입니다: etNickname=" + (etNickname == null) +
                    ", btnSubmit=" + (btnSubmit == null));
            Toast.makeText(this, "화면 로딩 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "View 초기화 완료");

        // 기존 닉네임이 있는 경우 입력 필드에 표시
        loadExistingNickname();
    }

    /**
     * 기존 닉네임을 불러와서 입력 필드에 표시하는 메서드
     */
    private void loadExistingNickname() {
        try {
            SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            String existingNickname = preferences.getString("nickname", "");

            if (!existingNickname.isEmpty()) {
                etNickname.setText(existingNickname);
                etNickname.setSelection(existingNickname.length());
                Log.d(TAG, "기존 닉네임 로드됨: " + existingNickname);
            }
        } catch (Exception e) {
            Log.e(TAG, "기존 닉네임 로드 실패: ", e);
        }
    }

    /**
     * 클릭 리스너들을 설정하는 메서드
     */
    private void setupClickListeners() {
        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processNicknameSubmission();
            }
        });

        // Enter 키 입력 시에도 닉네임 제출 처리
        etNickname.setOnEditorActionListener((v, actionId, event) -> {
            processNicknameSubmission();
            return true;
        });
    }

    /**
     * 닉네임 제출을 처리하는 메서드
     */
    private void processNicknameSubmission() {
        try {
            String nickname = etNickname.getText().toString().trim();

            // 닉네임 유효성 검사
            if (!isValidNickname(nickname)) {
                return;
            }

            // 닉네임 저장 (서버 연동 포함)
            saveNickname(nickname);

        } catch (Exception e) {
            Log.e(TAG, "닉네임 처리 오류: ", e);
            Toast.makeText(LoginActivity.this, getString(R.string.login_process_error),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 닉네임 유효성을 검사하는 메서드
     * @param nickname 검사할 닉네임
     * @return 유효한 경우 true, 그렇지 않으면 false
     */
    private boolean isValidNickname(String nickname) {
        if (nickname.isEmpty()) {
            Toast.makeText(this, getString(R.string.empty_nickname_error),
                    Toast.LENGTH_SHORT).show();
            etNickname.requestFocus();
            return false;
        }

        if (nickname.length() > 20) {
            Toast.makeText(this, "닉네임은 20자 이하로 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            etNickname.requestFocus();
            return false;
        }

        return true;
    }

    /**
     * 닉네임을 SharedPreferences에 저장하고 서버에 사용자를 등록하는 메서드
     * @param nickname 저장할 닉네임
     */
    private void saveNickname(String nickname) {
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 기존 UUID 확인
        String existingUuid = preferences.getString("user_uuid", null);

        if (existingUuid == null) {
            // UUID가 없으면 서버에 사용자 생성 요청
            createUserOnServer(nickname, preferences);
        } else {
            // 이미 UUID가 있으면 닉네임만 업데이트하고 진행
            Log.d(TAG, "기존 UUID 사용: " + existingUuid);
            updateNicknameAndProceed(nickname, preferences);
        }
    }

    /**
     * 서버에 새 사용자를 생성하는 메서드
     * @param nickname 사용자 닉네임
     * @param preferences SharedPreferences 인스턴스
     */
    private void createUserOnServer(String nickname, SharedPreferences preferences) {
        // 가이드라인 준수: null 체크를 통한 방어적 프로그래밍
        if (ApiClient.getInstance() == null || ApiClient.getInstance().getApiService() == null) {
            Log.e(TAG, "API 클라이언트를 초기화할 수 없습니다");
            // 서버 등록 실패 시 로컬에서만 UUID 생성 (fallback)
            fallbackLocalUuidCreation(nickname, preferences);
            return;
        }

        // 서버에 사용자 생성 요청 (기존 생성자 사용)
        UserCreateRequest request = new UserCreateRequest(nickname, 1800);

        Log.d(TAG, "서버에 사용자 생성 요청 중...");

        ApiClient.getInstance().getApiService()
                .createUser(request)
                .enqueue(new Callback<UserCreateResponse>() {
                    @Override
                    public void onResponse(Call<UserCreateResponse> call, Response<UserCreateResponse> response) {
                        // 가이드라인 준수: 단계별 null 체크
                        if (response != null && response.isSuccessful() && response.body() != null) {
                            String serverUuid = response.body().getUuid();

                            // 가이드라인 준수: null 체크
                            if (serverUuid != null && !serverUuid.isEmpty()) {
                                // 서버에서 받은 UUID 저장
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putString("user_uuid", serverUuid);
                                editor.putString("nickname", nickname);
                                editor.putBoolean("is_logged_in", true);
                                editor.putLong("setup_time", System.currentTimeMillis());
                                editor.apply();

                                Log.d(TAG, "서버에서 UUID 생성 성공: " + serverUuid);

                                // MainActivity로 이동
                                runOnUiThread(() -> navigateToMainActivity(nickname));
                            } else {
                                Log.e(TAG, "서버 응답에 UUID가 없습니다");
                                fallbackLocalUuidCreation(nickname, preferences);
                            }
                        } else {
                            // 서버 응답 실패 시 에러 코드 로깅
                            int errorCode = response != null ? response.code() : -1;
                            Log.e(TAG, "서버 사용자 생성 실패 - 응답 코드: " + errorCode);

                            // fallback: 로컬에서 UUID 생성
                            fallbackLocalUuidCreation(nickname, preferences);
                        }
                    }

                    @Override
                    public void onFailure(Call<UserCreateResponse> call, Throwable t) {
                        // 가이드라인 준수: 예외 처리
                        Log.e(TAG, "서버 통신 실패", t);

                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this,
                                    "서버 연결 실패. 오프라인 모드로 진행합니다.",
                                    Toast.LENGTH_SHORT).show();
                        });

                        // fallback: 로컬에서 UUID 생성
                        fallbackLocalUuidCreation(nickname, preferences);
                    }
                });
    }

    /**
     * 서버 등록 실패 시 로컬에서 UUID를 생성하는 fallback 메서드
     * @param nickname 사용자 닉네임
     * @param preferences SharedPreferences 인스턴스
     */
    private void fallbackLocalUuidCreation(String nickname, SharedPreferences preferences) {
        // 가이드라인 준수: 방어적 프로그래밍
        try {
            String localUuid = UUID.randomUUID().toString();

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("user_uuid", localUuid);
            editor.putString("nickname", nickname);
            editor.putBoolean("is_logged_in", true);
            editor.putBoolean("needs_server_sync", true);
            editor.putLong("setup_time", System.currentTimeMillis());
            editor.apply();

            Log.w(TAG, "로컬에서 UUID 생성 (서버 미등록): " + localUuid);
            Log.w(TAG, "나중에 서버 연결 시 동기화가 필요합니다");

            // MainActivity로 이동
            runOnUiThread(() -> navigateToMainActivity(nickname));

        } catch (Exception e) {
            Log.e(TAG, "UUID 생성 실패", e);
            runOnUiThread(() -> {
                Toast.makeText(LoginActivity.this,
                        "초기화 중 오류가 발생했습니다",
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 닉네임만 업데이트하고 MainActivity로 이동
     * @param nickname 사용자 닉네임
     * @param preferences SharedPreferences 인스턴스
     */
    private void updateNicknameAndProceed(String nickname, SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("nickname", nickname);
        editor.putBoolean("is_logged_in", true);
        editor.apply();

        Log.d(TAG, "닉네임 업데이트 완료: " + nickname);
        navigateToMainActivity(nickname);
    }

    /**
     * MainActivity로 이동하는 메서드
     * @param nickname 설정된 닉네임
     */
    private void navigateToMainActivity(String nickname) {
        Toast.makeText(this, getString(R.string.welcome_user, nickname),
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("user_nickname", nickname);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        Log.d(TAG, "MainActivity로 이동 완료");
    }

    /**
     * 뒤로 가기 버튼 처리
     * 초기 설정 화면에서는 앱을 종료합니다.
     */
    @Override
    public void onBackPressed() {
        // 초기 설정 화면에서는 앱 종료
        super.onBackPressed();
        finishAffinity();
    }
}