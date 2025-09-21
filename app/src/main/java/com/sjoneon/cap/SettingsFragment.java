package com.sjoneon.cap;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import static android.content.Context.MODE_PRIVATE;

import java.util.UUID;
/**
 * 개인설정을 관리하는 프래그먼트
 */
public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    // UI 요소들
    private ImageView imageProfile;
    private TextView textUserName;
    private TextView textUserNameSubtitle;
    private LinearLayout layoutNicknameEdit;
    private LinearLayout layoutUuidView;
    private LinearLayout layoutLocationSetting;
    private Switch switchPushAlarm;
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
        layoutUuidView = view.findViewById(R.id.layoutUuidView);
        layoutLocationSetting = view.findViewById(R.id.layoutLocationSetting);
        switchPushAlarm = view.findViewById(R.id.switchPushAlarm);
        layoutAbout = view.findViewById(R.id.layoutAbout);
        layoutHelp = view.findViewById(R.id.layoutHelp);

        // 서브타이틀 찾기 (layoutNicknameEdit 안의 두 번째 TextView)
        textUserNameSubtitle = layoutNicknameEdit.findViewById(R.id.textUserNameSubtitle);
    }

    /**
     * 클릭 리스너들을 설정하는 메서드
     */
    private void setupClickListeners() {
        // 닉네임 편집 클릭
        layoutNicknameEdit.setOnClickListener(v -> showNicknameEditDialog());

        // UUID 보기 클릭
        layoutUuidView.setOnClickListener(v -> showUuidDialog());

        // 위치 설정 클릭
        layoutLocationSetting.setOnClickListener(v -> showLocationSettingsDialog());

        // 푸시 알림 스위치
        switchPushAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
                // 스위치를 다시 끄고 권한 요청
                switchPushAlarm.setChecked(false);
                showNotificationPermissionDialog();
            } else {
                savePushAlarmSetting(isChecked);
            }
        });

        // 앱 정보 클릭
        layoutAbout.setOnClickListener(v -> showAboutDialog());

        // 도움말 클릭
        layoutHelp.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showFragment(new HelpFragment());
                ((MainActivity) getActivity()).setToolbarTitle("도움말");
            }
        });
    }

    /**
     * UUID 다이얼로그를 표시하는 메서드
     */
    private void showUuidDialog() {
        // UUID가 없으면 생성
        ensureUuidExists();

        String userUuid = preferences.getString("user_uuid", "");

        if (userUuid.isEmpty()) {
            Toast.makeText(getContext(), "UUID 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);

        String message = getString(R.string.uuid_dialog_message) + "\n\n" + userUuid;

        builder.setTitle(getString(R.string.uuid_dialog_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.copy_uuid), (dialog, id) -> {
                    // UUID를 클립보드에 복사
                    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("UUID", userUuid);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getContext(), getString(R.string.uuid_copied), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.confirm), (dialog, id) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * UUID가 없으면 생성하는 메서드
     */
    private void ensureUuidExists() {
        String existingUuid = preferences.getString("user_uuid", null);
        if (existingUuid == null) {
            String newUuid = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("user_uuid", newUuid);
            editor.apply();
            Log.d(TAG, "기존 사용자용 UUID 생성: " + newUuid);
        }
    }

    /**
     * 저장된 설정을 로드하는 메서드
     */
    private void loadSettings() {
        // 사용자 닉네임 로드 (기본값을 "사용자"로 변경)
        currentNickname = preferences.getString("nickname", "사용자");
        textUserName.setText(currentNickname);

        // 서브타이틀도 업데이트
        if (textUserNameSubtitle != null) {
            textUserNameSubtitle.setText(currentNickname);
        }

        // 푸시 알림 설정 로드 및 실제 권한 상태 확인
        loadPushAlarmSettings();
    }

    /**
     * 푸시 알림 설정을 로드하고 실제 권한 상태를 확인하는 메서드
     */
    private void loadPushAlarmSettings() {
        boolean pushAlarmEnabled = preferences.getBoolean("push_alarm_enabled", true);

        // 실제 시스템 알림 권한 상태 확인
        boolean hasNotificationPermission = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();

        // 둘 다 활성화되어야 실제로 켜진 것으로 표시
        boolean actuallyEnabled = pushAlarmEnabled && hasNotificationPermission;

        switchPushAlarm.setChecked(actuallyEnabled);

        Log.d(TAG, "푸시 알림 설정 - 앱 설정: " + pushAlarmEnabled + ", 시스템 권한: " + hasNotificationPermission);
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
     * 위치 설정 다이얼로그를 표시하는 메서드
     */
    private void showLocationSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        builder.setTitle("위치 권한 설정")
                .setMessage("DaySync는 위치 기반 서비스를 제공합니다.\n\n정확한 위치 정보 제공을 위해 위치 권한을 허용해 주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    // 앱 설정 화면으로 이동
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 알림 권한 설정 다이얼로그를 표시하는 메서드
     */
    private void showNotificationPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        builder.setTitle("알림 권한 필요")
                .setMessage("푸시 알림을 받으려면 알림 권한이 필요합니다.\n\n앱 설정에서 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    // 앱 알림 설정 화면으로 이동
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireActivity().getPackageName());
                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
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
                "© 2025 DaySync";

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

        // 서브타이틀도 업데이트
        if (textUserNameSubtitle != null) {
            textUserNameSubtitle.setText(newNickname);
        }

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

        Log.d(TAG, "푸시 알림 설정 저장: " + enabled);
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