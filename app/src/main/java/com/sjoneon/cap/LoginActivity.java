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
            etNickname = findViewById(R.id.etNickname);
            btnSubmit = findViewById(R.id.btnSubmit);

            if (etNickname == null || btnSubmit == null) {
                Log.e(TAG, "View가 null입니다: etNickname=" + (etNickname == null) + ", btnSubmit=" + (btnSubmit == null));
            } else {
                Log.d(TAG, "View 초기화 완료");
            }

            // 버튼 클릭 리스너 설정
            btnSubmit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        String nickname = etNickname.getText().toString().trim();

                        if (nickname.isEmpty()) {
                            Toast.makeText(LoginActivity.this, getString(R.string.empty_nickname_error), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 닉네임을 SharedPreferences에 저장
                        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("nickname", nickname);
                        editor.putBoolean("is_logged_in", true);
                        editor.apply();

                        // MainActivity로 이동
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "버튼 클릭 처리 오류: ", e);
                        Toast.makeText(LoginActivity.this, getString(R.string.login_process_error), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "onCreate 오류: ",e);
            Toast.makeText(this, getString(R.string.login_screen_error), Toast.LENGTH_SHORT).show();
        }
    }
}