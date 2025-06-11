package com.sjoneon.cap;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static android.content.Context.MODE_PRIVATE;

/**
 * 개인설정을 관리하는 프래그먼트
 */
public class SettingsFragment extends Fragment {

    // UI 요소들
    private ImageView imageProfile;
    private TextView textUserName;
    private LinearLayout layoutNicknameEdit;
    private LinearLayout layoutLocationSetting;
    private Switch switchPushAlarm;
    private Switch switchLocationService;
    private LinearLayout layoutAbout;
    private LinearLayout layoutHelp;

    // 설정 데이터
    private SharedPreferences preferences;
    private String currentNickname;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // SharedPreferences 초기화
        preferences = requireActivity().getSharedPreferences("UserPrefs", MODE_PRIVATE);

        // 뷰 초기화
        initializeViews(view);

        // 설정 로드
        loadSettings();

        // 클릭 리스너 설정
        setupClickListeners();

        return view;
    }

    /**
     * 뷰 요소들을 초기화하는 메서드
     */
    private void initializeViews(View view) {
        imageProfile = view.findViewById(R.id.imageProfile);
        textUserName = view.findViewById(R.id.textUserName);
        layoutNicknameEdit = view.findViewById(R.id.layoutNicknameEdit);
        layoutLocationSetting = view.findViewById(R.id.layoutLocationSetting);
        switchPushAlarm = view.findViewById(R.id.switchPushAlarm);
        switchLocationService = view.findViewById(R.id.switchLocationService);
        layoutAbout = view.findViewById(R.id.layoutAbout);
        layoutHelp = view.findViewById(R.id.layoutHelp);
    }

    /**
     * 저장된 설정을 로드하는 메서드
     */
    private void loadSettings() {
        // 사용자 닉네임 로드
        currentNickname = preferences.getString("nickname", "나는북극곰");
        textUserName.setText(currentNickname);

        // 푸시 알림 설정 로드
        boolean pushAlarmEnabled = preferences.getBoolean("push_alarm_enabled", true);
        switchPushAlarm.setChecked(pushAlarmEnabled);

        // 위치 서비스 설정 로드
        boolean locationServiceEnabled = preferences.getBoolean("location_service_enabled", true);
        switchLocationService.setChecked(locationServiceEnabled);
    }

    /**
     * 클릭 리스너들을 설정하는 메서드
     */
    private void setupClickListeners() {
        // 닉네임 편집 클릭
        layoutNicknameEdit.setOnClickListener(v -> showNicknameEditDialog());

        // 위치 설정 클릭
        layoutLocationSetting.setOnClickListener(v -> {
            // 위치 설정 화면으로 이동 (추후 구현)
            Toast.makeText(getContext(), "위치 설정 기능 준비 중...", Toast.LENGTH_SHORT).show();
        });

        // 푸시 알림 스위치
        switchPushAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePushAlarmSetting(isChecked);
            String message = isChecked ? "푸시 알림이 켜졌습니다" : "푸시 알림이 꺼졌습니다";
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 위치 서비스 스위치
        switchLocationService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveLocationServiceSetting(isChecked);
            String message = isChecked ? "위치 서비스가 켜졌습니다" : "위치 서비스가 꺼졌습니다";
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        });

        // 앱 정보 클릭
        layoutAbout.setOnClickListener(v -> showAboutDialog());

        // 도움말 클릭
        layoutHelp.setOnClickListener(v -> {
            Toast.makeText(getContext(), "도움말 기능 준비 중...", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 닉네임 편집 다이얼로그를 표시하는 메서드
     */
    private void showNicknameEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_nickname_edit, null);

        EditText editNewNickname = dialogView.findViewById(R.id.editNewNickname);
        editNewNickname.setText(currentNickname);
        editNewNickname.setSelection(currentNickname.length()); // 커서를 끝으로 이동

        builder.setView(dialogView)
                .setTitle("닉네임 변경")
                .setPositiveButton("변경", (dialog, id) -> {
                    String newNickname = editNewNickname.getText().toString().trim();

                    if (newNickname.isEmpty()) {
                        Toast.makeText(getContext(), "닉네임을 입력해주세요", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (newNickname.length() > 20) {
                        Toast.makeText(getContext(), "닉네임은 20자 이하로 입력해주세요", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 닉네임 저장
                    saveNickname(newNickname);
                })
                .setNegativeButton("취소", (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 앱 정보 다이얼로그를 표시하는 메서드
     */
    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);

        String aboutMessage = "DaySync v1.0\n\n" +
                "버스 기반 일정-이동 최적화 비서\n\n" +
                "개발자: 송재원\n" +
                "학과: 컴퓨터공학과\n" +
                "학번: 202010788\n\n" +
                "© 2025 DaySync Team";

        builder.setTitle("앱 정보")
                .setMessage(aboutMessage)
                .setPositiveButton("확인", (dialog, id) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 닉네임을 저장하는 메서드
     */
    private void saveNickname(String newNickname) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("nickname", newNickname);
        editor.apply();

        currentNickname = newNickname;
        textUserName.setText(newNickname);

        Toast.makeText(getContext(), "닉네임이 변경되었습니다", Toast.LENGTH_SHORT).show();

        // MainActivity의 네비게이션 헤더도 업데이트하도록 알림
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateNavigationHeader();
        }
    }

    /**
     * 푸시 알림 설정을 저장하는 메서드
     */
    private void savePushAlarmSetting(boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("push_alarm_enabled", enabled);
        editor.apply();
    }

    /**
     * 위치 서비스 설정을 저장하는 메서드
     */
    private void saveLocationServiceSetting(boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("location_service_enabled", enabled);
        editor.apply();
    }

    /**
     * 프래그먼트가 보여질 때 호출되는 메서드
     * 설정이 변경되었을 수 있으므로 다시 로드
     */
    @Override
    public void onResume() {
        super.onResume();
        loadSettings();
    }
}