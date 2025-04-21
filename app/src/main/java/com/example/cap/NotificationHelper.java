package com.example.cap;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * 알림 관련 기능을 처리하는 헬퍼 클래스
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "daysync_channel";
    private static final String CHANNEL_NAME = "DaySync 알림";
    private static final String CHANNEL_DESCRIPTION = "DaySync 앱의 알림입니다";

    private Context context;
    private NotificationManagerCompat notificationManager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);

        // 알림 채널 생성 (Android 8.0 이상)
        createNotificationChannel();
    }

    /**
     * 알림 채널 생성 (Android 8.0 이상)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESCRIPTION);

            // 시스템에 채널 등록
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 알림 표시
     *
     * @param id 알림 ID
     * @param title 알림 제목
     * @param content 알림 내용
     */
    public void showNotification(int id, String title, String content) {
        // 알림 클릭 시 실행할 인텐트 설정
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 알림 빌더 설정
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // 알림 표시
        notificationManager.notify(id, builder.build());

        // 알림 내역 저장 (실제로는 데이터베이스에 저장)
        saveNotification(id, title, content);
    }

    /**
     * 알림 저장 (실제로는 데이터베이스에 저장)
     */
    private void saveNotification(int id, String title, String content) {
        // 실제 구현에서는 여기서 데이터베이스에 알림 내역 저장
        // 지금은 임시로 SharedPreferences 사용
        NotificationRepository.getInstance(context).addNotification(
                new NotificationItem(id, title, content, System.currentTimeMillis(), false)
        );
    }
}