package com.sjoneon.cap.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sjoneon.cap.R;

/**
 * 데이터 복구를 위한 Activity
 * 사용자가 UUID를 입력하여 기존 데이터를 복구할 수 있습니다.
 */
public class RestoreDataActivity extends AppCompatActivity {

    private static final String TAG = "RestoreDataActivity";
    private EditText etUuid;
    private Button btnRestore;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restore_data);

        initializeViews();
        setupClickListeners();
    }

    /**
     * 뷰 요소들을 초기화하는 메서드
     */
    private void initializeViews() {
        etUuid = findViewById(R.id.etUuid);
        btnRestore = findViewById(R.id.btnRestore);
        btnBack = findViewById(R.id.btnBack);

        if (etUuid == null || btnRestore == null || btnBack == null) {
            Log.e(TAG, "View 초기화 실패");
            Toast.makeText(this, "화면 로딩 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 클릭 리스너들을 설정하는 메서드
     */
    private void setupClickListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processRestore();
            }
        });
    }

    /**
     * UUID 복구를 처리하는 메서드
     */
    private void processRestore() {
        String uuid = etUuid.getText().toString().trim();

        if (uuid.isEmpty()) {
            Toast.makeText(this, "복구 코드를 입력해주세요", Toast.LENGTH_SHORT).show();
            etUuid.requestFocus();
            return;
        }

        // TODO: 서버에 UUID 검증 요청 (다음 단계에서 구현)
        // 현재는 임시로 SharedPreferences에 저장만 함
        SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("user_uuid", uuid);
        editor.putBoolean("is_logged_in", true);
        editor.apply();

        Toast.makeText(this, "복구 기능은 준비 중입니다", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * 뒤로 가기 버튼 처리
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}