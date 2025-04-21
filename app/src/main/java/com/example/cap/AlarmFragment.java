package com.example.cap;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlarmFragment extends Fragment {

    private RecyclerView recyclerViewAlarms;
    private TextView textNoAlarms;
    private FloatingActionButton fabAddAlarm;
    private AlarmAdapter alarmAdapter;
    private List<AlarmItem> alarmList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alarm, container, false);

        // 뷰 초기화
        recyclerViewAlarms = view.findViewById(R.id.recyclerViewAlarms);
        textNoAlarms = view.findViewById(R.id.textNoAlarms);
        fabAddAlarm = view.findViewById(R.id.fabAddAlarm);

        // 리사이클러뷰 설정
        recyclerViewAlarms.setLayoutManager(new LinearLayoutManager(getContext()));
        alarmAdapter = new AlarmAdapter(alarmList);
        recyclerViewAlarms.setAdapter(alarmAdapter);

        // 알람 추가 버튼 클릭 리스너
        fabAddAlarm.setOnClickListener(v -> showTimePickerDialog());

        // 초기 알람 목록 로드 (실제로는 DB에서 가져와야 함)
        loadAlarms();

        return view;
    }

    /**
     * 알람 목록 로드 (실제로는 DB에서 가져와야 함)
     */
    private void loadAlarms() {
        // 임시 더미 데이터
        alarmList.clear();

        // 표시할 알람이 있으면 리사이클러뷰 표시, 없으면 메시지 표시
        updateAlarmListVisibility();
    }

    /**
     * 알람 목록 표시 상태 업데이트
     */
    private void updateAlarmListVisibility() {
        if (alarmList.isEmpty()) {
            textNoAlarms.setVisibility(View.VISIBLE);
            recyclerViewAlarms.setVisibility(View.GONE);
        } else {
            textNoAlarms.setVisibility(View.GONE);
            recyclerViewAlarms.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 시간 선택 다이얼로그 표시
     */
    private void showTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                getContext(),
                R.style.DialogTheme,
                (view, hourOfDay, selectedMinute) -> showAlarmLabelDialog(hourOfDay, selectedMinute),
                hour,
                minute,
                true
        );

        timePickerDialog.setTitle("알람 시간 설정");
        timePickerDialog.show();
    }

    /**
     * 알람 레이블 입력 다이얼로그 표시
     */
    private void showAlarmLabelDialog(int hourOfDay, int minute) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_alarm_label, null);

        EditText editAlarmLabel = dialogView.findViewById(R.id.editAlarmLabel);

        builder.setView(dialogView)
                .setTitle("알람 레이블")
                .setPositiveButton("설정", (dialog, id) -> {
                    String label = editAlarmLabel.getText().toString().trim();

                    // 레이블이 비어있으면 기본값 설정
                    if (label.isEmpty()) {
                        label = "알람";
                    }

                    // 알람 추가
                    addAlarm(hourOfDay, minute, label);
                })
                .setNegativeButton("취소", (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 알람 추가
     */
    private void addAlarm(int hourOfDay, int minute, String label) {
        // 알람 ID 생성 (실제로는 DB에서 관리)
        int alarmId = (int) System.currentTimeMillis();

        // 시간 문자열 생성
        String timeString = String.format("%02d:%02d", hourOfDay, minute);

        // 알람 아이템 생성 및 목록에 추가
        AlarmItem alarmItem = new AlarmItem(alarmId, timeString, label, true);
        alarmList.add(alarmItem);
        alarmAdapter.notifyDataSetChanged();

        // 리스트 가시성 업데이트
        updateAlarmListVisibility();

        // 실제 알람 설정 (AlarmManager 사용)
        scheduleAlarm(alarmId, hourOfDay, minute, label);

        Toast.makeText(getContext(), "알람이 " + timeString + "에 설정되었습니다.", Toast.LENGTH_SHORT).show();
    }

    /**
     * 실제 알람 스케줄링 (AlarmManager 사용)
     */
    private void scheduleAlarm(int alarmId, int hourOfDay, int minute, String label) {
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);

        // 알람 시간 설정
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // 현재 시간보다 이전이면 다음 날로 설정
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 인텐트 설정 (실제로는 AlarmReceiver 브로드캐스트 리시버를 생성해야 함)
        Intent intent = new Intent(getContext(), MainActivity.class); // 임시로 MainActivity로 설정
        intent.putExtra("ALARM_ID", alarmId);
        intent.putExtra("ALARM_LABEL", label);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getContext(),
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 알람 설정
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    /**
     * 알람 데이터 클래스
     */
    public static class AlarmItem {
        private int id;
        private String time;
        private String label;
        private boolean isEnabled;

        public AlarmItem(int id, String time, String label, boolean isEnabled) {
            this.id = id;
            this.time = time;
            this.label = label;
            this.isEnabled = isEnabled;
        }

        public int getId() {
            return id;
        }

        public String getTime() {
            return time;
        }

        public String getLabel() {
            return label;
        }

        public boolean isEnabled() {
            return isEnabled;
        }

        public void setEnabled(boolean enabled) {
            isEnabled = enabled;
        }
    }

    /**
     * 알람 어댑터
     */
    private class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder> {

        private List<AlarmItem> alarms;

        public AlarmAdapter(List<AlarmItem> alarms) {
            this.alarms = alarms;
        }

        @NonNull
        @Override
        public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_alarm, parent, false);
            return new AlarmViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
            AlarmItem alarm = alarms.get(position);
            holder.textTime.setText(alarm.getTime());
            holder.textLabel.setText(alarm.getLabel());

            // 알람 활성화/비활성화 상태 설정
            holder.switchAlarm.setChecked(alarm.isEnabled());

            // 알람 스위치 리스너
            holder.switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                alarm.setEnabled(isChecked);
                // 실제로는 여기서 알람 활성화/비활성화 처리
            });

            // 알람 삭제 버튼 리스너
            holder.buttonDelete.setOnClickListener(v -> {
                // 알람 삭제
                alarms.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, alarms.size());

                // 리스트 가시성 업데이트
                updateAlarmListVisibility();

                // 실제로는 여기서 AlarmManager에서 알람 취소 처리
                Toast.makeText(getContext(), "알람이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return alarms.size();
        }

        class AlarmViewHolder extends RecyclerView.ViewHolder {
            TextView textTime;
            TextView textLabel;
            android.widget.Switch switchAlarm;
            ImageButton buttonDelete;

            AlarmViewHolder(View itemView) {
                super(itemView);
                textTime = itemView.findViewById(R.id.textAlarmTime);
                textLabel = itemView.findViewById(R.id.textAlarmLabel);
                switchAlarm = itemView.findViewById(R.id.switchAlarm);
                buttonDelete = itemView.findViewById(R.id.buttonDeleteAlarm);
            }
        }
    }
}