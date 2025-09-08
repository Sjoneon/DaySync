package com.sjoneon.cap;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 개선된 일정 관리 프래그먼트
 * - 완전한 CRUD 기능
 * - 데이터 영구 저장
 * - 알림 설정 기능
 */
public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerViewEvents;
    private FloatingActionButton fabAddEvent;
    private TextView textNoEvents;

    private CalendarEventRepository eventRepository;
    private EventAlarmManager alarmManager;
    private CalendarEventAdapter eventAdapter;
    private List<CalendarEvent> currentEvents = new ArrayList<>();

    // 현재 선택된 날짜
    private long selectedDate = System.currentTimeMillis();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        android.util.Log.d("CalendarFragment", "onCreateView called");

        // 뷰 초기화
        calendarView = view.findViewById(R.id.calendarView);
        recyclerViewEvents = view.findViewById(R.id.recyclerViewEvents);
        fabAddEvent = view.findViewById(R.id.fabAddEvent);
        textNoEvents = view.findViewById(R.id.textNoEvents);

        android.util.Log.d("CalendarFragment", "Views initialized");

        // 저장소 및 알림 매니저 초기화
        eventRepository = CalendarEventRepository.getInstance(requireContext());
        alarmManager = new EventAlarmManager(requireContext());

        android.util.Log.d("CalendarFragment", "Repository and AlarmManager initialized");

        // 초기 설정
        setupCalendarView();
        setupRecyclerView();
        setupFab();

        android.util.Log.d("CalendarFragment", "Setup methods called");

        // 초기 데이터 로드
        loadEventsForDate(selectedDate);

        android.util.Log.d("CalendarFragment", "Initial data loaded");

        return view;
    }

    /**
     * 캘린더 뷰 설정
     */
    private void setupCalendarView() {
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, dayOfMonth);
                selectedDate = calendar.getTimeInMillis();

                // 해당 날짜의 일정 로드
                loadEventsForDate(selectedDate);
            }
        });
    }

    /**
     * 리사이클러뷰 설정
     */
    private void setupRecyclerView() {
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        eventAdapter = new CalendarEventAdapter();
        recyclerViewEvents.setAdapter(eventAdapter);
    }

    /**
     * 플로팅 액션 버튼 설정
     */
    private void setupFab() {
        fabAddEvent.setOnClickListener(v -> showAddEventDialog());
    }

    /**
     * 특정 날짜의 일정을 로드
     */
    private void loadEventsForDate(long date) {
        currentEvents = eventRepository.getEventsForDate(date);
        eventAdapter.updateEvents(currentEvents);

        // 일정이 없는 경우 메시지 표시
        if (currentEvents.isEmpty()) {
            textNoEvents.setVisibility(View.VISIBLE);
            recyclerViewEvents.setVisibility(View.GONE);
        } else {
            textNoEvents.setVisibility(View.GONE);
            recyclerViewEvents.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 일정 추가 다이얼로그 표시
     */
    private void showAddEventDialog() {
        showEventDialog(null, false);
    }

    /**
     * 일정 수정 다이얼로그 표시
     */
    private void showEditEventDialog(CalendarEvent event) {
        showEventDialog(event, true);
    }

    /**
     * 일정 추가/수정 다이얼로그
     */
    private void showEventDialog(CalendarEvent existingEvent, boolean isEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_event, null);

        EditText editTitle = dialogView.findViewById(R.id.editEventTitle);
        EditText editDescription = dialogView.findViewById(R.id.editEventDescription);
        TextView textSelectedDateTime = dialogView.findViewById(R.id.textSelectedDateTime);
        LinearLayout layoutNotificationSettings = dialogView.findViewById(R.id.layoutNotificationSettings);

        // 기존 일정 정보 설정 (수정 모드)
        Calendar eventCalendar = Calendar.getInstance();
        if (isEdit && existingEvent != null) {
            editTitle.setText(existingEvent.getTitle());
            editDescription.setText(existingEvent.getDescription());
            eventCalendar.setTimeInMillis(existingEvent.getDateTime());
        } else {
            // 새 일정의 경우 선택된 날짜와 현재 시간 설정
            eventCalendar.setTimeInMillis(selectedDate);
            eventCalendar.set(Calendar.HOUR_OF_DAY, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
            eventCalendar.set(Calendar.MINUTE, Calendar.getInstance().get(Calendar.MINUTE));
        }

        // 날짜/시간 표시 업데이트
        updateDateTimeDisplay(textSelectedDateTime, eventCalendar.getTimeInMillis());

        // 날짜/시간 선택 리스너
        textSelectedDateTime.setOnClickListener(v -> showDateTimePicker(eventCalendar, textSelectedDateTime));

        // 알림 설정 체크박스들 추가
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

        // 커스텀 save 버튼 처리
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                android.util.Log.d("CalendarFragment", "Save button clicked!");

                String title = editTitle.getText().toString().trim();
                String description = editDescription.getText().toString().trim();

                android.util.Log.d("CalendarFragment", "Title: " + title + ", Description: " + description);

                if (title.isEmpty()) {
                    Toast.makeText(getContext(), R.string.empty_event_title, Toast.LENGTH_SHORT).show();
                    return;
                }

                // 알림 설정 수집
                List<CalendarEvent.NotificationSetting> notificationSettings = collectNotificationSettings(layoutNotificationSettings);
                android.util.Log.d("CalendarFragment", "Notification settings collected: " + notificationSettings.size());

                long eventDateTime = eventCalendar.getTimeInMillis();
                android.util.Log.d("CalendarFragment", "Event date time: " + new java.util.Date(eventDateTime));

                if (isEdit && existingEvent != null) {
                    android.util.Log.d("CalendarFragment", "Updating existing event");
                    // 일정 수정
                    updateEvent(existingEvent, title, description, eventDateTime, notificationSettings);
                } else {
                    android.util.Log.d("CalendarFragment", "Creating new event");
                    // 새 일정 추가
                    createNewEvent(title, description, eventDateTime, notificationSettings);
                }

                dialog.dismiss();

            } catch (Exception e) {
                android.util.Log.e("CalendarFragment", "Error in save button click", e);
                Toast.makeText(getContext(), "저장 중 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 날짜/시간 선택기 표시
     */
    private void showDateTimePicker(Calendar calendar, TextView displayTextView) {
        // 날짜 선택
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    // 시간 선택
                    TimePickerDialog timePickerDialog = new TimePickerDialog(
                            requireContext(),
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.set(Calendar.MILLISECOND, 0);

                                updateDateTimeDisplay(displayTextView, calendar.getTimeInMillis());
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    timePickerDialog.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * 날짜/시간 표시 업데이트
     */
    private void updateDateTimeDisplay(TextView textView, long dateTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault());
        textView.setText(sdf.format(new Date(dateTime)));
    }

    /**
     * 알림 설정 체크박스 설정
     */
    private void setupNotificationCheckboxes(LinearLayout container, CalendarEvent existingEvent) {
        container.removeAllViews();

        CalendarEvent.NotificationSetting.NotificationType[] types =
                CalendarEvent.NotificationSetting.NotificationType.values();

        for (CalendarEvent.NotificationSetting.NotificationType type : types) {
            CheckBox checkBox = new CheckBox(getContext());
            checkBox.setText(type.getDisplayName());
            checkBox.setTextColor(getResources().getColor(R.color.text_primary));
            checkBox.setTag(type);

            // 기존 일정의 알림 설정 확인
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

    /**
     * 알림 설정 수집
     */
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

    /**
     * 새 일정 생성
     */
    private void createNewEvent(String title, String description, long dateTime,
                                List<CalendarEvent.NotificationSetting> notificationSettings) {
        CalendarEvent newEvent = new CalendarEvent(title, description, dateTime);
        newEvent.setNotificationSettings(notificationSettings);

        long eventId = eventRepository.addEvent(newEvent);
        newEvent.setId(eventId);

        // 알림 스케줄링
        alarmManager.scheduleEventNotifications(newEvent);

        // UI 업데이트
        loadEventsForDate(selectedDate);
        Toast.makeText(getContext(), R.string.event_added, Toast.LENGTH_SHORT).show();
    }

    /**
     * 일정 업데이트
     */
    private void updateEvent(CalendarEvent event, String title, String description, long dateTime,
                             List<CalendarEvent.NotificationSetting> notificationSettings) {
        // 기존 알림 취소
        alarmManager.cancelEventNotifications(event);

        // 일정 정보 업데이트
        event.setTitle(title);
        event.setDescription(description);
        event.setDateTime(dateTime);
        event.setNotificationSettings(notificationSettings);

        // 저장소에 업데이트
        eventRepository.updateEvent(event);

        // 새로운 알림 스케줄링
        alarmManager.scheduleEventNotifications(event);

        // UI 업데이트
        loadEventsForDate(selectedDate);
        Toast.makeText(getContext(), "일정이 수정되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /**
     * 삭제 확인 다이얼로그
     */
    private void showDeleteConfirmationDialog(CalendarEvent event) {
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("일정 삭제")
                .setMessage("정말로 이 일정을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> deleteEvent(event))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 일정 삭제
     */
    private void deleteEvent(CalendarEvent event) {
        // 알림 취소
        alarmManager.cancelEventNotifications(event);

        // 저장소에서 삭제
        eventRepository.deleteEvent(event.getId());

        // UI 업데이트
        loadEventsForDate(selectedDate);
        Toast.makeText(getContext(), "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /**
     * 일정 어댑터
     */
    private class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {

        public void updateEvents(List<CalendarEvent> events) {
            currentEvents.clear();
            currentEvents.addAll(events);
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
            CalendarEvent event = currentEvents.get(position);

            holder.textTitle.setText(event.getTitle());
            holder.textDescription.setText(event.getDescription());

            // 시간 표시
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            holder.textTime.setText(timeFormat.format(new Date(event.getDateTime())));

            // 지난 일정 표시
            if (event.isPastEvent()) {
                holder.textTitle.setAlpha(0.6f);
                holder.textDescription.setAlpha(0.6f);
                holder.textTime.setAlpha(0.6f);
            } else {
                holder.textTitle.setAlpha(1.0f);
                holder.textDescription.setAlpha(1.0f);
                holder.textTime.setAlpha(1.0f);
            }

            // 클릭 리스너 (수정 모드로 이동)
            holder.itemView.setOnClickListener(v -> showEditEventDialog(event));

            // 길게 누르기 리스너 (삭제 옵션)
            holder.itemView.setOnLongClickListener(v -> {
                showDeleteConfirmationDialog(event);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return currentEvents.size();
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
        // 화면이 다시 표시될 때 데이터 새로고침
        loadEventsForDate(selectedDate);
    }
}