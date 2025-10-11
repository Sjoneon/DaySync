package com.sjoneon.cap.fragments;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sjoneon.cap.R;
import com.sjoneon.cap.helpers.AlarmScheduler;
import com.sjoneon.cap.repositories.AlarmRepository;
import com.sjoneon.cap.utils.ApiClient;
import com.sjoneon.cap.models.api.AlarmRequest;
import com.sjoneon.cap.models.api.AlarmResponse;
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

public class AlarmFragment extends Fragment {

    private static final String TAG = "AlarmFragment";
    private RecyclerView recyclerViewAlarms;
    private TextView textNoAlarms;
    private FloatingActionButton fabAddAlarm;
    private AlarmAdapter alarmAdapter;
    private List<AlarmItem> alarmList = new ArrayList<>();
    private AlarmRepository alarmRepository;
    private static final int REQUEST_SCHEDULE_EXACT_ALARM = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alarm, container, false);

        recyclerViewAlarms = view.findViewById(R.id.recyclerViewAlarms);
        textNoAlarms = view.findViewById(R.id.textNoAlarms);
        fabAddAlarm = view.findViewById(R.id.fabAddAlarm);

        alarmRepository = AlarmRepository.getInstance(requireContext());

        recyclerViewAlarms.setLayoutManager(new LinearLayoutManager(getContext()));
        alarmAdapter = new AlarmAdapter(alarmList);
        recyclerViewAlarms.setAdapter(alarmAdapter);

        fabAddAlarm.setOnClickListener(v -> showTimePickerDialog());

        loadAlarmsFromRepository();
        updateAlarmListVisibility();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        loadAlarmsFromRepository();
        syncWithServer();
    }

    private void loadAlarmsFromRepository() {
        alarmList.clear();
        alarmList.addAll(alarmRepository.getAllAlarms());
        if (alarmAdapter != null) {
            alarmAdapter.notifyDataSetChanged();
        }
        updateAlarmListVisibility();
    }

    private void syncWithServer() {
        SharedPreferences preferences = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userUuid = preferences.getString("user_uuid", null);

        if (userUuid == null || ApiClient.getInstance() == null) {
            return;
        }

        ApiClient.getInstance().getApiService()
                .getUserAlarms(userUuid)
                .enqueue(new Callback<List<AlarmResponse>>() {
                    @Override
                    public void onResponse(Call<List<AlarmResponse>> call,
                                           Response<List<AlarmResponse>> response) {
                        if (response != null && response.isSuccessful() && response.body() != null) {
                            mapServerIdsToLocalAlarms(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<AlarmResponse>> call, Throwable t) {
                        Log.e(TAG, "서버 동기화 실패", t);
                    }
                });
    }

    private void mapServerIdsToLocalAlarms(List<AlarmResponse> serverAlarms) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat serverFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

        for (AlarmItem localAlarm : alarmList) {
            if (localAlarm.getServerId() != null) {
                continue;
            }

            for (AlarmResponse serverAlarm : serverAlarms) {
                if (!localAlarm.getLabel().equals(serverAlarm.getLabel())) {
                    continue;
                }

                try {
                    Date serverTime = serverFormat.parse(serverAlarm.getAlarmTime());
                    String serverTimeStr = timeFormat.format(serverTime);

                    if (serverTimeStr.equals(localAlarm.getTime())) {
                        localAlarm.setServerId(serverAlarm.getId());
                        alarmRepository.updateAlarm(localAlarm);
                        Log.d(TAG, "서버 ID 매핑: " + localAlarm.getLabel() + " -> " + serverAlarm.getId());
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "시간 파싱 오류", e);
                }
            }
        }
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

        editAlarmLabel.setFilters(new InputFilter[]{new InputFilter.LengthFilter(25)});

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

    private void addAlarm(int hourOfDay, int minute, String label,
                          boolean soundEnabled, boolean vibrationEnabled) {
        int alarmId = (int) System.currentTimeMillis();
        String timeString = String.format("%02d:%02d", hourOfDay, minute);

        AlarmScheduler.saveAlarmSettings(requireContext(), alarmId, soundEnabled, vibrationEnabled);

        boolean success = AlarmScheduler.scheduleAlarm(requireContext(), alarmId,
                hourOfDay, minute, label);

        if (success) {
            AlarmItem alarmItem = new AlarmItem(alarmId, timeString, label,
                    true, soundEnabled, vibrationEnabled);
            alarmList.add(alarmItem);

            alarmRepository.addAlarm(alarmItem);

            alarmAdapter.notifyDataSetChanged();
            updateAlarmListVisibility();

            createAlarmOnServer(hourOfDay, minute, label, alarmItem);

            Toast.makeText(getContext(), getString(R.string.alarm_time_set, timeString),
                    Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + requireContext().getPackageName()));
                startActivityForResult(intent, REQUEST_SCHEDULE_EXACT_ALARM);
                Toast.makeText(getContext(), "알람 설정을 위해 권한을 허용해주세요.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "알람 설정에 실패했습니다.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createAlarmOnServer(int hourOfDay, int minute, String label, AlarmItem localAlarm) {
        SharedPreferences preferences = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userUuid = preferences.getString("user_uuid", null);

        if (userUuid == null || ApiClient.getInstance() == null) {
            Log.e(TAG, "서버에 알람 추가 불가");
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        String alarmTime = sdf.format(calendar.getTime());

        AlarmRequest request = new AlarmRequest(userUuid, alarmTime, label);

        ApiClient.getInstance().getApiService()
                .createAlarm(request)
                .enqueue(new Callback<AlarmResponse>() {
                    @Override
                    public void onResponse(Call<AlarmResponse> call,
                                           Response<AlarmResponse> response) {
                        if (response != null && response.isSuccessful() && response.body() != null) {
                            int serverId = response.body().getId();
                            Log.d(TAG, "서버에 알람 추가 성공, ID: " + serverId);

                            localAlarm.setServerId(serverId);
                            alarmRepository.updateAlarm(localAlarm);
                        }
                    }

                    @Override
                    public void onFailure(Call<AlarmResponse> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                    }
                });
    }

    private void deleteAlarmFromServer(int serverId) {
        if (ApiClient.getInstance() == null) {
            Log.e(TAG, "ApiClient 없음");
            return;
        }

        ApiClient.getInstance().getApiService()
                .deleteAlarm(serverId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                        if (response != null && response.isSuccessful()) {
                            Log.d(TAG, "서버에서 알람 삭제 성공: ID=" + serverId);
                        } else {
                            Log.e(TAG, "서버 알람 삭제 실패: " +
                                    (response != null ? response.code() : "null"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse> call, Throwable t) {
                        Log.e(TAG, "서버 통신 실패", t);
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCHEDULE_EXACT_ALARM) {
            Toast.makeText(getContext(), "권한 설정 후 다시 알람을 설정해주세요.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static class AlarmItem {
        private int id;
        private String time;
        private String label;
        private boolean isEnabled;
        private boolean soundEnabled;
        private boolean vibrationEnabled;
        private Integer serverId;

        public AlarmItem() {}

        public AlarmItem(int id, String time, String label, boolean isEnabled,
                         boolean soundEnabled, boolean vibrationEnabled) {
            this.id = id;
            this.time = time;
            this.label = label;
            this.isEnabled = isEnabled;
            this.soundEnabled = soundEnabled;
            this.vibrationEnabled = vibrationEnabled;
        }

        public int getId() { return id; }
        public String getTime() { return time; }
        public String getLabel() { return label; }
        public boolean isEnabled() { return isEnabled; }
        public boolean isSoundEnabled() { return soundEnabled; }
        public boolean isVibrationEnabled() { return vibrationEnabled; }
        public Integer getServerId() { return serverId; }

        public void setEnabled(boolean enabled) { isEnabled = enabled; }
        public void setLabel(String label) { this.label = label; }
        public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
        public void setVibrationEnabled(boolean vibrationEnabled) { this.vibrationEnabled = vibrationEnabled; }
        public void setServerId(Integer serverId) { this.serverId = serverId; }
    }

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

            String settings = "";
            if (alarm.isSoundEnabled()) settings += "소리 ";
            if (alarm.isVibrationEnabled()) settings += "진동 ";
            if (settings.isEmpty()) settings = "무음 ";

            holder.textSettings.setText(settings.trim());
            holder.switchAlarm.setChecked(alarm.isEnabled());

            holder.switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                alarm.setEnabled(isChecked);

                alarmRepository.updateAlarm(alarm);

                if (isChecked) {
                    String[] timeParts = alarm.getTime().split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);
                    AlarmScheduler.scheduleAlarm(getContext(), alarm.getId(),
                            hour, minute, alarm.getLabel());
                } else {
                    AlarmScheduler.cancelAlarm(getContext(), alarm.getId());
                }
            });

            holder.buttonSettings.setOnClickListener(v -> showAlarmEditDialog(alarm, position));

            holder.buttonDelete.setOnClickListener(v -> {
                AlarmScheduler.cancelAlarm(getContext(), alarm.getId());

                alarmRepository.deleteAlarm(alarm.getId());

                if (alarm.getServerId() != null) {
                    deleteAlarmFromServer(alarm.getServerId());
                } else {
                    Log.w(TAG, "서버 ID가 없어 로컬에서만 삭제됨");
                }

                alarms.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, alarms.size());
                updateAlarmListVisibility();

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
            TextView textSettings;
            Switch switchAlarm;
            ImageButton buttonSettings;
            ImageButton buttonDelete;

            AlarmViewHolder(@NonNull View itemView) {
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

        editAlarmLabel.setFilters(new InputFilter[]{new InputFilter.LengthFilter(25)});

        editAlarmLabel.setText(alarm.getLabel());
        checkBoxSound.setChecked(alarm.isSoundEnabled());
        checkBoxVibration.setChecked(alarm.isVibrationEnabled());

        builder.setView(dialogView)
                .setTitle("알람 수정")
                .setPositiveButton("저장", (dialog, id) -> {
                    String label = editAlarmLabel.getText().toString().trim();
                    if (label.isEmpty()) {
                        label = "알람";
                    }

                    boolean soundEnabled = checkBoxSound.isChecked();
                    boolean vibrationEnabled = checkBoxVibration.isChecked();

                    alarm.setLabel(label);
                    alarm.setSoundEnabled(soundEnabled);
                    alarm.setVibrationEnabled(vibrationEnabled);

                    AlarmScheduler.saveAlarmSettings(requireContext(), alarm.getId(),
                            soundEnabled, vibrationEnabled);

                    alarmRepository.updateAlarm(alarm);

                    if (alarm.isEnabled()) {
                        String[] timeParts = alarm.getTime().split(":");
                        int hour = Integer.parseInt(timeParts[0]);
                        int minute = Integer.parseInt(timeParts[1]);
                        AlarmScheduler.scheduleAlarm(requireContext(), alarm.getId(),
                                hour, minute, label);
                    }

                    alarmAdapter.notifyItemChanged(position);
                    Toast.makeText(getContext(), "알람 설정이 변경되었습니다.",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        builder.create().show();
    }
}