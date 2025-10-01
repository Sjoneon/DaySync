package com.sjoneon.cap.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sjoneon.cap.activities.AlarmActivity;
import com.sjoneon.cap.helpers.NotificationHelper;

/**
 * 알람이 울릴 때 처리하는 BroadcastReceiver
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    public static final String EXTRA_ALARM_ID = "ALARM_ID";
    public static final String EXTRA_ALARM_LABEL = "ALARM_LABEL";
    public static final String EXTRA_ALARM_SOUND_ENABLED = "ALARM_SOUND_ENABLED";
    public static final String EXTRA_ALARM_VIBRATION_ENABLED = "ALARM_VIBRATION_ENABLED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "알람 수신됨");

        // 인텐트에서 알람 정보 추출
        int alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1);
        String alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL);
        boolean soundEnabled = intent.getBooleanExtra(EXTRA_ALARM_SOUND_ENABLED, true);
        boolean vibrationEnabled = intent.getBooleanExtra(EXTRA_ALARM_VIBRATION_ENABLED, true);

        Log.d(TAG, "알람 ID: " + alarmId + ", 라벨: " + alarmLabel);

        // AlarmActivity 시작
        Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
        alarmActivityIntent.putExtra(EXTRA_ALARM_ID, alarmId);
        alarmActivityIntent.putExtra(EXTRA_ALARM_LABEL, alarmLabel);
        alarmActivityIntent.putExtra(EXTRA_ALARM_SOUND_ENABLED, soundEnabled);
        alarmActivityIntent.putExtra(EXTRA_ALARM_VIBRATION_ENABLED, vibrationEnabled);
        alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        context.startActivity(alarmActivityIntent);

        // 알림도 함께 표시 (백그라운드에서 알람이 울릴 경우를 대비)
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.showNotification(
                alarmId,
                "알람",
                alarmLabel != null ? alarmLabel : "알람이 울렸습니다"
        );
    }
}