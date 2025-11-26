package com.sjoneon.cap.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.sjoneon.cap.R;
import com.sjoneon.cap.activities.AlarmActivity;
import com.sjoneon.cap.receivers.AlarmReceiver;

/**
 * 알람을 처리하는 Foreground Service
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_service_channel";
    private static final int NOTIFICATION_ID = 9999;

    public static final String ACTION_START_ALARM = "START_ALARM";
    public static final String ACTION_STOP_ALARM = "STOP_ALARM";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AlarmService onCreate");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AlarmService onStartCommand");

        if (intent == null) {
            Log.e(TAG, "Intent is null");
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "Action: " + action);

        if (ACTION_START_ALARM.equals(action)) {
            int alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
            String alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL);
            boolean soundEnabled = intent.getBooleanExtra(AlarmReceiver.EXTRA_ALARM_SOUND_ENABLED, true);
            boolean vibrationEnabled = intent.getBooleanExtra(AlarmReceiver.EXTRA_ALARM_VIBRATION_ENABLED, true);

            Log.d(TAG, "알람 서비스 시작 - ID: " + alarmId + ", 라벨: " + alarmLabel);

            // Foreground Service 먼저 시작
            try {
                startForeground(NOTIFICATION_ID, createNotification(alarmLabel));
                Log.d(TAG, "Foreground 시작 완료");
            } catch (Exception e) {
                Log.e(TAG, "Foreground 시작 실패", e);
            }

            // 약간의 지연 후 Activity 시작
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent alarmActivityIntent = new Intent(this, AlarmActivity.class);
                    alarmActivityIntent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
                    alarmActivityIntent.putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarmLabel);
                    alarmActivityIntent.putExtra(AlarmReceiver.EXTRA_ALARM_SOUND_ENABLED, soundEnabled);
                    alarmActivityIntent.putExtra(AlarmReceiver.EXTRA_ALARM_VIBRATION_ENABLED, vibrationEnabled);
                    alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    startActivity(alarmActivityIntent);
                    Log.d(TAG, "AlarmActivity 시작 완료");
                } catch (Exception e) {
                    Log.e(TAG, "AlarmActivity 시작 실패", e);
                }
            }, 100);

        } else if (ACTION_STOP_ALARM.equals(action)) {
            Log.d(TAG, "알람 서비스 중지");
            stopForeground(true);
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "알람 서비스",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("알람 실행 중");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "알림 채널 생성 완료");
            }
        }
    }

    private Notification createNotification(String label) {
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP_ALARM);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("알람")
                .setContentText(label != null ? label : "알람이 울리고 있습니다")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "끄기", stopPendingIntent)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}