package com.sjoneon.cap.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sjoneon.cap.R;

/**
 * 앱 시작 시 스플래시 화면을 표시하고
 * 초기 사용자 설정 상태를 확인하는 액티비티
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DURATION = 2000; // 2초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            Log.d(TAG, "onCreate 시작됨");
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_splash);
            Log.d(TAG, "setContentView 완료");

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Handler 실행됨");

                        // 사용자 설정 상태 확인
                        checkUserSetupStatus();

                    } catch (Exception e) {
                        Log.e(TAG, "Handler 내부 오류: ", e);
                        Toast.makeText(SplashActivity.this, getString(R.string.error_occurred), Toast.LENGTH_SHORT).show();
                    }
                }
            }, SPLASH_DURATION);

        } catch (Exception e) {
            Log.e(TAG, "onCreate 오류: ", e);
            Toast.makeText(this, getString(R.string.splash_screen_error), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 사용자 초기 설정 상태를 확인하고 적절한 액티비티로 이동하는 메서드
     */
    private void checkUserSetupStatus() {
        try {
            SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            boolean isLoggedIn = preferences.getBoolean("is_logged_in", false);
            String nickname = preferences.getString("nickname", "");

            Log.d(TAG, "사용자 설정 상태 확인: isLoggedIn=" + isLoggedIn + ", nickname=" + nickname);

            Intent intent;

            // 처음 실행하거나 닉네임이 설정되지 않은 경우
            if (!isLoggedIn || nickname.isEmpty()) {
                Log.d(TAG, "초기 설정 필요 - LoginActivity로 이동");
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            } else {
                Log.d(TAG, "설정 완료 - MainActivity로 이동");
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }

            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "사용자 설정 상태 확인 오류: ", e);
            // 오류 발생 시 LoginActivity로 이동
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }
}