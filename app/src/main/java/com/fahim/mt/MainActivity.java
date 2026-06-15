package com.fahim.mt;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.fahim.mt.mtproto.TelegramClientManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String DEFAULT_CHAT_ID = "-4965986934";
    private TextView logTextView;
    private TextInputEditText messageEditText;
    private TextInputEditText phoneEditText;
    private MaterialButton loginButton;
    private MaterialButton submitCodeButton;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable logUpdater;

    private enum AuthState {
        IDLE,
        WAITING_CODE,
        WAITING_PASSWORD,
        READY
    }
    private AuthState currentAuthState = AuthState.IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageEditText = findViewById(R.id.messageEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        loginButton = findViewById(R.id.loginButton);
        submitCodeButton = findViewById(R.id.submitCodeButton);
        logTextView = findViewById(R.id.activeNotificationsTextView);
        
        checkPermissions();

        // Register callback immediately to handle session resumption
        TelegramClientManager.getInstance(this).startAuthentication(null, new TelegramClientManager.AuthCallback() {
            @Override
            public void onCodeRequested() {
                currentAuthState = AuthState.WAITING_CODE;
                runOnUiThread(() -> {
                    loginButton.setVisibility(View.GONE);
                    submitCodeButton.setVisibility(View.VISIBLE);
                    phoneEditText.setText("");
                    phoneEditText.setHint("Enter SMS Code");
                    Toast.makeText(MainActivity.this, "SMS Code requested", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "UI: Switched to Code input mode (Auto)");
                });
            }

            @Override
            public void onPasswordRequested() {
                currentAuthState = AuthState.WAITING_PASSWORD;
                runOnUiThread(() -> {
                    phoneEditText.setText("");
                    phoneEditText.setHint("Enter 2FA Password");
                    Toast.makeText(MainActivity.this, "2FA Password requested", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "UI: Switched to Password input mode (Auto)");
                });
            }

            @Override
            public void onSuccess() {
                currentAuthState = AuthState.READY;
                runOnUiThread(() -> {
                    loginButton.setVisibility(View.VISIBLE);
                    loginButton.setText("Logged In");
                    loginButton.setEnabled(false);
                    submitCodeButton.setVisibility(View.GONE);
                    phoneEditText.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Session Resumed!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "UI: Session Resumed");
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + message, Toast.LENGTH_LONG).show());
            }
        });

        // MTProto Login
        loginButton.setOnClickListener(v -> {
            String phone = phoneEditText.getText().toString();
            if (!phone.isEmpty()) {
                Log.i(TAG, "Login button clicked for: " + phone);
                Logger.log(this, "Starting MTProto Login for: " + phone);
                // The callback is already registered above, just trigger auth with number
                TelegramClientManager.getInstance(this).startAuthentication(phone, null);
            }
        });

        submitCodeButton.setOnClickListener(v -> {
            String input = phoneEditText.getText().toString();
            Log.i(TAG, "Submit button clicked. State: " + currentAuthState + ", Input length: " + input.length());
            if (!input.isEmpty()) {
                if (currentAuthState == AuthState.WAITING_CODE) {
                    Log.i(TAG, "Calling sendCode...");
                    TelegramClientManager.getInstance(this).sendCode(input);
                } else if (currentAuthState == AuthState.WAITING_PASSWORD) {
                    Log.i(TAG, "Calling sendPassword...");
                    TelegramClientManager.getInstance(this).sendPassword(input);
                }
            }
        });

        // Manual Send Button
        MaterialButton sendButton = findViewById(R.id.sendButton);
        sendButton.setText("Send to Telegram");
        sendButton.setOnClickListener(v -> {
            String message = messageEditText.getText().toString();
            if (!message.isEmpty()) {
                Log.i(TAG, "Manual message input: " + message);
                sendToTelegram(message);
                messageEditText.setText("");
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            }
        });

        // Simplified UI: Hide other buttons
        findViewById(R.id.checkNotificationAccessButton).setVisibility(View.GONE);
        findViewById(R.id.startListenerServiceButton).setVisibility(View.GONE);
        findViewById(R.id.refreshNotificationsButton).setVisibility(View.GONE);
        
        MaterialButton clearLogsButton = findViewById(R.id.sendLogsButton);
        clearLogsButton.setText("Clear Logs");
        clearLogsButton.setOnClickListener(v -> {
            java.io.File logFile = new java.io.File(getFilesDir(), "logs.txt");
            if (logFile.exists()) logFile.delete();
            logTextView.setText("App Logs cleared.");
        });

        logUpdater = new Runnable() {
            @Override
            public void run() {
                refreshLogs();
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(logUpdater);

        Logger.log(this, "MainActivity: Ready.");
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.INTERNET
        };

        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void sendToTelegram(String content) {
        Data inputData = new Data.Builder()
                .putString(TelegramSendWorker.KEY_MESSAGE, content)
                .putString(TelegramSendWorker.KEY_CHAT_ID, DEFAULT_CHAT_ID)
                .build();

        OneTimeWorkRequest telegramWorkRequest = new OneTimeWorkRequest.Builder(TelegramSendWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(this).enqueue(telegramWorkRequest);
        Toast.makeText(this, "Enqueued to Telegram", Toast.LENGTH_SHORT).show();
        Logger.log(this, "MainActivity: Enqueued message to Telegram.");
    }

    private void refreshLogs() {
        String logs = Logger.getLogs(this);
        logTextView.setText("App Logs:\n" + logs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(logUpdater);
    }
}
