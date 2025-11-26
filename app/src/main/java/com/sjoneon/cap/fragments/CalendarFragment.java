package com.sjoneon.cap.fragments;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sjoneon.cap.repositories.CalendarEventRepository;
import com.sjoneon.cap.R;
import com.sjoneon.cap.helpers.EventAlarmManager;
import com.sjoneon.cap.helpers.PermissionHelper;
import com.sjoneon.cap.models.local.CalendarEvent;
import com.sjoneon.cap.utils.ApiClient;
import com.sjoneon.cap.models.api.CalendarEventRequest;
import com.sjoneon.cap.models.api.CalendarEventResponse;
import com.sjoneon.cap.models.api.CalendarEventUpdateRequest;
import com.sjoneon.cap.models.api.ApiResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CalendarFragment extends Fragment {

    private static final String TAG = "CalendarFragment";

    private CalendarView calendarView;
    private RecyclerView recyclerViewEvents;
    private FloatingActionButton fabAddEvent;
    private TextView textNoEvents;

    private CalendarEventRepository eventRepository;
    private EventAlarmManager alarmManager;
    private CalendarEventAdapter eventAdapter;
    private List<CalendarEvent> currentEvents = new ArrayList<>();

    private long selectedDate = System.currentTimeMillis();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        Log.d(TAG, "onCreateView called");

        calendarView = view.findViewById(R.id.calendarView);
        recyclerViewEvents = view.findViewById(R.id.recyclerViewEvents);
        fabAddEvent = view.findViewById(R.id.fabAddEvent);
        textNoEvents = view.findViewById(R.id.textNoEvents);

        Log.d(TAG, "Views initialized");

        eventRepository = CalendarEventRepository.getInstance(requireContext());
        alarmManager = new EventAlarmManager(requireContext());

        Log.d(TAG, "Repository and AlarmManager initialized");

        setupCalendarView();
        setupRecyclerView();
        setupFab();

        Log.d(TAG, "Setup methods called");

        loadEventsFromServer();

        Log.d(TAG, "Initial data loaded");

        return view;
    }

    private void setupCalendarView() {
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                Log.d(TAG, "날짜 선택: " + year + "-" + (month+1) + "-" + dayOfMonth);

                Calendar calendar = Calendar.getInstance();
                calendar.clear();
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                selectedDate = calendar.getTimeInMillis();

                Log.d(TAG, "선택된 날짜 timestamp: " + selectedDate);
                Log.d(TAG, "선택된 날짜: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(selectedDate)));

                loadEventsForDate(selectedDate);
            }
        });

        Calendar initialCalendar = Calendar.getInstance();
        initialCalendar.set(Calendar.HOUR_OF_DAY, 0);
        initialCalendar.set(Calendar.MINUTE, 0);
        initialCalendar.set(Calendar.SECOND, 0);
        initialCalendar.set(Calendar.MILLISECOND, 0);
        selectedDate = initialCalendar.getTimeInMillis();

        Log.d(TAG, "초기 선택 날짜: " + new Date(selectedDate));
    }

    private void setupRecyclerView() {
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        eventAdapter = new CalendarEventAdapter();
        recyclerViewEvents.setAdapter(eventAdapter);

        Log.d(TAG, "RecyclerView 설정 완료");

        eventAdapter.updateEvents(new ArrayList<>());
    }

    private void setupFab() {
        fabAddEvent.setOnClickListener(v -> showAddEventDialog());
    }

    private void loadEventsFromServer() {
        SharedPreferences preferences = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userUuid = preferences.getString("user_uuid", null);

        if (userUuid == null) {
            Log.e(TAG, "사용자 UUID가 없습니다");
            loadEventsForDate(selectedDate);
            return;
        }

        if (ApiClient.getInstance() == null || ApiClient.getInstance().getApiService() == null) {
            Log.e(TAG, "API 클라이언트 초기화 실패");
            loadEventsForDate(selectedDate);
            return;
        }

        ApiClient.getInstance().getApiService()
                .getUserEvents(userUuid)
                .enqueue(new Callback<List<CalendarEventResponse>>() {
                    @Override
                    public void onResponse(Call<List<CalendarEventResponse>> call, Response<List<CalendarEventResponse>> response) {
                        if (response == null || !response.isSuccessful() || response.body() == null) {
                            loadEventsForDate(selectedDate);
                            return;
                        }

                        Log.d(TAG, "서버에서 일정 불러오기 성공: " + response.body().size() + "개");

                        // 타임존을 한국 시간으로 설정
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

                        List<CalendarEvent> existingEvents = eventRepository.getAllEvents();

                        for (CalendarEventResponse eventResponse : response.body()) {
                            CalendarEvent existingEvent = null;

                            // serverId로 기존 일정 찾기
                            for (CalendarEvent event : existingEvents) {
                                if (event.getServerId() != null && event.getServerId() == eventResponse.getId()) {
                                    existingEvent = event;
                                    break;
                                }
                            }

                            try {
                                Date startDate = sdf.parse(eventResponse.getEventStartTime());
                                if (startDate == null) {
                                    Log.e(TAG, "날짜 파싱 실패: " + eventResponse.getEventStartTime());
                                    continue;
                                }

                                long newDateTime = startDate.getTime();

                                if (existingEvent != null) {
                                    // 서버와 로컬 데이터 비교
                                    String serverDescription = eventResponse.getDescription() != null ? eventResponse.getDescription() : "";
                                    String localDescription = existingEvent.getDescription() != null ? existingEvent.getDescription() : "";

                                    boolean timeChanged = existingEvent.getDateTime() != newDateTime;
                                    boolean descriptionChanged = !serverDescription.equals(localDescription);
                                    boolean titleChanged = !eventResponse.getEventTitle().equals(existingEvent.getTitle());

                                    // 시간, 설명, 제목 중 하나라도 변경되면 업데이트
                                    if (timeChanged || descriptionChanged || titleChanged) {
                                        Log.d(TAG, "일정 변경 감지: " + eventResponse.getEventTitle());
                                        Log.d(TAG, "시간변경: " + timeChanged + ", 설명변경: " + descriptionChanged + ", 제목변경: " + titleChanged);

                                        existingEvent.setDateTime(newDateTime);
                                        existingEvent.setTitle(eventResponse.getEventTitle());
                                        existingEvent.setDescription(serverDescription);

                                        eventRepository.updateEvent(existingEvent);
                                        Log.d(TAG, "로컬 일정 업데이트 완료");
                                    } else {
                                        Log.d(TAG, "일정 변경 없음: " + eventResponse.getEventTitle());
                                    }
                                } else {
                                    // 새 일정인 경우 - 추가
                                    CalendarEvent newEvent = new CalendarEvent(
                                            eventResponse.getEventTitle(),
                                            eventResponse.getDescription() != null ? eventResponse.getDescription() : "",
                                            newDateTime
                                    );
                                    newEvent.setServerId(eventResponse.getId());
                                    eventRepository.addEvent(newEvent);
                                    Log.d(TAG, "서버에서 가져온 새 일정 추가: " + eventResponse.getEventTitle());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "날짜 파싱 오류: " + eventResponse.getEventStartTime(), e);
                            }
                        }

                        // UI 업데이트
                        loadEventsForDate(selectedDate);
                    }

                    @Override
                    public void onFailure(Call<List<CalendarEventResponse>> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                        loadEventsForDate(selectedDate);
                    }
                });
    }

    private void loadEventsForDate(long date) {
        Log.d(TAG, "loadEventsForDate 호출 - 날짜: " + new Date(date));

        Calendar startOfDay = Calendar.getInstance();
        startOfDay.setTimeInMillis(date);
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);

        Calendar endOfDay = Calendar.getInstance();
        endOfDay.setTimeInMillis(date);
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 999);

        long startTime = startOfDay.getTimeInMillis();
        long endTime = endOfDay.getTimeInMillis();

        Log.d(TAG, "검색 범위: " + new Date(startTime) + " ~ " + new Date(endTime));

        List<CalendarEvent> allEvents = eventRepository.getAllEvents();
        currentEvents.clear();

        for (CalendarEvent event : allEvents) {
            if (event.getDateTime() >= startTime && event.getDateTime() <= endTime) {
                currentEvents.add(event);
                Log.d(TAG, "이벤트 발견: " + event.getTitle() + " at " + new Date(event.getDateTime()));
            }
        }

        Log.d(TAG, "선택된 날짜의 이벤트 수: " + currentEvents.size());

        if (eventAdapter != null) {
            eventAdapter.updateEvents(currentEvents);
            Log.d(TAG, "어댑터 업데이트 완료");
        }

        updateEventListVisibility();

        if (getView() != null) {
            getView().post(() -> {
                if (recyclerViewEvents != null && recyclerViewEvents.getAdapter() != null) {
                    recyclerViewEvents.getAdapter().notifyDataSetChanged();
                    Log.d(TAG, "RecyclerView 강제 새로고침 완료");

                    if (eventAdapter.getItemCount() > 0 && recyclerViewEvents.getChildCount() == 0) {
                        recyclerViewEvents.requestLayout();
                        Log.d(TAG, "RecyclerView 레이아웃 강제 요청");
                    }
                }
            });
        }
    }

    private void updateEventListVisibility() {
        Log.d(TAG, "UI 업데이트 - 이벤트 수: " + currentEvents.size());

        if (currentEvents == null || currentEvents.isEmpty()) {
            Log.d(TAG, "일정 없음 - 메시지 표시");
            textNoEvents.setVisibility(View.VISIBLE);
            recyclerViewEvents.setVisibility(View.GONE);
        } else {
            Log.d(TAG, "일정 있음 - 리스트 표시");
            textNoEvents.setVisibility(View.GONE);
            recyclerViewEvents.setVisibility(View.VISIBLE);
        }
    }

    private void showAddEventDialog() {
        showEventDialog(null, false);
    }

    private void showEditEventDialog(CalendarEvent event) {
        showEventDialog(event, true);
    }

    private void showEventDialog(CalendarEvent existingEvent, boolean isEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_event, null);

        EditText editTitle = dialogView.findViewById(R.id.editEventTitle);
        EditText editDescription = dialogView.findViewById(R.id.editEventDescription);
        TextView textSelectedDateTime = dialogView.findViewById(R.id.textSelectedDateTime);
        LinearLayout layoutNotificationSettings = dialogView.findViewById(R.id.layoutNotificationSettings);

        Calendar eventCalendar = Calendar.getInstance();
        if (isEdit && existingEvent != null) {
            editTitle.setText(existingEvent.getTitle());
            editDescription.setText(existingEvent.getDescription());
            eventCalendar.setTimeInMillis(existingEvent.getDateTime());
        } else {
            eventCalendar.setTimeInMillis(selectedDate);
            eventCalendar.set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
            eventCalendar.set(Calendar.MINUTE, Calendar.getInstance().get(Calendar.MINUTE));
        }

        updateDateTimeDisplay(textSelectedDateTime, eventCalendar.getTimeInMillis());

        textSelectedDateTime.setOnClickListener(v -> showDateTimePicker(eventCalendar, textSelectedDateTime));

        setupNotificationCheckboxes(layoutNotificationSettings, existingEvent);

        builder.setView(dialogView)
                .setTitle(isEdit ? "일정 수정" : getString(R.string.add_event))
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        if (isEdit) {
            builder.setNeutralButton(R.string.delete, (dialog, id) -> {
                showDeleteConfirmationDialog(existingEvent);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                Log.d(TAG, "Save button clicked!");

                String title = editTitle.getText().toString().trim();
                String description = editDescription.getText().toString().trim();

                Log.d(TAG, "Title: " + title + ", Description: " + description);

                if (title.isEmpty()) {
                    Toast.makeText(getContext(), R.string.empty_event_title, Toast.LENGTH_SHORT).show();
                    return;
                }

                List<CalendarEvent.NotificationSetting> notificationSettings = collectNotificationSettings(layoutNotificationSettings);
                Log.d(TAG, "Notification settings collected: " + notificationSettings.size());

                long eventDateTime = eventCalendar.getTimeInMillis();
                Log.d(TAG, "Event date time: " + new Date(eventDateTime));

                if (isEdit && existingEvent != null) {
                    Log.d(TAG, "Updating existing event");
                    updateEvent(existingEvent, title, description, eventDateTime, notificationSettings);
                } else {
                    Log.d(TAG, "Creating new event");
                    createNewEvent(title, description, eventDateTime, notificationSettings);
                }

                dialog.dismiss();

            } catch (Exception e) {
                Log.e(TAG, "Error in save button click", e);
                Toast.makeText(getContext(), "저장 중 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDateTimePicker(Calendar calendar, TextView textView) {
        new DatePickerDialog(getContext(), R.style.DialogTheme,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    new TimePickerDialog(getContext(), R.style.DialogTheme,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                updateDateTimeDisplay(textView, calendar.getTimeInMillis());
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false
                    ).show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void updateDateTimeDisplay(TextView textView, long dateTime) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        textView.setText(format.format(new Date(dateTime)));
    }

    private void setupNotificationCheckboxes(LinearLayout layout, CalendarEvent existingEvent) {
        layout.removeAllViews();

        for (CalendarEvent.NotificationSetting.NotificationType type :
                CalendarEvent.NotificationSetting.NotificationType.values()) {

            CheckBox checkBox = new CheckBox(getContext());
            checkBox.setText(type.getDisplayName());
            checkBox.setTag(type);

            if (existingEvent != null && existingEvent.getNotificationSettings() != null) {
                for (CalendarEvent.NotificationSetting setting : existingEvent.getNotificationSettings()) {
                    if (setting.getType() == type && setting.isEnabled()) {
                        checkBox.setChecked(true);
                        break;
                    }
                }
            }

            layout.addView(checkBox);
        }
    }

    private List<CalendarEvent.NotificationSetting> collectNotificationSettings(LinearLayout layout) {
        List<CalendarEvent.NotificationSetting> settings = new ArrayList<>();

        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) child;
                CalendarEvent.NotificationSetting.NotificationType type =
                        (CalendarEvent.NotificationSetting.NotificationType) checkBox.getTag();

                if (checkBox.isChecked()) {
                    settings.add(new CalendarEvent.NotificationSetting(type, true));
                }
            }
        }

        return settings;
    }

    private void createNewEvent(String title, String description, long dateTime,
                                List<CalendarEvent.NotificationSetting> notificationSettings) {
        Log.d(TAG, "새 이벤트 생성 시작");

        if (alarmManager != null) {
            alarmManager.logPermissionStatus();
        }

        CalendarEvent newEvent = new CalendarEvent(title, description, dateTime);
        newEvent.setNotificationSettings(notificationSettings);

        long eventId = eventRepository.addEvent(newEvent);
        newEvent.setId(eventId);

        Log.d(TAG, "이벤트 저장 완료, ID: " + eventId);

        createEventOnServer(title, description, dateTime);

        if (!notificationSettings.isEmpty()) {
            if (!PermissionHelper.hasNotificationPermission(getContext()) ||
                    !PermissionHelper.hasExactAlarmPermission(getContext())) {
                PermissionHelper.checkAndRequestAllPermissions(getActivity());
                Toast.makeText(getContext(),
                        "일정이 저장되었습니다. 알림을 받으려면 권한을 허용해주세요.",
                        Toast.LENGTH_LONG).show();
            } else {
                alarmManager.scheduleEventNotifications(newEvent);
            }
        }

        Calendar eventCalendar = Calendar.getInstance();
        eventCalendar.setTimeInMillis(dateTime);
        eventCalendar.set(Calendar.HOUR_OF_DAY, 0);
        eventCalendar.set(Calendar.MINUTE, 0);
        eventCalendar.set(Calendar.SECOND, 0);
        eventCalendar.set(Calendar.MILLISECOND, 0);

        long eventDate = eventCalendar.getTimeInMillis();

        if (selectedDate != eventDate) {
            selectedDate = eventDate;
            calendarView.setDate(selectedDate, false, false);
        }

        loadEventsForDate(selectedDate);

        String successMessage = getString(R.string.event_added);
        if (!notificationSettings.isEmpty()) {
            successMessage += " (" + notificationSettings.size() + "개 알림 설정됨)";
        }
        Toast.makeText(getContext(), successMessage, Toast.LENGTH_SHORT).show();
    }

    private void createEventOnServer(String title, String description, long dateTime) {
        SharedPreferences preferences = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userUuid = preferences.getString("user_uuid", null);

        if (userUuid == null || ApiClient.getInstance() == null) {
            Log.e(TAG, "서버에 일정 추가 불가");
            return;
        }

        // 타임존을 한국 시간으로 설정
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String eventStartTime = sdf.format(new Date(dateTime));

        CalendarEventRequest request = new CalendarEventRequest(userUuid, title, eventStartTime);
        request.setDescription(description);

        ApiClient.getInstance().getApiService()
                .createCalendarEvent(request)
                .enqueue(new Callback<CalendarEventResponse>() {
                    @Override
                    public void onResponse(Call<CalendarEventResponse> call,
                                           Response<CalendarEventResponse> response) {
                        if (response != null && response.isSuccessful() && response.body() != null) {
                            int serverId = response.body().getId();
                            Log.d(TAG, "서버에 일정 추가 성공, ID: " + serverId);

                            saveServerIdToLocalEvent(title, dateTime, serverId);
                        }
                    }

                    @Override
                    public void onFailure(Call<CalendarEventResponse> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                    }
                });
    }

    private void saveServerIdToLocalEvent(String title, long dateTime, int serverId) {
        List<CalendarEvent> events = eventRepository.getAllEvents();

        for (CalendarEvent event : events) {
            if (event.getTitle().equals(title) &&
                    Math.abs(event.getDateTime() - dateTime) < 1000) {
                event.setServerId(serverId);
                eventRepository.updateEvent(event);
                Log.d(TAG, "로컬 이벤트에 서버 ID 저장 완료: " + serverId);
                return;
            }
        }
        Log.w(TAG, "서버 ID를 저장할 로컬 이벤트를 찾지 못함");
    }

    private void updateEvent(CalendarEvent event, String title, String description, long dateTime,
                             List<CalendarEvent.NotificationSetting> notificationSettings) {
        alarmManager.cancelEventNotifications(event);

        event.setTitle(title);
        event.setDescription(description);
        event.setDateTime(dateTime);
        event.setNotificationSettings(notificationSettings);

        eventRepository.updateEvent(event);

        if (event.getServerId() != null) {
            updateEventOnServer(event.getServerId(), title, description, dateTime);
        } else {
            Log.w(TAG, "서버 ID가 없어 로컬에만 수정됨");
        }

        if (!notificationSettings.isEmpty()) {
            if (PermissionHelper.hasNotificationPermission(getContext()) &&
                    PermissionHelper.hasExactAlarmPermission(getContext())) {
                alarmManager.scheduleEventNotifications(event);
            } else {
                Toast.makeText(getContext(),
                        "일정이 수정되었습니다. 알림을 받으려면 권한을 허용해주세요.",
                        Toast.LENGTH_LONG).show();
            }
        }

        loadEventsForDate(selectedDate);
        Toast.makeText(getContext(), "일정이 수정되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void updateEventOnServer(int serverId, String title, String description, long dateTime) {
        if (ApiClient.getInstance() == null) {
            Log.e(TAG, "ApiClient 없음");
            return;
        }

        // 타임존을 한국 시간으로 설정
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String eventStartTime = sdf.format(new Date(dateTime));

        CalendarEventUpdateRequest request = new CalendarEventUpdateRequest(title, eventStartTime);
        request.setDescription(description);

        ApiClient.getInstance().getApiService()
                .updateCalendarEvent(serverId, request)
                .enqueue(new Callback<CalendarEventResponse>() {
                    @Override
                    public void onResponse(Call<CalendarEventResponse> call,
                                           Response<CalendarEventResponse> response) {
                        if (response != null && response.isSuccessful()) {
                            Log.d(TAG, "서버에 일정 수정 성공: ID=" + serverId);
                        } else {
                            Log.e(TAG, "서버 일정 수정 실패");
                        }
                    }

                    @Override
                    public void onFailure(Call<CalendarEventResponse> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                    }
                });
    }

    private void showDeleteConfirmationDialog(CalendarEvent event) {
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("일정 삭제")
                .setMessage("정말로 이 일정을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> deleteEvent(event))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteEvent(CalendarEvent event) {
        alarmManager.cancelEventNotifications(event);

        eventRepository.deleteEvent(event.getId());

        if (event.getServerId() != null) {
            deleteEventFromServer(event.getServerId());
        } else {
            Log.w(TAG, "서버 ID가 없어 로컬에서만 삭제됨");
        }

        loadEventsForDate(selectedDate);
        Toast.makeText(getContext(), "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void deleteEventFromServer(int serverId) {
        if (ApiClient.getInstance() == null) {
            Log.e(TAG, "ApiClient 없음");
            return;
        }

        ApiClient.getInstance().getApiService()
                .deleteCalendarEvent(serverId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                        if (response != null && response.isSuccessful()) {
                            Log.d(TAG, "서버에서 일정 삭제 성공: ID=" + serverId);
                        } else {
                            Log.e(TAG, "서버 일정 삭제 실패: " +
                                    (response != null ? response.code() : "null"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                    }
                });
    }

    private class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {

        private List<CalendarEvent> adapterEvents = new ArrayList<>();

        public void updateEvents(List<CalendarEvent> events) {
            Log.d(TAG + "_Adapter", "updateEvents 호출: " + events.size() + "개 이벤트");

            adapterEvents.clear();
            if (events != null) {
                adapterEvents.addAll(events);
            }

            Log.d(TAG + "_Adapter", "어댑터 이벤트 수: " + adapterEvents.size());
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_event, parent, false);
            return new EventViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
            if (position >= adapterEvents.size()) {
                Log.e(TAG + "_Adapter", "잘못된 position: " + position);
                return;
            }

            CalendarEvent event = adapterEvents.get(position);

            holder.textTitle.setText(event.getTitle());
            holder.textDescription.setText(event.getDescription());

            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            holder.textTime.setText(timeFormat.format(new Date(event.getDateTime())));

            if (event.isPastEvent()) {
                holder.textTitle.setAlpha(0.6f);
                holder.textDescription.setAlpha(0.6f);
                holder.textTime.setAlpha(0.6f);
            } else {
                holder.textTitle.setAlpha(1.0f);
                holder.textDescription.setAlpha(1.0f);
                holder.textTime.setAlpha(1.0f);
            }

            holder.itemView.setOnClickListener(v -> showEditEventDialog(event));

            holder.itemView.setOnLongClickListener(v -> {
                showDeleteConfirmationDialog(event);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return adapterEvents.size();
        }

        class EventViewHolder extends RecyclerView.ViewHolder {
            TextView textTitle;
            TextView textTime;
            TextView textDescription;

            EventViewHolder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textEventTitle);
                textTime = itemView.findViewById(R.id.textEventTime);
                textDescription = itemView.findViewById(R.id.textEventDescription);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");

        // 일정 변경 플래그 확인
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("calendar_needs_sync", false)) {
            Log.d(TAG, "일정 변경 플래그 감지 - 서버 동기화");
            loadEventsFromServer();
            prefs.edit().putBoolean("calendar_needs_sync", false).apply();
        } else {
            syncWithServer();
        }

        if (getView() != null) {
            getView().postDelayed(() -> {
                loadEventsForDate(selectedDate);
            }, 100);
        }
    }

    /**
     * 외부에서 호출 가능한 서버 동기화 메서드
     */
    public void syncFromServer() {
        Log.d(TAG, "외부에서 동기화 요청됨");
        loadEventsFromServer();
    }

    private void syncWithServer() {
        SharedPreferences preferences = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userUuid = preferences.getString("user_uuid", null);

        if (userUuid == null || ApiClient.getInstance() == null) {
            return;
        }

        ApiClient.getInstance().getApiService()
                .getUserEvents(userUuid)
                .enqueue(new Callback<List<CalendarEventResponse>>() {
                    @Override
                    public void onResponse(Call<List<CalendarEventResponse>> call,
                                           Response<List<CalendarEventResponse>> response) {
                        if (response != null && response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "서버 동기화 성공: " + response.body().size() + "개");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CalendarEventResponse>> call, Throwable t) {
                        Log.e(TAG, "서버 동기화 실패", t);
                    }
                });
    }
}