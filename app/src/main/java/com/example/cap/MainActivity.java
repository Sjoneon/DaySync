package com.example.cap;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get nickname from SharedPreferences
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String nickname = preferences.getString("nickname", "사용자");

        // 텍스트뷰 확인 후 환영인사
        TextView tvMainMessage = findViewById(R.id.tvMainMessage);
        if (tvMainMessage != null) {
            tvMainMessage.setText(nickname + "님, 환영합니다!");
        }
    }
}