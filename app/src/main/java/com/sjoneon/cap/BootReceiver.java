package com.sjoneon.cap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

/**
 * 부팅 완료 후 알람을 복원하는 BroadcastReceiver
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "부팅 완료 감지, 알람 복원 시작");

            try {
                // 일정 알람 복원
                restoreEventAlarms(context);

                Log.d(TAG, "알람 복원 완료");

            } catch (Exception e) {
                Log.e(TAG, "알람 복원 중 오류 발생", e);
            }
        }
    }

    /**
     * 일정 알람 복원
     */
    private void restoreEventAlarms(Context context) {
        try {
            CalendarEventRepository repository = CalendarEventRepository.getInstance(context);
            EventAlarmManager alarmManager = new EventAlarmManager(context);

            List<CalendarEvent> allEvents = repository.getAllEvents();

            int restoredCount = 0;
            long currentTime = System.currentTimeMillis();

            for (CalendarEvent event : allEvents) {
                // 아직 지나지 않은 일정만 복원
                if (event.getDateTime() > currentTime) {
                    alarmManager.scheduleEventNotifications(event);
                    restoredCount++;
                }
            }

            Log.d(TAG, "총 " + restoredCount + "개의 일정 알람이 복원되었습니다");

            // 복원 완료 알림 (선택사항)
            if (restoredCount > 0) {
                NotificationHelper notificationHelper = new NotificationHelper(context);
                notificationHelper.showNotification(
                        999999,
                        "DaySync 알람 복원",
                        restoredCount + "개의 일정 알람이 복원되었습니다."
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "일정 알람 복원 실패", e);
        }
    }
}