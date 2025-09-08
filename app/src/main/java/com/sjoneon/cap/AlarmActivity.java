package com.sjoneon.cap;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 알람이 울릴 때 표시되는 Activity
 */
public class AlarmActivity extends AppCompatActivity {

    private static final String TAG = "AlarmActivity";
    private static final String ALARM_PREFS = "alarm_preferences";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private TextView textAlarmLabel;
    private TextView textCurrentTime;
    private Button buttonStopAlarm;
    private Button buttonSnooze;

    private int alarmId;
    private String alarmLabel;
    private boolean soundEnabled;
    private boolean vibrationEnabled;
    private TextView textCurrentDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // 잠금 화면 위에 표시되도록 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        initializeViews();
        getAlarmData();
        setupButtons();
        startAlarmSoundAndVibration();
        updateTimeDisplay();
    }

    private void initializeViews() {
        textAlarmLabel = findViewById(R.id.textAlarmLabel);
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textCurrentDate = findViewById(R.id.textCurrentDate);
        buttonStopAlarm = findViewById(R.id.buttonStopAlarm);
        buttonSnooze = findViewById(R.id.buttonSnooze);
    }

    private void getAlarmData() {
        // Intent에서 알람 정보 가져오기
        alarmId = getIntent().getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1);
        alarmLabel = getIntent().getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL);
        soundEnabled = getIntent().getBooleanExtra(AlarmReceiver.EXTRA_ALARM_SOUND_ENABLED, true);
        vibrationEnabled = getIntent().getBooleanExtra(AlarmReceiver.EXTRA_ALARM_VIBRATION_ENABLED, true);

        // 라벨이 없으면 기본 텍스트 사용
        if (alarmLabel == null || alarmLabel.trim().isEmpty()) {
            alarmLabel = getString(R.string.default_alarm_label);
        }

        textAlarmLabel.setText(alarmLabel);

        Log.d(TAG, "알람 정보 - ID: " + alarmId + ", 라벨: " + alarmLabel +
                ", 사운드: " + soundEnabled + ", 진동: " + vibrationEnabled);
    }

    private void setupButtons() {
        buttonStopAlarm.setOnClickListener(v -> stopAlarm());

        buttonSnooze.setOnClickListener(v -> snoozeAlarm());
    }

    private void startAlarmSoundAndVibration() {
        // 사운드 재생
        if (soundEnabled) {
            startAlarmSound();
        }

        // 진동 시작
        if (vibrationEnabled) {
            startVibration();
        }
    }

    private void startAlarmSound() {
        try {
            // 사용자 설정 알람음 가져오기 (기본값은 시스템 알람음)
            SharedPreferences prefs = getSharedPreferences(ALARM_PREFS, MODE_PRIVATE);
            String alarmSoundUri = prefs.getString("alarm_sound_uri", null);

            Uri soundUri;
            if (alarmSoundUri != null) {
                soundUri = Uri.parse(alarmSoundUri);
            } else {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (soundUri == null) {
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
            }

            mediaPlayer = new MediaPlayer();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            mediaPlayer.setDataSource(this, soundUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            Log.d(TAG, "알람 사운드 시작");

        } catch (Exception e) {
            Log.e(TAG, "알람 사운드 재생 실패", e);
        }
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            // 진동 패턴: [대기, 진동, 대기, 진동, ...]
            long[] pattern = {0, 1000, 1000, 1000, 1000};

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect vibrationEffect = VibrationEffect.createWaveform(pattern, 0);
                vibrator.vibrate(vibrationEffect);
            } else {
                vibrator.vibrate(pattern, 0);
            }

            Log.d(TAG, "알람 진동 시작");
        }
    }

    private void updateTimeDisplay() {
        if (textCurrentTime != null && textCurrentDate != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일 EEEE", Locale.getDefault());

            Date now = new Date();
            String timeText = timeFormat.format(now);
            String dateText = dateFormat.format(now);

            textCurrentTime.setText(timeText);
            textCurrentDate.setText(dateText);
        }
    }

    private void stopAlarm() {
        Log.d(TAG, "알람 정지");
        stopSoundAndVibration();
        finish();
    }

    private void snoozeAlarm() {
        Log.d(TAG, "알람 다시 울림 설정 (5분 후)");

        // 5분 후 다시 알람 설정
        AlarmScheduler.scheduleSnoozeAlarm(this, alarmId, alarmLabel, 5 * 60 * 1000); // 5분

        stopSoundAndVibration();
        finish();
    }

    private void stopSoundAndVibration() {
        // 사운드 정지
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "미디어 플레이어 정지 오류", e);
            }
        }

        // 진동 정지
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSoundAndVibration();
    }

    @Override
    public void onBackPressed() {
        // 뒤로 가기 버튼으로 알람을 끌 수 없도록 함
        // 사용자가 명시적으로 버튼을 눌러야 함
    }
}