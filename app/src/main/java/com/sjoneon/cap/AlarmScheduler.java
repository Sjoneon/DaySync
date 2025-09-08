package com.sjoneon.cap;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * 알람 스케줄링을 관리하는 헬퍼 클래스 (수정됨)
 */
public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";
    private static final String ALARM_PREFS = "alarm_preferences";

    /**
     * 새로운 알람을 스케줄링합니다
     * 
     * @param context 컨텍스트
     * @param alarmId 알람 ID
     * @param hourOfDay 시간 (24시간 형식)
     * @param minute 분
     * @param label 알람 라벨
     * @return 스케줄링 성공 여부
     */
    public static boolean scheduleAlarm(Context context, int alarmId, int hourOfDay, int minute, String label) {
        if (context == null) {
            Log.e(TAG, "Context가 null입니다");
            return false;
        }

        // 입력값 검증
        if (hourOfDay < 0 || hourOfDay > 23 || minute < 0 || minute > 59) {
            Log.e(TAG, "유효하지 않은 시간: " + hourOfDay + ":" + minute);
            return false;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager를 가져올 수 없습니다");
            return false;
        }

        // Android 12 이상에서는 정확한 알람 스케줄링 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "정확한 알람 스케줄링 권한이 없습니다");
                return false;
            }
        }

        // 알람 시간 설정 (시간대 고려)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // 설정된 시간이 현재 시간보다 이전이면 다음 날로 설정
        long currentTime = System.currentTimeMillis();
        if (calendar.getTimeInMillis() <= currentTime) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 사용자 설정 가져오기 (안전하게)
        boolean soundEnabled = true;
        boolean vibrationEnabled = true;
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
            soundEnabled = prefs.getBoolean("sound_enabled_" + alarmId, true);
            vibrationEnabled = prefs.getBoolean("vibration_enabled_" + alarmId, true);
        } catch (Exception e) {
            Log.w(TAG, "설정 로드 실패, 기본값 사용", e);
        }

        // Intent 생성
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label != null ? label : "알람");
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_SOUND_ENABLED, soundEnabled);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_VIBRATION_ENABLED, vibrationEnabled);

        PendingIntent pendingIntent;
        try {
            pendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarmId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } catch (Exception e) {
            Log.e(TAG, "PendingIntent 생성 실패", e);
            return false;
        }

        try {
            // 정확한 알람 설정
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }

            Log.d(TAG, "알람 스케줄링 성공 - ID: " + alarmId + ", 시간: " + 
                       hourOfDay + ":" + String.format("%02d", minute) + 
                       ", 라벨: " + label);
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "알람 스케줄링 권한 부족", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "알람 스케줄링 실패", e);
            return false;
        }
    }

    /**
     * 다시 울림(스누즈) 알람을 스케줄링합니다
     * 
     * @param context 컨텍스트
     * @param alarmId 알람 ID
     * @param label 알람 라벨
     * @param delayMillis 지연 시간 (밀리초)
     */
    public static void scheduleSnoozeAlarm(Context context, int alarmId, String label, long delayMillis) {
        if (context == null) {
            Log.e(TAG, "Context가 null입니다");
            return;
        }

        if (delayMillis <= 0) {
            Log.e(TAG, "유효하지 않은 지연 시간: " + delayMillis);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager를 가져올 수 없습니다");
            return;
        }

        // 현재 시간에서 지연 시간만큼 더한 시간에 알람 설정
        long triggerTime = System.currentTimeMillis() + delayMillis;

        // 사용자 설정 가져오기 (안전하게)
        boolean soundEnabled = true;
        boolean vibrationEnabled = true;
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
            soundEnabled = prefs.getBoolean("sound_enabled_" + alarmId, true);
            vibrationEnabled = prefs.getBoolean("vibration_enabled_" + alarmId, true);
        } catch (Exception e) {
            Log.w(TAG, "설정 로드 실패, 기본값 사용", e);
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, (label != null ? label : "알람") + " (다시 울림)");
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_SOUND_ENABLED, soundEnabled);
        intent.putExtra(AlarmReceiver.EXTRA_ALARM_VIBRATION_ENABLED, vibrationEnabled);

        // 스누즈 알람을 위한 고유 ID 생성 (원래 ID + 1000000)
        int snoozeId = alarmId + 1000000;

        PendingIntent pendingIntent;
        try {
            pendingIntent = PendingIntent.getBroadcast(
                    context,
                    snoozeId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } catch (Exception e) {
            Log.e(TAG, "스누즈 PendingIntent 생성 실패", e);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }

            Log.d(TAG, "스누즈 알람 설정 - " + (delayMillis / 60000) + "분 후");

        } catch (SecurityException e) {
            Log.e(TAG, "스누즈 알람 권한 부족", e);
        } catch (Exception e) {
            Log.e(TAG, "스누즈 알람 설정 실패", e);
        }
    }

    /**
     * 알람을 취소합니다
     * 
     * @param context 컨텍스트
     * @param alarmId 알람 ID
     */
    public static void cancelAlarm(Context context, int alarmId) {
        if (context == null) {
            Log.e(TAG, "Context가 null입니다");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager를 가져올 수 없습니다");
            return;
        }

        try {
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarmId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();

            Log.d(TAG, "알람 취소 - ID: " + alarmId);
        } catch (Exception e) {
            Log.e(TAG, "알람 취소 실패 - ID: " + alarmId, e);
        }
    }

    /**
     * 알람 설정 저장 (Thread-safe)
     * 
     * @param context 컨텍스트
     * @param alarmId 알람 ID
     * @param soundEnabled 사운드 활성화 여부
     * @param vibrationEnabled 진동 활성화 여부
     */
    public static synchronized void saveAlarmSettings(Context context, int alarmId, boolean soundEnabled, boolean vibrationEnabled) {
        if (context == null) {
            Log.e(TAG, "Context가 null입니다");
            return;
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            editor.putBoolean("sound_enabled_" + alarmId, soundEnabled);
            editor.putBoolean("vibration_enabled_" + alarmId, vibrationEnabled);
            
            editor.apply();
            
            Log.d(TAG, "알람 설정 저장 - ID: " + alarmId + ", 사운드: " + soundEnabled + ", 진동: " + vibrationEnabled);
        } catch (Exception e) {
            Log.e(TAG, "알람 설정 저장 실패 - ID: " + alarmId, e);
        }
    }

    /**
     * 알람 설정 가져오기 (Thread-safe)
     * 
     * @param context 컨텍스트
     * @param alarmId 알람 ID
     * @return [사운드 활성화, 진동 활성화]
     */
    public static synchronized boolean[] getAlarmSettings(Context context, int alarmId) {
        if (context == null) {
            Log.e(TAG, "Context가 null입니다");
            return new boolean[]{true, true}; // 기본값
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE);
            
            boolean soundEnabled = prefs.getBoolean("sound_enabled_" + alarmId, true);
            boolean vibrationEnabled = prefs.getBoolean("vibration_enabled_" + alarmId, true);
            
            return new boolean[]{soundEnabled, vibrationEnabled};
        } catch (Exception e) {
            Log.e(TAG, "알람 설정 로드 실패 - ID: " + alarmId, e);
            return new boolean[]{true, true}; // 기본값
        }
    }
}