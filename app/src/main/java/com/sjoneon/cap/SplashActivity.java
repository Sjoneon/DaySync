// SplashActivity.java
package com.sjoneon.cap;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DURATION = 2000; //  2초

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
                        // 일단 LoginActivity로 바로 이동하게 수정
                        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Handler 내부 오류: ", e);
                        Toast.makeText(SplashActivity.this, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            }, SPLASH_DURATION);

        } catch (Exception e) {
            Log.e(TAG, "onCreate 오류: ", e);
            Toast.makeText(this, "스플래시 화면 로드 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}