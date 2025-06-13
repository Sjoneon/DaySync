package com.sjoneon.cap;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
                etNickname.setSelection(existingNickname.length()); // 커서를 끝으로 이동
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

            // 닉네임 저장
            saveNickname(nickname);

            // MainActivity로 이동
            navigateToMainActivity(nickname);

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
     * 닉네임을 SharedPreferences에 저장하는 메서드
     * @param nickname 저장할 닉네임
     */
    private void saveNickname(String nickname) {
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("nickname", nickname);
        editor.putBoolean("is_logged_in", true);
        editor.putLong("setup_time", System.currentTimeMillis()); // 설정 시간 기록
        editor.apply();

        Log.d(TAG, "닉네임 저장 완료: " + nickname);
    }

    /**
     * MainActivity로 이동하는 메서드
     * @param nickname 설정된 닉네임
     */
    private void navigateToMainActivity(String nickname) {
        Toast.makeText(this, getString(R.string.welcome_user, nickname),
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("user_nickname", nickname); // 닉네임을 MainActivity에 전달
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