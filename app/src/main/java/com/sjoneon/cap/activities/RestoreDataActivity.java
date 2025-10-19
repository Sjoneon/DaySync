package com.sjoneon.cap.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;


import com.sjoneon.cap.models.api.UserResponse;
import com.sjoneon.cap.utils.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        btnRestore.setEnabled(false);
        Toast.makeText(this, "복구 중...", Toast.LENGTH_SHORT).show();

        if (ApiClient.getInstance() == null || ApiClient.getInstance().getApiService() == null) {
            Log.e(TAG, "API 클라이언트 초기화 실패");
            runOnUiThread(() -> {
                Toast.makeText(this, "네트워크 연결을 확인해주세요", Toast.LENGTH_SHORT).show();
                btnRestore.setEnabled(true);
            });
            return;
        }

        ApiClient.getInstance().getApiService()
                .getUser(uuid)
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                        if (response != null && response.isSuccessful() && response.body() != null) {
                            UserResponse userResponse = response.body();

                            SharedPreferences preferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("user_uuid", userResponse.getUuid());
                            editor.putString("nickname", userResponse.getNickname());
                            editor.putInt("prep_time", userResponse.getPrepTime());
                            editor.putBoolean("is_logged_in", true);
                            editor.putBoolean("needs_server_sync", false);
                            editor.apply();

                            Log.d(TAG, "데이터 복구 성공: " + userResponse.getNickname());

                            runOnUiThread(() -> {
                                Toast.makeText(RestoreDataActivity.this,
                                        userResponse.getNickname() + "님, 복구되었습니다!",
                                        Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(RestoreDataActivity.this, MainActivity.class);
                                intent.putExtra("user_nickname", userResponse.getNickname());
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });

                        } else {
                            int errorCode = response != null ? response.code() : -1;
                            Log.e(TAG, "UUID 검증 실패 - 응답 코드: " + errorCode);

                            runOnUiThread(() -> {
                                String errorMsg;
                                if (errorCode == 404) {
                                    errorMsg = "존재하지 않는 복구 코드입니다";
                                } else if (errorCode == 400) {
                                    errorMsg = "잘못된 복구 코드 형식입니다";
                                } else {
                                    errorMsg = "복구에 실패했습니다. 다시 시도해주세요";
                                }
                                Toast.makeText(RestoreDataActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                btnRestore.setEnabled(true);
                                etUuid.requestFocus();
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<UserResponse> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);

                        runOnUiThread(() -> {
                            Toast.makeText(RestoreDataActivity.this,
                                    "서버 연결에 실패했습니다. 네트워크를 확인해주세요",
                                    Toast.LENGTH_LONG).show();
                            btnRestore.setEnabled(true);
                        });
                    }
                });
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