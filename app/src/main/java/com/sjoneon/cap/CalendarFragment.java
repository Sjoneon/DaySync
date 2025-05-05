package com.sjoneon.cap;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
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
 * 일정 관리 기능을 제공하는 프래그먼트
 */
public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerViewEvents;
    private FloatingActionButton fabAddEvent;

    // 임시 이벤트 데이터 (실제로는 데이터베이스에서 가져와야 함)
    private List<CalendarEvent> eventList = new ArrayList<>();
    private CalendarEventAdapter eventAdapter;

    // 현재 선택된 날짜
    private long selectedDate = System.currentTimeMillis();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        // 뷰 초기화
        calendarView = view.findViewById(R.id.calendarView);
        recyclerViewEvents = view.findViewById(R.id.recyclerViewEvents);
        fabAddEvent = view.findViewById(R.id.fabAddEvent);

        // 초기 설정
        setupCalendarView();
        setupRecyclerView();
        setupFab();

        return view;
    }

    /**
     * 캘린더 뷰 설정
     */
    private void setupCalendarView() {
        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                // 선택된 날짜 저장
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, dayOfMonth);
                selectedDate = calendar.getTimeInMillis();

                // 해당 날짜의 일정 로드 (실제로는 데이터베이스 쿼리)
                loadEventsForDate(selectedDate);
            }
        });
    }

    /**
     * 리사이클러뷰 설정
     */
    private void setupRecyclerView() {
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        eventAdapter = new CalendarEventAdapter(eventList);
        recyclerViewEvents.setAdapter(eventAdapter);
    }

    /**
     * 플로팅 액션 버튼 설정
     */
    private void setupFab() {
        fabAddEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddEventDialog();
            }
        });
    }

    /**
     * 특정 날짜의 일정을 로드
     * @param date 날짜
     */
    private void loadEventsForDate(long date) {
        // 실제 구현에서는 데이터베이스에서 해당 날짜의 일정을 가져와야 함
        // 여기서는 임시로 더미 데이터를 설정
        eventList.clear();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(new Date(date));

        // 더미 데이터
        if (dateString.equals(sdf.format(new Date()))) {
            // 오늘 날짜인 경우 예시 일정 추가
            eventList.add(new CalendarEvent("아침 회의", "09:00", "화상 회의 - 팀 미팅"));
            eventList.add(new CalendarEvent("점심", "12:30", "친구와 점심 약속"));
            eventList.add(new CalendarEvent("저녁 수업", "18:00", "온라인 강의"));
        }

        eventAdapter.notifyDataSetChanged();

        // 일정이 없는 경우 메시지 표시
        if (eventList.isEmpty()) {
            Toast.makeText(getContext(), R.string.no_events, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 일정 추가 다이얼로그 표시
     */
    private void showAddEventDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_event, null);

        EditText editTitle = dialogView.findViewById(R.id.editEventTitle);
        EditText editDescription = dialogView.findViewById(R.id.editEventDescription);
        TimePicker timePicker = dialogView.findViewById(R.id.timePickerEvent);

        timePicker.setIs24HourView(true);

        builder.setView(dialogView)
                .setTitle(R.string.add_event)
                .setPositiveButton(R.string.save, (dialog, id) -> {
                    // 입력 데이터 가져오기
                    String title = editTitle.getText().toString().trim();
                    String description = editDescription.getText().toString().trim();

                    if (title.isEmpty()) {
                        Toast.makeText(getContext(), R.string.empty_event_title, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 시간 포맷팅
                    String timeString = String.format(Locale.getDefault(), "%02d:%02d",
                            timePicker.getHour(), timePicker.getMinute());

                    // 새 일정 추가 (실제로는 데이터베이스에 저장)
                    CalendarEvent newEvent = new CalendarEvent(title, timeString, description);
                    eventList.add(newEvent);
                    eventAdapter.notifyDataSetChanged();

                    Toast.makeText(getContext(), R.string.event_added, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 일정 데이터 클래스
     */
    public static class CalendarEvent {
        private String title;
        private String time;
        private String description;

        public CalendarEvent(String title, String time, String description) {
            this.title = title;
            this.time = time;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getTime() {
            return time;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 일정 어댑터
     */
    private class CalendarEventAdapter extends RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder> {

        private List<CalendarEvent> events;

        public CalendarEventAdapter(List<CalendarEvent> events) {
            this.events = events;
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
            CalendarEvent event = events.get(position);
            holder.textTitle.setText(event.getTitle());
            holder.textTime.setText(event.getTime());
            holder.textDescription.setText(event.getDescription());
        }

        @Override
        public int getItemCount() {
            return events.size();
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
}