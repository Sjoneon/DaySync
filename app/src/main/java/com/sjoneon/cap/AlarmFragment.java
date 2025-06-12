package com.sjoneon.cap;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

/**
 * 알람 설정 및 관리를 위한 프래그먼트 (수정본)
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
        // 임시 더미 데이터 (실제로는 DB에서 가져와야 함)
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
                (view, hourOfDay, selectedMinute) -> showAlarmLabelDialog(hourOfDay, selectedMinute),
                hour,
                minute,
                true
        );

        timePickerDialog.setTitle(getString(R.string.alarm_time_setting));
        timePickerDialog.show();
    }

    private void showAlarmLabelDialog(int hourOfDay, int minute) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_alarm_label, null);
        EditText editAlarmLabel = dialogView.findViewById(R.id.editAlarmLabel);

        builder.setView(dialogView)
                .setTitle(R.string.alarm_label_setting)
                .setPositiveButton(R.string.alarm_set, (dialog, id) -> {
                    String label = editAlarmLabel.getText().toString().trim();
                    if (label.isEmpty()) {
                        label = getString(R.string.default_alarm_label);
                    }
                    addAlarm(hourOfDay, minute, label);
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        builder.create().show();
    }

    private void addAlarm(int hourOfDay, int minute, String label) {
        int alarmId = (int) System.currentTimeMillis();
        String timeString = String.format("%02d:%02d", hourOfDay, minute);
        AlarmItem alarmItem = new AlarmItem(alarmId, timeString, label, true);
        alarmList.add(alarmItem);
        alarmAdapter.notifyDataSetChanged();
        updateAlarmListVisibility();

        // 실제 알람 설정 (AlarmManager 사용)
        scheduleAlarm(alarmId, hourOfDay, minute, label);
    }

    /**
     * 실제 알람 스케줄링 (AlarmManager 사용) - 수정된 부분
     */
    private void scheduleAlarm(int alarmId, int hourOfDay, int minute, String label) {
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);

        // Android 12 이상에서는 SCHEDULE_EXACT_ALARM 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // 권한이 없는 경우 사용자에게 설정 화면으로 이동하도록 요청
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivityForResult(intent, REQUEST_SCHEDULE_EXACT_ALARM);
                Toast.makeText(getContext(), "알람 설정을 위해 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                return; // 권한이 없으므로 알람 설정 중단
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        Intent intent = new Intent(getContext(), MainActivity.class); // 임시로 MainActivity로 설정
        intent.putExtra("ALARM_ID", alarmId);
        intent.putExtra("ALARM_LABEL", label);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getContext(),
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 권한이 있는 경우 알람 설정
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            String timeString = String.format("%02d:%02d", hourOfDay, minute);
            Toast.makeText(getContext(), getString(R.string.alarm_time_set, timeString), Toast.LENGTH_SHORT).show();
        }
    }

    // 사용자가 권한 설정 후 돌아왔을 때 처리
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCHEDULE_EXACT_ALARM) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    Toast.makeText(getContext(), "권한이 허용되었습니다. 다시 알람을 설정해주세요.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "권한이 거부되었습니다. 알람을 설정할 수 없습니다.", Toast.LENGTH_SHORT).show();
                }
            }
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

        public int getId() { return id; }
        public String getTime() { return time; }
        public String getLabel() { return label; }
        public boolean isEnabled() { return isEnabled; }
        public void setEnabled(boolean enabled) { isEnabled = enabled; }
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
            return new AlarmViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
            AlarmItem alarm = alarms.get(position);
            holder.textTime.setText(alarm.getTime());
            holder.textLabel.setText(alarm.getLabel());
            holder.switchAlarm.setChecked(alarm.isEnabled());

            holder.switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                alarm.setEnabled(isChecked);
            });

            holder.buttonDelete.setOnClickListener(v -> {
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