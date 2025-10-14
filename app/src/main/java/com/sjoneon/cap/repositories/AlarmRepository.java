package com.sjoneon.cap.repositories;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sjoneon.cap.fragments.AlarmFragment.AlarmItem;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 알람 데이터를 영구 저장하는 저장소 클래스
 */
public class AlarmRepository {
    private static final String PREFS_NAME = "alarm_prefs";
    private static final String KEY_ALARMS = "alarms";

    private static AlarmRepository instance;
    private final Context context;
    private List<AlarmItem> alarms;
    private final Gson gson;

    private AlarmRepository(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        loadAlarms();
    }

    public static synchronized AlarmRepository getInstance(Context context) {
        if (instance == null) {
            instance = new AlarmRepository(context);
        }
        return instance;
    }

    /**
     * SharedPreferences에서 알람 로드
     */
    private void loadAlarms() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ALARMS, null);

        if (json != null) {
            Type type = new TypeToken<ArrayList<AlarmItem>>() {}.getType();
            alarms = gson.fromJson(json, type);
        } else {
            alarms = new ArrayList<>();
        }
    }

    /**
     * SharedPreferences에 알람 저장
     */
    private void saveAlarms() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = gson.toJson(alarms);
        editor.putString(KEY_ALARMS, json);
        editor.apply();
    }

    /**
     * 알람 추가
     */
    public synchronized void addAlarm(AlarmItem alarm) {
        alarms.add(alarm);
        saveAlarms();
    }

    /**
     * 알람 삭제
     */
    public synchronized boolean deleteAlarm(int alarmId) {
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).getId() == alarmId) {
                alarms.remove(i);
                saveAlarms();
                return true;
            }
        }
        return false;
    }

    /**
     * 알람 업데이트
     */
    public synchronized boolean updateAlarm(AlarmItem updatedAlarm) {
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).getId() == updatedAlarm.getId()) {
                alarms.set(i, updatedAlarm);
                saveAlarms();
                return true;
            }
        }
        return false;
    }

    /**
     * 모든 알람 가져오기
     */
    public synchronized List<AlarmItem> getAllAlarms() {
        return new ArrayList<>(alarms);
    }

    /**
     * ID로 알람 찾기
     */
    public synchronized AlarmItem getAlarmById(int alarmId) {
        for (AlarmItem alarm : alarms) {
            if (alarm.getId() == alarmId) {
                return alarm;
            }
        }
        return null;
    }

    /**
     * 서버 ID로 알람 찾기
     */
    public synchronized AlarmItem getAlarmByServerId(Integer serverId) {
        if (serverId == null) {
            return null;
        }
        for (AlarmItem alarm : alarms) {
            if (serverId.equals(alarm.getServerId())) {
                return alarm;
            }
        }
        return null;
    }
}