package com.sjoneon.cap;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 일정 알림 관리 클래스
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
     * 일정의 모든 알림 설정
     */
    public void scheduleEventNotifications(CalendarEvent event) {
        if (event.getNotificationSettings() == null || event.getNotificationSettings().isEmpty()) {
            return;
        }

        for (CalendarEvent.NotificationSetting setting : event.getNotificationSettings()) {
            if (setting.isEnabled()) {
                scheduleNotification(event, setting);
            }
        }
    }

    /**
     * 개별 알림 스케줄링
     */
    private void scheduleNotification(CalendarEvent event, CalendarEvent.NotificationSetting setting) {
        long notificationTime = setting.getNotificationTime(event.getDateTime());

        // 알림 시간이 이미 지났으면 스케줄링하지 않음
        if (notificationTime <= System.currentTimeMillis()) {
            Log.d(TAG, "알림 시간이 이미 지나서 스케줄링을 건너뜁니다.");
            return;
        }

        Intent intent = new Intent(context, EventNotificationReceiver.class);
        intent.putExtra("event_id", event.getId());
        intent.putExtra("event_title", event.getTitle());
        intent.putExtra("event_description", event.getDescription());
        intent.putExtra("notification_type", setting.getType().getDisplayName());
        intent.putExtra("event_time", event.getDateTime());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                setting.getRequestCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
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

            Log.d(TAG, "알림 스케줄링 완료: " + event.getTitle() + " - " +
                    setting.getType().getDisplayName());

        } catch (Exception e) {
            Log.e(TAG, "알림 스케줄링 실패: " + e.getMessage());
        }
    }

    /**
     * 일정의 모든 알림 취소
     */
    public void cancelEventNotifications(CalendarEvent event) {
        if (event.getNotificationSettings() == null) {
            return;
        }

        for (CalendarEvent.NotificationSetting setting : event.getNotificationSettings()) {
            Intent intent = new Intent(context, EventNotificationReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    setting.getRequestCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * 알림 수신자
     */
    public static class EventNotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long eventId = intent.getLongExtra("event_id", 0);
            String eventTitle = intent.getStringExtra("event_title");
            String eventDescription = intent.getStringExtra("event_description");
            String notificationType = intent.getStringExtra("notification_type");
            long eventTime = intent.getLongExtra("event_time", 0);

            // 알림 제목과 내용 구성
            String title = notificationType + " - " + eventTitle;
            String content = eventDescription;

            if (eventTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM월 dd일 HH:mm", Locale.getDefault());
                content += "\n일정 시간: " + sdf.format(new Date(eventTime));
            }

            // 알림 표시
            NotificationHelper notificationHelper = new NotificationHelper(context);
            notificationHelper.showNotification((int) eventId, title, content);

            Log.d("EventNotificationReceiver",
                    "일정 알림 발송: " + eventTitle + " (" + notificationType + ")");
        }
    }
}