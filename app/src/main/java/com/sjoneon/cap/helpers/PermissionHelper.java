package com.sjoneon.cap.helpers;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sjoneon.cap.R;

/**
 * 알림 및 알람 권한 관리 헬퍼 클래스
 */
public class PermissionHelper {

    private static final String TAG = "PermissionHelper";
    public static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    public static final int REQUEST_EXACT_ALARM_PERMISSION = 1002;

    /**
     * 알림 권한 체크
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * 정확한 알람 권한 체크
     */
    public static boolean hasExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true; // Android 12 미만에서는 별도 권한 불필요
    }

    /**
     * 알림 권한 요청
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        } else {
            // Android 13 미만에서는 설정 화면으로 이동
            showNotificationSettingsDialog(activity);
        }
    }

    /**
     * 정확한 알람 권한 요청
     */
    public static void requestExactAlarmPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasExactAlarmPermission(activity)) {
                showExactAlarmPermissionDialog(activity);
            }
        }
    }

    /**
     * 알림 설정 다이얼로그 표시
     */
    private static void showNotificationSettingsDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.DialogTheme)
                .setTitle("알림 권한 필요")
                .setMessage("일정 알림을 받으려면 알림 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                    } else {
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    }
                    activity.startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 정확한 알람 권한 다이얼로그 표시
     */
    private static void showExactAlarmPermissionDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.DialogTheme)
                .setTitle("알람 권한 필요")
                .setMessage("정확한 시간에 알림을 받으려면 '정확한 알람' 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, REQUEST_EXACT_ALARM_PERMISSION);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 모든 권한 체크 및 요청
     */
    public static void checkAndRequestAllPermissions(Activity activity) {
        Log.d(TAG, "모든 권한 체크 시작");

        boolean allPermissionsGranted = true;

        // 알림 권한 체크
        if (!hasNotificationPermission(activity)) {
            Log.d(TAG, "알림 권한이 없습니다");
            allPermissionsGranted = false;
        }

        // 정확한 알람 권한 체크
        if (!hasExactAlarmPermission(activity)) {
            Log.d(TAG, "정확한 알람 권한이 없습니다");
            allPermissionsGranted = false;
        }

        if (!allPermissionsGranted) {
            showPermissionExplanationDialog(activity);
        } else {
            Log.d(TAG, "모든 권한이 허용되어 있습니다");
        }
    }

    /**
     * 권한 설명 다이얼로그
     */
    private static void showPermissionExplanationDialog(Activity activity) {
        new AlertDialog.Builder(activity, R.style.DialogTheme)
                .setTitle("권한 설정이 필요합니다")
                .setMessage("일정 알림 기능을 사용하려면 다음 권한들이 필요합니다:\n\n" +
                        "• 알림 권한: 알림을 표시하기 위해 필요\n" +
                        "• 정확한 알람 권한: 정확한 시간에 알림을 받기 위해 필요")
                .setPositiveButton("권한 설정", (dialog, which) -> {
                    // 순차적으로 권한 요청
                    if (!hasNotificationPermission(activity)) {
                        requestNotificationPermission(activity);
                    } else if (!hasExactAlarmPermission(activity)) {
                        requestExactAlarmPermission(activity);
                    }
                })
                .setNegativeButton("나중에", null)
                .show();
    }

    /**
     * 권한 상태 로깅
     */
    public static void logPermissionStatus(Context context) {
        Log.d(TAG, "=== 권한 상태 확인 ===");
        Log.d(TAG, "알림 권한: " + (hasNotificationPermission(context) ? "허용" : "거부"));
        Log.d(TAG, "정확한 알람 권한: " + (hasExactAlarmPermission(context) ? "허용" : "거부"));
        Log.d(TAG, "Android 버전: " + Build.VERSION.SDK_INT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Android 13+ - POST_NOTIFICATIONS 권한 확인 필요");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "Android 12+ - SCHEDULE_EXACT_ALARM 권한 확인 필요");
        }
    }
}