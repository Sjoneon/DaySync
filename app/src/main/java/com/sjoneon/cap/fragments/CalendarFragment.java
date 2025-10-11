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

        // 서버에서 일정 불러오기
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

                        List<CalendarEvent> existingEvents = eventRepository.getAllEvents();

                        for (CalendarEventResponse eventResponse : response.body()) {
                            if (eventResponse == null || eventResponse.getEventTitle() == null ||
                                    eventResponse.getEventStartTime() == null) {
                                continue;
                            }

                            long dateTimeMillis = convertToMillis(eventResponse.getEventStartTime());
                            if (dateTimeMillis == -1) continue;

                            boolean isDuplicate = false;
                            for (CalendarEvent existing : existingEvents) {
                                if (existing.getTitle().equals(eventResponse.getEventTitle()) &&
                                        existing.getDateTime() == dateTimeMillis) {
                                    isDuplicate = true;
                                    break;
                                }
                            }

                            if (!isDuplicate) {
                                CalendarEvent localEvent = new CalendarEvent(
                                        eventResponse.getEventTitle(),
                                        eventResponse.getDescription(),
                                        dateTimeMillis
                                );
                                eventRepository.addEvent(localEvent);
                            }
                        }

                        loadEventsForDate(selectedDate);
                    }

                    @Override
                    public void onFailure(Call<List<CalendarEventResponse>> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                        loadEventsForDate(selectedDate);
                    }
                });
    }

    private long convertToMillis(String isoDateString) {
        if (isoDateString == null || isoDateString.isEmpty()) {
            return -1;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(isoDateString);
            return date == null ? -1 : date.getTime();
        } catch (Exception e) {
            Log.e(TAG, "날짜 변환 오류: " + isoDateString, e);
            return -1;
        }
    }

    private void loadEventsForDate(long date) {
        Log.d(TAG, "loadEventsForDate 시작: " + new Date(date));

        List<CalendarEvent> events = eventRepository.getEventsForDate(date);
        Log.d(TAG, "Repository에서 로드된 이벤트 수: " + events.size());

        currentEvents.clear();
        currentEvents.addAll(events);

        Log.d(TAG, "currentEvents 크기: " + currentEvents.size());

        if (eventAdapter != null) {
            eventAdapter.updateEvents(new ArrayList<>(currentEvents));
            Log.d(TAG, "어댑터 업데이트 완료");
        } else {
            Log.e(TAG, "eventAdapter가 null입니다!");
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

    private void showDateTimePicker(Calendar eventCalendar, TextView textView) {
        int year = eventCalendar.get(Calendar.YEAR);
        int month = eventCalendar.get(Calendar.MONTH);
        int day = eventCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), R.style.DialogTheme,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    eventCalendar.set(Calendar.YEAR, selectedYear);
                    eventCalendar.set(Calendar.MONTH, selectedMonth);
                    eventCalendar.set(Calendar.DAY_OF_MONTH, selectedDay);

                    int hour = eventCalendar.get(Calendar.HOUR_OF_DAY);
                    int minute = eventCalendar.get(Calendar.MINUTE);

                    TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(), R.style.DialogTheme,
                            (timeView, selectedHour, selectedMinute) -> {
                                eventCalendar.set(Calendar.HOUR_OF_DAY, selectedHour);
                                eventCalendar.set(Calendar.MINUTE, selectedMinute);
                                updateDateTimeDisplay(textView, eventCalendar.getTimeInMillis());
                            }, hour, minute, true);

                    timePickerDialog.show();
                }, year, month, day);

        datePickerDialog.show();
    }

    private void updateDateTimeDisplay(TextView textView, long dateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault());
        textView.setText(sdf.format(new Date(dateTime)));
    }

    private void setupNotificationCheckboxes(LinearLayout container, CalendarEvent existingEvent) {
        container.removeAllViews();

        CalendarEvent.NotificationSetting.NotificationType[] types =
                CalendarEvent.NotificationSetting.NotificationType.values();

        for (CalendarEvent.NotificationSetting.NotificationType type : types) {
            CheckBox checkBox = new CheckBox(getContext());
            checkBox.setText(type.getDisplayName());
            checkBox.setTextColor(getResources().getColor(R.color.text_primary));
            checkBox.setTag(type);

            if (existingEvent != null && existingEvent.getNotificationSettings() != null) {
                for (CalendarEvent.NotificationSetting setting : existingEvent.getNotificationSettings()) {
                    if (setting.getType() == type) {
                        checkBox.setChecked(setting.isEnabled());
                        break;
                    }
                }
            }

            container.addView(checkBox);
        }
    }

    private List<CalendarEvent.NotificationSetting> collectNotificationSettings(LinearLayout container) {
        List<CalendarEvent.NotificationSetting> settings = new ArrayList<>();

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
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

        // 서버에 일정 추가
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
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

    private void showDeleteConfirmationDialog(CalendarEvent event) {
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("일정 삭제")
                .setMessage("정말로 이 일정을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> deleteEvent(event))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteEvent(CalendarEvent event) {
        // 시스템 알림 취소
        alarmManager.cancelEventNotifications(event);

        // 로컬에서 삭제
        eventRepository.deleteEvent(event.getId());

        // 서버에서도 삭제
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
        syncWithServer();

        if (getView() != null) {
            getView().postDelayed(() -> {
                loadEventsForDate(selectedDate);
            }, 100);
        }
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
                            mapServerIdsToLocalEvents(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CalendarEventResponse>> call, Throwable t) {
                        Log.e(TAG, "서버 동기화 실패", t);
                    }
                });
    }

    private void mapServerIdsToLocalEvents(List<CalendarEventResponse> serverEvents) {
        List<CalendarEvent> localEvents = eventRepository.getAllEvents();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

        for (CalendarEvent localEvent : localEvents) {
            if (localEvent.getServerId() != null) {
                continue;
            }

            for (CalendarEventResponse serverEvent : serverEvents) {
                if (!localEvent.getTitle().equals(serverEvent.getEventTitle())) {
                    continue;
                }

                try {
                    Date serverDate = sdf.parse(serverEvent.getEventStartTime());
                    if (serverDate != null &&
                            Math.abs(serverDate.getTime() - localEvent.getDateTime()) < 60000) {
                        localEvent.setServerId(serverEvent.getId());
                        eventRepository.updateEvent(localEvent);
                        Log.d(TAG, "서버 ID 매핑: " + localEvent.getTitle() + " -> " + serverEvent.getId());
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "날짜 파싱 오류", e);
                }
            }
        }
    }
}