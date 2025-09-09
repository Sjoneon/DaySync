package com.sjoneon.cap;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 개선된 일정 알림 관리 클래스
 * - 권한 체크 강화
 * - 상세 로깅 추가
 * - 알림 표시 개선
 */
public class EventAlarmManager {
    private static final String TAG = "EventAlarmManager";

    private Context context;
    private AlarmManager alarmManager;

    public EventAlarmManager(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * 알림 권한 체크
     */
    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * 정확한 알람 권한 체크
     */
    private boolean checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true; // Android 12 미만에서는 별도 권한 불필요
    }

    /**
     * 일정의 모든 알림 설정
     */
    public void scheduleEventNotifications(CalendarEvent event) {
        Log.d(TAG, "일정 알림 설정 시작: " + event.getTitle());

        // 권한 체크
        if (!checkNotificationPermission()) {
            Log.e(TAG, "알림 권한이 없습니다.");
            return;
        }

        if (!checkExactAlarmPermission()) {
            Log.e(TAG, "정확한 알람 권한이 없습니다.");
            return;
        }

        if (event.getNotificationSettings() == null || event.getNotificationSettings().isEmpty()) {
            Log.d(TAG, "설정된 알림이 없습니다.");
            return;
        }

        int successCount = 0;
        for (CalendarEvent.NotificationSetting setting : event.getNotificationSettings()) {
            if (setting.isEnabled()) {
                boolean success = scheduleNotification(event, setting);
                if (success) {
                    successCount++;
                }
            }
        }

        Log.d(TAG, "총 " + successCount + "개의 알림이 설정되었습니다.");
    }

    /**
     * 개별 알림 스케줄링 (개선된 버전)
     */
    private boolean scheduleNotification(CalendarEvent event, CalendarEvent.NotificationSetting setting) {
        long notificationTime = setting.getNotificationTime(event.getDateTime());
        long currentTime = System.currentTimeMillis();

        // 알림 시간이 이미 지났으면 스케줄링하지 않음
        if (notificationTime <= currentTime) {
            Log.w(TAG, "알림 시간이 이미 지나서 스케줄링을 건너뜁니다: " +
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(notificationTime)));
            return false;
        }

        Log.d(TAG, "알림 스케줄링: " + event.getTitle() + " - " + setting.getType().getDisplayName());
        Log.d(TAG, "알림 시간: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(notificationTime)));
        Log.d(TAG, "현재 시간: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(currentTime)));

        try {
            Intent intent = new Intent(context, EventNotificationReceiver.class);
            intent.putExtra("event_id", event.getId());
            intent.putExtra("event_title", event.getTitle());
            intent.putExtra("event_description", event.getDescription());
            intent.putExtra("notification_type", setting.getType().getDisplayName());
            intent.putExtra("event_time", event.getDateTime());

            // 고유한 request code 생성
            int requestCode = (int) (event.getId() * 1000 + setting.getType().ordinal());

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 알람 설정
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        notificationTime,
                        pendingIntent
                );
            }

            Log.d(TAG, "알림 스케줄링 성공: " + event.getTitle() + " - " +
                    setting.getType().getDisplayName() + " (RequestCode: " + requestCode + ")");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "알림 스케줄링 실패: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 일정의 모든 알림 취소
     */
    public void cancelEventNotifications(CalendarEvent event) {
        Log.d(TAG, "일정 알림 취소: " + event.getTitle());

        if (event.getNotificationSettings() == null) {
            return;
        }

        int cancelCount = 0;
        for (CalendarEvent.NotificationSetting setting : event.getNotificationSettings()) {
            try {
                Intent intent = new Intent(context, EventNotificationReceiver.class);
                int requestCode = (int) (event.getId() * 1000 + setting.getType().ordinal());

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
                cancelCount++;

                Log.d(TAG, "알림 취소됨: " + setting.getType().getDisplayName() + " (RequestCode: " + requestCode + ")");
            } catch (Exception e) {
                Log.e(TAG, "알림 취소 실패: " + e.getMessage(), e);
            }
        }

        Log.d(TAG, "총 " + cancelCount + "개의 알림이 취소되었습니다.");
    }

    /**
     * 권한 상태 체크 및 로깅
     */
    public void logPermissionStatus() {
        Log.d(TAG, "=== 권한 상태 체크 ===");
        Log.d(TAG, "알림 권한: " + (checkNotificationPermission() ? "허용" : "거부"));
        Log.d(TAG, "정확한 알람 권한: " + (checkExactAlarmPermission() ? "허용" : "거부"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "Android 12+ 정확한 알람 권한 필요");
        }

        Log.d(TAG, "알림 채널 활성화: " + NotificationManagerCompat.from(context).areNotificationsEnabled());
    }

    /**
     * 개선된 알림 수신자 (알림 표시 부분 수정됨)
     */
    public static class EventNotificationReceiver extends BroadcastReceiver {
        private static final String TAG = "EventNotificationReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "일정 알림 수신됨");

            try {
                long eventId = intent.getLongExtra("event_id", 0);
                String eventTitle = intent.getStringExtra("event_title");
                String eventDescription = intent.getStringExtra("event_description");
                String notificationType = intent.getStringExtra("notification_type");
                long eventTime = intent.getLongExtra("event_time", 0);

                Log.d(TAG, "알림 정보:");
                Log.d(TAG, "- 이벤트 ID: " + eventId);
                Log.d(TAG, "- 제목: " + eventTitle);
                Log.d(TAG, "- 알림 타입: " + notificationType);
                Log.d(TAG, "- 이벤트 시간: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(eventTime)));

                // 알림 제목과 내용 구성
                String title = notificationType + " - " + eventTitle;
                String content = eventDescription;

                if (eventTime > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MM월 dd일 HH:mm", Locale.getDefault());
                    content += "\n일정 시간: " + sdf.format(new Date(eventTime));
                }

                // 일정 알림 표시 (중요도 높음으로 수정됨)
                NotificationHelper notificationHelper = new NotificationHelper(context);
                notificationHelper.showEventNotification((int) eventId, title, content);

                Log.d(TAG, "일정 알림 발송 완료: " + eventTitle + " (" + notificationType + ")");

            } catch (Exception e) {
                Log.e(TAG, "일정 알림 처리 중 오류 발생", e);
            }
        }
    }
}