package com.sjoneon.cap;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputFilter;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 알람 설정 및 관리를 위한 프래그먼트 (완전 기능 구현)
 */
public class AlarmFragment extends Fragment {

    private RecyclerView recyclerViewAlarms;
    private TextView textNoAlarms;
    private FloatingActionButton fabAddAlarm;
    private AlarmAdapter alarmAdapter;
    private List<AlarmItem> alarmList = new ArrayList<>();
    private static final int REQUEST_SCHEDULE_EXACT_ALARM = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alarm, container, false);

        recyclerViewAlarms = view.findViewById(R.id.recyclerViewAlarms);
        textNoAlarms = view.findViewById(R.id.textNoAlarms);
        fabAddAlarm = view.findViewById(R.id.fabAddAlarm);

        recyclerViewAlarms.setLayoutManager(new LinearLayoutManager(getContext()));
        alarmAdapter = new AlarmAdapter(alarmList);
        recyclerViewAlarms.setAdapter(alarmAdapter);

        fabAddAlarm.setOnClickListener(v -> showTimePickerDialog());

        loadAlarms();

        return view;
    }

    private void loadAlarms() {
        // 실제 구현에서는 데이터베이스에서 알람 목록을 로드해야 함
        // 여기서는 임시로 저장된 알람들을 불러오는 로직을 구현
        alarmList.clear();
        updateAlarmListVisibility();
    }

    private void updateAlarmListVisibility() {
        if (alarmList.isEmpty()) {
            textNoAlarms.setVisibility(View.VISIBLE);
            recyclerViewAlarms.setVisibility(View.GONE);
        } else {
            textNoAlarms.setVisibility(View.GONE);
            recyclerViewAlarms.setVisibility(View.VISIBLE);
        }
    }

    private void showTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                getContext(),
                R.style.DialogTheme,
                (view, hourOfDay, selectedMinute) -> showAlarmSettingsDialog(hourOfDay, selectedMinute),
                hour,
                minute,
                true
        );

        timePickerDialog.setTitle(getString(R.string.alarm_time_setting));
        timePickerDialog.show();
    }

    private void showAlarmSettingsDialog(int hourOfDay, int minute) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_alarm_setting, null);

        EditText editAlarmLabel = dialogView.findViewById(R.id.editAlarmLabel);
        CheckBox checkBoxSound = dialogView.findViewById(R.id.checkBoxSound);
        CheckBox checkBoxVibration = dialogView.findViewById(R.id.checkBoxVibration);

        // 입력 길이 제한 (25자)
        editAlarmLabel.setFilters(new InputFilter[]{new InputFilter.LengthFilter(25)});

        // 기본값 설정
        checkBoxSound.setChecked(true);
        checkBoxVibration.setChecked(true);

        builder.setView(dialogView)
                .setTitle("알람 설정")
                .setPositiveButton("알람 설정", (dialog, id) -> {
                    String label = editAlarmLabel.getText().toString().trim();
                    if (label.isEmpty()) {
                        label = "알람";
                    }

                    boolean soundEnabled = checkBoxSound.isChecked();
                    boolean vibrationEnabled = checkBoxVibration.isChecked();

                    addAlarm(hourOfDay, minute, label, soundEnabled, vibrationEnabled);
                })
                .setNegativeButton("취소", (dialog, id) -> dialog.cancel());

        builder.create().show();
    }

    private void addAlarm(int hourOfDay, int minute, String label, boolean soundEnabled, boolean vibrationEnabled) {
        int alarmId = (int) System.currentTimeMillis();
        String timeString = String.format("%02d:%02d", hourOfDay, minute);

        // 알람 설정 저장
        AlarmScheduler.saveAlarmSettings(requireContext(), alarmId, soundEnabled, vibrationEnabled);

        // 실제 알람 스케줄링
        boolean success = AlarmScheduler.scheduleAlarm(requireContext(), alarmId, hourOfDay, minute, label);

        if (success) {
            AlarmItem alarmItem = new AlarmItem(alarmId, timeString, label, true, soundEnabled, vibrationEnabled);
            alarmList.add(alarmItem);
            alarmAdapter.notifyDataSetChanged();
            updateAlarmListVisibility();

            Toast.makeText(getContext(), getString(R.string.alarm_time_set, timeString), Toast.LENGTH_SHORT).show();
        } else {
            // 권한이 없는 경우 설정 화면으로 이동
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivityForResult(intent, REQUEST_SCHEDULE_EXACT_ALARM);
                Toast.makeText(getContext(), "알람 설정을 위해 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "알람 설정에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCHEDULE_EXACT_ALARM) {
            Toast.makeText(getContext(), "권한 설정 후 다시 알람을 설정해주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 알람 데이터 클래스 (확장됨)
     */
    public static class AlarmItem {
        private int id;
        private String time;
        private String label;
        private boolean isEnabled;
        private boolean soundEnabled;
        private boolean vibrationEnabled;

        // Gson을 위한 기본 생성자
        public AlarmItem() {}

        public AlarmItem(int id, String time, String label, boolean isEnabled, boolean soundEnabled, boolean vibrationEnabled) {
            this.id = id;
            this.time = time;
            this.label = label;
            this.isEnabled = isEnabled;
            this.soundEnabled = soundEnabled;
            this.vibrationEnabled = vibrationEnabled;
        }

        // Getters
        public int getId() { return id; }
        public String getTime() { return time; }
        public String getLabel() { return label; }
        public boolean isEnabled() { return isEnabled; }
        public boolean isSoundEnabled() { return soundEnabled; }
        public boolean isVibrationEnabled() { return vibrationEnabled; }

        // Setters
        public void setEnabled(boolean enabled) { isEnabled = enabled; }
        public void setLabel(String label) { this.label = label; }
        public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
        public void setVibrationEnabled(boolean vibrationEnabled) { this.vibrationEnabled = vibrationEnabled; }
    }

    /**
     * 알람 어댑터 (확장됨)
     */
    private class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder> {
        private List<AlarmItem> alarms;

        public AlarmAdapter(List<AlarmItem> alarms) {
            this.alarms = alarms;
        }

        @NonNull
        @Override
        public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
            return new AlarmViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
            AlarmItem alarm = alarms.get(position);
            holder.textTime.setText(alarm.getTime());
            holder.textLabel.setText(alarm.getLabel());

            // 설정 정보 표시
            String settings = "";
            if (alarm.isSoundEnabled()) settings += "🔊 ";
            if (alarm.isVibrationEnabled()) settings += "📳 ";
            if (settings.isEmpty()) settings = "🔇 ";

            holder.textSettings.setText(settings.trim());
            holder.switchAlarm.setChecked(alarm.isEnabled());

            holder.switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                alarm.setEnabled(isChecked);
                if (isChecked) {
                    // 알람 다시 활성화
                    String[] timeParts = alarm.getTime().split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);
                    AlarmScheduler.scheduleAlarm(getContext(), alarm.getId(), hour, minute, alarm.getLabel());
                } else {
                    // 알람 비활성화
                    AlarmScheduler.cancelAlarm(getContext(), alarm.getId());
                }
            });

            // 설정 버튼 클릭 리스너
            holder.buttonSettings.setOnClickListener(v -> showAlarmEditDialog(alarm, position));

            holder.buttonDelete.setOnClickListener(v -> {
                // 알람 취소
                AlarmScheduler.cancelAlarm(getContext(), alarm.getId());

                alarms.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, alarms.size());
                updateAlarmListVisibility();
                Toast.makeText(getContext(), R.string.alarm_deleted, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return alarms.size();
        }

        class AlarmViewHolder extends RecyclerView.ViewHolder {
            TextView textTime;
            TextView textLabel;
            TextView textSettings;
            android.widget.Switch switchAlarm;
            ImageButton buttonSettings;
            ImageButton buttonDelete;

            AlarmViewHolder(View itemView) {
                super(itemView);
                textTime = itemView.findViewById(R.id.textAlarmTime);
                textLabel = itemView.findViewById(R.id.textAlarmLabel);
                textSettings = itemView.findViewById(R.id.textAlarmSettings);
                switchAlarm = itemView.findViewById(R.id.switchAlarm);
                buttonSettings = itemView.findViewById(R.id.buttonAlarmSettings);
                buttonDelete = itemView.findViewById(R.id.buttonDeleteAlarm);
            }
        }
    }

    private void showAlarmEditDialog(AlarmItem alarm, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_alarm_setting, null);

        EditText editAlarmLabel = dialogView.findViewById(R.id.editAlarmLabel);
        CheckBox checkBoxSound = dialogView.findViewById(R.id.checkBoxSound);
        CheckBox checkBoxVibration = dialogView.findViewById(R.id.checkBoxVibration);

        // 입력 길이 제한 (25자)
        editAlarmLabel.setFilters(new InputFilter[]{new InputFilter.LengthFilter(25)});

        // 현재 설정값으로 초기화
        editAlarmLabel.setText(alarm.getLabel());
        checkBoxSound.setChecked(alarm.isSoundEnabled());
        checkBoxVibration.setChecked(alarm.isVibrationEnabled());

        builder.setView(dialogView)
                .setTitle("알람 수정")
                .setPositiveButton("저장", (dialog, id) -> {
                    String label = editAlarmLabel.getText().toString().trim();
                    if (label.isEmpty()) {
                        label = getString(R.string.default_alarm_label);
                    }

                    boolean soundEnabled = checkBoxSound.isChecked();
                    boolean vibrationEnabled = checkBoxVibration.isChecked();

                    // 설정 업데이트
                    alarm.setLabel(label);
                    alarm.setSoundEnabled(soundEnabled);
                    alarm.setVibrationEnabled(vibrationEnabled);

                    // 알람 설정 저장
                    AlarmScheduler.saveAlarmSettings(requireContext(), alarm.getId(), soundEnabled, vibrationEnabled);

                    // 알람이 활성화되어 있으면 다시 스케줄링
                    if (alarm.isEnabled()) {
                        String[] timeParts = alarm.getTime().split(":");
                        int hour = Integer.parseInt(timeParts[0]);
                        int minute = Integer.parseInt(timeParts[1]);
                        AlarmScheduler.scheduleAlarm(requireContext(), alarm.getId(), hour, minute, label);
                    }

                    alarmAdapter.notifyItemChanged(position);
                    Toast.makeText(getContext(), "알람 설정이 변경되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        builder.create().show();
    }
}