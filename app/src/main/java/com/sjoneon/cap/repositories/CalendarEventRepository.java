package com.sjoneon.cap.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sjoneon.cap.models.local.CalendarEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 일정 데이터를 관리하는 저장소 클래스
 */
public class CalendarEventRepository {
    private static final String PREFS_NAME = "calendar_events_prefs";
    private static final String KEY_EVENTS = "calendar_events";

    private static CalendarEventRepository instance;
    private final Context context;
    private List<CalendarEvent> events;
    private final Gson gson;

    private CalendarEventRepository(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        loadEvents();
    }

    public static synchronized CalendarEventRepository getInstance(Context context) {
        if (instance == null) {
            instance = new CalendarEventRepository(context);
        }
        return instance;
    }

    /**
     * 저장된 일정 로드
     */
    private void loadEvents() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_EVENTS, null);

        if (json != null) {
            Type type = new TypeToken<ArrayList<CalendarEvent>>() {}.getType();
            events = gson.fromJson(json, type);
        } else {
            events = new ArrayList<>();
        }
    }

    /**
     * 일정 저장
     */
    private void saveEvents() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = gson.toJson(events);
        editor.putString(KEY_EVENTS, json);
        editor.apply();
    }

    /**
     * 특정 날짜의 일정 가져오기 (수정된 버전)
     */
    public List<CalendarEvent> getEventsForDate(long date) {
        List<CalendarEvent> eventsForDate = new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);

        // 해당 날짜의 시작과 끝을 정확히 설정
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfDay = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        long endOfDay = calendar.getTimeInMillis();

        Log.d("CalendarRepository", "검색 날짜 범위: " +
                new Date(startOfDay) + " ~ " + new Date(endOfDay));

        for (CalendarEvent event : events) {
            Log.d("CalendarRepository", "이벤트 시간: " + new Date(event.getDateTime()) +
                    ", 제목: " + event.getTitle());

            if (event.getDateTime() >= startOfDay && event.getDateTime() <= endOfDay) {
                eventsForDate.add(event);
                Log.d("CalendarRepository", "이벤트 매칭됨: " + event.getTitle());
            }
        }

        Collections.sort(eventsForDate, new Comparator<CalendarEvent>() {
            @Override
            public int compare(CalendarEvent o1, CalendarEvent o2) {
                return Long.compare(o1.getDateTime(), o2.getDateTime());
            }
        });

        Log.d("CalendarRepository", "총 " + eventsForDate.size() + "개 이벤트 발견");
        return eventsForDate;
    }

    /**
     * 일정 추가
     */
    public long addEvent(CalendarEvent event) {
        if (event.getId() == 0 || isIdExists(event.getId())) {
            event.setId(generateUniqueId());
        }

        events.add(event);
        saveEvents();
        return event.getId();
    }

    /**
     * 일정 수정
     */
    public boolean updateEvent(CalendarEvent updatedEvent) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getId() == updatedEvent.getId()) {
                events.set(i, updatedEvent);
                saveEvents();
                return true;
            }
        }
        return false;
    }

    /**
     * 일정 삭제
     */
    public boolean deleteEvent(long eventId) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getId() == eventId) {
                events.remove(i);
                saveEvents();
                return true;
            }
        }
        return false;
    }

    /**
     * ID로 일정 찾기
     */
    public CalendarEvent getEventById(long eventId) {
        for (CalendarEvent event : events) {
            if (event.getId() == eventId) {
                return event;
            }
        }
        return null;
    }

    /**
     * 고유 ID 생성
     */
    private long generateUniqueId() {
        long id = System.currentTimeMillis();
        while (isIdExists(id)) {
            id++;
        }
        return id;
    }

    /**
     * ID 중복 확인
     */
    private boolean isIdExists(long id) {
        for (CalendarEvent event : events) {
            if (event.getId() == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * 모든 일정 가져오기
     */
    public List<CalendarEvent> getAllEvents() {
        Collections.sort(events, new Comparator<CalendarEvent>() {
            @Override
            public int compare(CalendarEvent o1, CalendarEvent o2) {
                return Long.compare(o1.getDateTime(), o2.getDateTime());
            }
        });
        return new ArrayList<>(events);
    }
}