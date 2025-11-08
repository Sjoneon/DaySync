package com.sjoneon.cap.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.sjoneon.cap.R;
import com.sjoneon.cap.adapters.SessionAdapter;
import com.sjoneon.cap.models.api.ApiResponse;
import com.sjoneon.cap.models.api.SessionInfo;
import com.sjoneon.cap.models.api.SessionListResponse;
import com.sjoneon.cap.models.api.SessionUpdateRequest;
import com.sjoneon.cap.services.DaySyncApiService;
import com.sjoneon.cap.utils.ApiClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 대화 목록을 표시하는 바텀시트
 */
public class SessionListBottomSheet extends BottomSheetDialogFragment implements SessionAdapter.OnSessionClickListener {

    private static final String TAG = "SessionListBottomSheet";
    private static final String ARG_USER_UUID = "user_uuid";
    private static final String ARG_CURRENT_SESSION_ID = "current_session_id";

    private RecyclerView recyclerViewSessions;
    private Button buttonNewChat;
    private SessionAdapter adapter;
    private List<SessionInfo> sessionList = new ArrayList<>();

    private String userUuid;
    private Integer currentSessionId;
    private DaySyncApiService apiService;
    private OnSessionActionListener actionListener;

    /**
     * 세션 액션 리스너 인터페이스
     */
    public interface OnSessionActionListener {
        void onSessionSelected(int sessionId);
        void onNewChatRequested();
        void onCurrentSessionDeleted();
    }

    public static SessionListBottomSheet newInstance(String userUuid, Integer currentSessionId) {
        SessionListBottomSheet fragment = new SessionListBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_USER_UUID, userUuid);
        if (currentSessionId != null) {
            args.putInt(ARG_CURRENT_SESSION_ID, currentSessionId);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userUuid = getArguments().getString(ARG_USER_UUID);
            if (getArguments().containsKey(ARG_CURRENT_SESSION_ID)) {
                currentSessionId = getArguments().getInt(ARG_CURRENT_SESSION_ID);
            }
        }
        apiService = ApiClient.getInstance().getApiService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_session_list, container, false);

        recyclerViewSessions = view.findViewById(R.id.recyclerViewSessions);
        buttonNewChat = view.findViewById(R.id.buttonNewChat);

        setupRecyclerView();
        setupButtonListeners();
        loadSessions();

        return view;
    }

    private void setupRecyclerView() {
        adapter = new SessionAdapter(sessionList, currentSessionId, this);
        recyclerViewSessions.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewSessions.setAdapter(adapter);
    }

    private void setupButtonListeners() {
        buttonNewChat.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onNewChatRequested();
            }
            dismiss();
        });
    }

    private void loadSessions() {
        if (userUuid == null) {
            Log.e(TAG, "User UUID is null");
            return;
        }

        apiService.getUserSessions(userUuid).enqueue(new Callback<SessionListResponse>() {
            @Override
            public void onResponse(Call<SessionListResponse> call, Response<SessionListResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SessionListResponse sessionListResponse = response.body();
                    if (sessionListResponse.isSuccess() && sessionListResponse.getSessions() != null) {
                        sessionList.clear();
                        sessionList.addAll(sessionListResponse.getSessions());
                        adapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onFailure(Call<SessionListResponse> call, Throwable t) {
                Log.e(TAG, "Failed to load sessions", t);
                Toast.makeText(getContext(), "대화 목록을 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onSessionClick(SessionInfo session) {
        if (actionListener != null) {
            actionListener.onSessionSelected(session.getId());
        }
        dismiss();
    }

    @Override
    public void onEditTitle(SessionInfo session) {
        showEditTitleDialog(session);
    }

    @Override
    public void onDeleteSession(SessionInfo session) {
        showDeleteConfirmDialog(session);
    }

    private void showEditTitleDialog(SessionInfo session) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_text, null);
        EditText editText = dialogView.findViewById(R.id.editTextInput);
        editText.setText(session.getTitle());
        editText.setHint(R.string.session_title_hint);

        builder.setTitle(R.string.edit_session_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newTitle = editText.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        Toast.makeText(getContext(), R.string.session_title_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateSessionTitle(session.getId(), newTitle);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog(SessionInfo session) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DialogTheme);

        boolean isCurrentSession = currentSessionId != null && session.getId() == currentSessionId;
        String message = isCurrentSession ?
                getString(R.string.confirm_delete_current_session) :
                getString(R.string.confirm_delete_session);

        builder.setTitle(R.string.delete_chat)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    deleteSession(session.getId(), isCurrentSession);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateSessionTitle(int sessionId, String newTitle) {
        SessionUpdateRequest request = new SessionUpdateRequest(newTitle);

        apiService.updateSession(sessionId, userUuid, request).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(getContext(), R.string.session_updated, Toast.LENGTH_SHORT).show();
                    loadSessions();
                } else {
                    Toast.makeText(getContext(), R.string.error_occurred, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Log.e(TAG, "Failed to update session title", t);
                Toast.makeText(getContext(), R.string.error_occurred, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteSession(int sessionId, boolean isCurrentSession) {
        apiService.deleteSession(sessionId, userUuid).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(getContext(), R.string.session_deleted, Toast.LENGTH_SHORT).show();

                    if (isCurrentSession && actionListener != null) {
                        actionListener.onCurrentSessionDeleted();
                    }

                    loadSessions();
                } else {
                    Toast.makeText(getContext(), R.string.error_occurred, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Log.e(TAG, "Failed to delete session", t);
                Toast.makeText(getContext(), R.string.error_occurred, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setOnSessionActionListener(OnSessionActionListener listener) {
        this.actionListener = listener;
    }
}