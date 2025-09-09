package com.sjoneon.cap;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * 개선된 알림 관련 기능을 처리하는 헬퍼 클래스
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    // 기본 알림 채널
    private static final String CHANNEL_ID = "daysync_channel";
    private static final String CHANNEL_NAME = "DaySync 알림";
    private static final String CHANNEL_DESCRIPTION = "DaySync 앱의 기본 알림입니다";

    // 일정 알림 채널 (중요도 높음)
    private static final String EVENT_CHANNEL_ID = "daysync_event_channel";
    private static final String EVENT_CHANNEL_NAME = "일정 알림";
    private static final String EVENT_CHANNEL_DESCRIPTION = "일정 관련 중요한 알림입니다";

    // 알람 알림 채널 (최고 중요도)
    private static final String ALARM_CHANNEL_ID = "daysync_alarm_channel";
    private static final String ALARM_CHANNEL_NAME = "알람";
    private static final String ALARM_CHANNEL_DESCRIPTION = "알람 관련 알림입니다";

    private Context context;
    private NotificationManagerCompat notificationManager;

    /**
     * 생성자
     * @param context 컨텍스트
     */
    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);

        // 알림 채널 생성 (Android 8.0 이상)
        createNotificationChannels();

        Log.d(TAG, "NotificationHelper 초기화 완료");
    }

    /**
     * 알림 채널 생성 (Android 8.0 이상) - 개선된 버전
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager == null) {
                Log.e(TAG, "NotificationManager를 가져올 수 없습니다");
                return;
            }

            try {
                // 1. 기본 알림 채널
                NotificationChannel defaultChannel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                defaultChannel.setDescription(CHANNEL_DESCRIPTION);
                defaultChannel.enableVibration(true);
                defaultChannel.setVibrationPattern(new long[]{0, 250, 250, 250});
                notificationManager.createNotificationChannel(defaultChannel);

                // 2. 일정 알림 채널 (중요도 높음)
                NotificationChannel eventChannel = new NotificationChannel(
                        EVENT_CHANNEL_ID,
                        EVENT_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                eventChannel.setDescription(EVENT_CHANNEL_DESCRIPTION);
                eventChannel.enableVibration(true);
                eventChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
                eventChannel.enableLights(true);
                eventChannel.setLightColor(0xFF0000FF); // 파란색

                // 기본 알림음 설정
                Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();
                eventChannel.setSound(defaultSoundUri, audioAttributes);

                notificationManager.createNotificationChannel(eventChannel);

                // 3. 알람 알림 채널 (최고 중요도)
                NotificationChannel alarmChannel = new NotificationChannel(
                        ALARM_CHANNEL_ID,
                        ALARM_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                alarmChannel.setDescription(ALARM_CHANNEL_DESCRIPTION);
                alarmChannel.enableVibration(true);
                alarmChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
                alarmChannel.enableLights(true);
                alarmChannel.setLightColor(0xFFFF0000); // 빨간색

                // 알람음 설정
                Uri alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmSoundUri == null) {
                    alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                alarmChannel.setSound(alarmSoundUri, audioAttributes);

                notificationManager.createNotificationChannel(alarmChannel);

                Log.d(TAG, "모든 알림 채널이 생성되었습니다");

            } catch (Exception e) {
                Log.e(TAG, "알림 채널 생성 중 오류 발생", e);
            }
        }
    }

    /**
     * 일반 알림 표시
     *
     * @param id 알림 ID
     * @param title 알림 제목
     * @param content 알림 내용
     */
    public void showNotification(int id, String title, String content) {
        showNotification(id, title, content, CHANNEL_ID);
    }

    /**
     * 일정 알림 표시 (중요도 높음)
     *
     * @param id 알림 ID
     * @param title 알림 제목
     * @param content 알림 내용
     */
    public void showEventNotification(int id, String title, String content) {
        showNotification(id, title, content, EVENT_CHANNEL_ID);
    }

    /**
     * 알람 알림 표시 (최고 중요도)
     *
     * @param id 알림 ID
     * @param title 알림 제목
     * @param content 알림 내용
     */
    public void showAlarmNotification(int id, String title, String content) {
        showNotification(id, title, content, ALARM_CHANNEL_ID);
    }

    /**
     * 알림 표시 (개선된 버전)
     *
     * @param id 알림 ID
     * @param title 알림 제목
     * @param content 알림 내용
     * @param channelId 채널 ID
     */
    private void showNotification(int id, String title, String content, String channelId) {
        Log.d(TAG, "알림 표시 시작 - ID: " + id + ", 제목: " + title + ", 채널: " + channelId);

        try {
            // 알림 클릭 시 실행할 인텐트 설정
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    id, // 고유한 request code
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 알림 빌더 설정 (개선된 버전)
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true);

            // 채널별 추가 설정
            if (EVENT_CHANNEL_ID.equals(channelId)) {
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            } else if (ALARM_CHANNEL_ID.equals(channelId)) {
                builder.setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setFullScreenIntent(pendingIntent, true); // 전체 화면 알림
            } else {
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            }

            // 긴 내용 처리
            if (content != null && content.length() > 50) {
                builder.setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content)
                        .setBigContentTitle(title));
            }

            // 알림 표시
            notificationManager.notify(id, builder.build());

            Log.d(TAG, "알림 표시 완료 - ID: " + id);

            // 알림 내역 저장
            saveNotification(id, title, content);

        } catch (SecurityException e) {
            Log.e(TAG, "알림 권한이 없습니다", e);
        } catch (Exception e) {
            Log.e(TAG, "알림 표시 중 오류 발생", e);
        }
    }

    /**
     * 알림 저장 (NotificationRepository 사용)
     */
    private void saveNotification(int id, String title, String content) {
        try {
            // 알림 저장소를 통해 알림 저장
            NotificationRepository.getInstance(context).addNotification(
                    new NotificationItem(id, title, content, System.currentTimeMillis(), false)
            );
            Log.d(TAG, "알림 저장 완료 - ID: " + id);
        } catch (Exception e) {
            Log.e(TAG, "알림 저장 중 오류 발생", e);
        }
    }

    /**
     * 특정 알림 취소
     * @param id 취소할 알림 ID
     */
    public void cancelNotification(int id) {
        try {
            notificationManager.cancel(id);
            Log.d(TAG, "알림 취소됨 - ID: " + id);
        } catch (Exception e) {
            Log.e(TAG, "알림 취소 중 오류 발생", e);
        }
    }

    /**
     * 모든 알림 취소
     */
    public void cancelAllNotifications() {
        try {
            notificationManager.cancelAll();
            Log.d(TAG, "모든 알림이 취소되었습니다");
        } catch (Exception e) {
            Log.e(TAG, "모든 알림 취소 중 오류 발생", e);
        }
    }

    /**
     * 알림 권한 상태 체크
     */
    public boolean areNotificationsEnabled() {
        return notificationManager.areNotificationsEnabled();
    }

    /**
     * 알림 상태 로깅
     */
    public void logNotificationStatus() {
        Log.d(TAG, "=== 알림 상태 체크 ===");
        Log.d(TAG, "알림 활성화: " + areNotificationsEnabled());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                Log.d(TAG, "기본 채널 중요도: " + nm.getNotificationChannel(CHANNEL_ID).getImportance());
                Log.d(TAG, "일정 채널 중요도: " + nm.getNotificationChannel(EVENT_CHANNEL_ID).getImportance());
                Log.d(TAG, "알람 채널 중요도: " + nm.getNotificationChannel(ALARM_CHANNEL_ID).getImportance());
            }
        }
    }
}