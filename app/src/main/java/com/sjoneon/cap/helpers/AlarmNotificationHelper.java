package com.sjoneon.cap.helpers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.sjoneon.cap.R;

/**
 * 알람 전용 알림 및 소리/진동 처리 헬퍼 클래스
 */
public class AlarmNotificationHelper {

    private static final String TAG = "AlarmNotificationHelper";
    private static final String ALARM_CHANNEL_ID = "daysync_alarm_channel";
    private static final String ALARM_CHANNEL_NAME = "알람";
    private static final String ALARM_CHANNEL_DESCRIPTION = "알람 알림";

    private Context context;
    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;

    public AlarmNotificationHelper(Context context) {
        this.context = context;
        createAlarmChannel();
    }

    private void createAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);

            if (notificationManager == null) return;

            // 기존 채널이 있으면 삭제 후 재생성 (설정 변경 적용)
            NotificationChannel existingChannel =
                    notificationManager.getNotificationChannel(ALARM_CHANNEL_ID);

            if (existingChannel == null) {
                NotificationChannel channel = new NotificationChannel(
                        ALARM_CHANNEL_ID,
                        ALARM_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(ALARM_CHANNEL_DESCRIPTION);
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
                channel.enableLights(true);
                channel.setLightColor(0xFFFF0000);
                channel.setBypassDnd(true);
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(alarmSound, audioAttributes);

                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "알람 채널 생성 완료");
            }
        }
    }

    /**
     * 알람 알림 표시 및 소리/진동 재생
     */
    public void showAlarmNotification(int alarmId, String label, boolean soundEnabled,
                                      boolean vibrationEnabled, PendingIntent fullScreenIntent) {
        Log.d(TAG, "알람 알림 표시 - ID: " + alarmId + ", 소리: " + soundEnabled + ", 진동: " + vibrationEnabled);

        // 소리 재생
        if (soundEnabled) {
            playAlarmSound();
        }

        // 진동 시작
        if (vibrationEnabled) {
            startVibration();
        }

        // 알림 생성
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("알람")
                .setContentText(label)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenIntent, true);

        // 소리/진동을 직접 제어하므로 알림 자체의 기본값은 끔
        builder.setDefaults(0);

        try {
            NotificationManagerCompat.from(context).notify(alarmId, builder.build());
            Log.d(TAG, "알람 알림 표시 완료");
        } catch (SecurityException e) {
            Log.e(TAG, "알림 권한 없음", e);
        }
    }

    /**
     * 알람 소리 재생
     */
    private void playAlarmSound() {
        try {
            stopAlarmSound();

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, alarmUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            Log.d(TAG, "알람 소리 재생 시작");
        } catch (Exception e) {
            Log.e(TAG, "알람 소리 재생 실패", e);
        }
    }

    /**
     * 진동 시작
     */
    private void startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager =
                        (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 1000, 500, 1000, 500};

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }

                Log.d(TAG, "진동 시작");
            }
        } catch (Exception e) {
            Log.e(TAG, "진동 시작 실패", e);
        }
    }

    /**
     * 알람 소리 중지
     */
    public static void stopAlarmSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "알람 소리 중지");
            } catch (Exception e) {
                Log.e(TAG, "알람 소리 중지 실패", e);
            }
        }
    }

    /**
     * 진동 중지
     */
    public static void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
                vibrator = null;
                Log.d(TAG, "진동 중지");
            } catch (Exception e) {
                Log.e(TAG, "진동 중지 실패", e);
            }
        }
    }

    /**
     * 알람 소리와 진동 모두 중지
     */
    public static void stopAll() {
        stopAlarmSound();
        stopVibration();
    }
}