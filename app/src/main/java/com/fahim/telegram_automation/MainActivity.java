package com.fahim.telegram_automation;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.content.ComponentName;
import android.content.Context;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.service.notification.StatusBarNotification;
import android.app.ActivityManager;
import java.util.ArrayList;
import java.util.List;

import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100; // Added
    private EditText messageEditText;
    private Button sendButton;
    private Button checkNotificationAccessButton;
    private Button startListenerServiceButton;
    private Button refreshNotificationsButton;
    private TextView activeNotificationsTextView;

    // IMPORTANT: Replace with your actual Bot Token and Chat ID
    // For security, consider storing these in a more secure way (e.g., BuildConfig, encrypted SharedPreferences)
    private static final String BOT_TOKEN = "8421082834:AAEh5J4fV7YvJIXLFzz-CMDuDdaZk-7eUNo"; // <<< REPLACE THIS
    private static final String CHAT_ID = "-4965986934";     // <<< REPLACE THIS (e.g., "-1001234567890" for a group)

    public static final String ACTION_REQUEST_NOTIFICATIONS = "com.fahim.telegram_automation.ACTION_REQUEST_NOTIFICATIONS";
    public static final String ACTION_RECEIVE_NOTIFICATIONS = "com.fahim.telegram_automation.ACTION_RECEIVE_NOTIFICATIONS";
    public static final String EXTRA_NOTIFICATIONS_LIST = "notifications_list";

    private BroadcastReceiver notificationsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_RECEIVE_NOTIFICATIONS.equals(intent.getAction())) {
                ArrayList<String> notifications = intent.getStringArrayListExtra(EXTRA_NOTIFICATIONS_LIST);
                if (notifications != null && !notifications.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Active Notifications (Total: ").append(notifications.size()).append("):\n\n");
                    for (String notification : notifications) {
                        sb.append(notification).append("\n---\n");
                    }
                    activeNotificationsTextView.setText(sb.toString());
                } else {
                    activeNotificationsTextView.setText("No active notifications found.");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request SMS permissions
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS},
                    PERMISSION_REQUEST_CODE);
        }


        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        checkNotificationAccessButton = findViewById(R.id.checkNotificationAccessButton);
        startListenerServiceButton = findViewById(R.id.startListenerServiceButton);
        refreshNotificationsButton = findViewById(R.id.refreshNotificationsButton);
        activeNotificationsTextView = findViewById(R.id.activeNotificationsTextView);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageContent = messageEditText.getText().toString().trim();
                if (!messageContent.isEmpty()) {
                    sendTelegramMessage(messageContent);
                    messageEditText.setText(""); // Clear the input field
                } else {
                    Toast.makeText(MainActivity.this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
                }
            }
        });

        checkNotificationAccessButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isNotificationServiceEnabled()) {
                    Toast.makeText(MainActivity.this, "Please enable Notification Access for TelegramAutomation", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } else {
                    Toast.makeText(MainActivity.this, "Notification Access is already enabled", Toast.LENGTH_SHORT).show();
                }
            }
        });

        startListenerServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNotificationServiceEnabled()) {
                    if (!isNotificationServiceRunning()) {
                        Intent serviceIntent = new Intent(MainActivity.this, WhatsAppNotificationListener.class);
                        startService(serviceIntent);
                        Toast.makeText(MainActivity.this, "Attempting to start Notification Listener Service", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Notification Listener Service is already running", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Notification Access not enabled. Cannot start service.", Toast.LENGTH_LONG).show();
                }
            }
        });

        refreshNotificationsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestActiveNotifications();
            }
        });

        // Automated permission check and service start
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Please enable Notification Access for TelegramAutomation", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } else {
            if (!isNotificationServiceRunning()) {
                Intent serviceIntent = new Intent(this, WhatsAppNotificationListener.class);
                startService(serviceIntent);
                Toast.makeText(this, "Notification Listener Service started automatically", Toast.LENGTH_SHORT).show();
            }
        }

        // Register receiver for notifications from the listener service
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationsReceiver,
                new IntentFilter(ACTION_RECEIVE_NOTIFICATIONS));

        // Start ForegroundService to keep the app alive
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        startService(serviceIntent);
        Log.d(TAG, "ForegroundService started from MainActivity.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationsReceiver);
    }

    private void sendTelegramMessage(String message) {
        if (BOT_TOKEN.equals("YOUR_TELEGRAM_BOT_TOKEN") || CHAT_ID.equals("YOUR_TELEGRAM_CHAT_ID")) {
            Log.e(TAG, "Telegram BOT_TOKEN or CHAT_ID not configured. Please update MainActivity.java");
            Toast.makeText(this, "Telegram API not configured", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TelegramBot bot = new TelegramBot(BOT_TOKEN);
                    bot.execute(new SendMessage(CHAT_ID, message));
                    Log.d(TAG, "Attempted to send message to Telegram.");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Message sent to Telegram", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message from MainActivity: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNotificationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (WhatsAppNotificationListener.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void requestActiveNotifications() {
        if (isNotificationServiceEnabled()) {
            if (isNotificationServiceRunning()) {
                Intent requestIntent = new Intent(ACTION_REQUEST_NOTIFICATIONS);
                LocalBroadcastManager.getInstance(this).sendBroadcast(requestIntent);
                Log.d(TAG, "Requested active notifications from listener service.");
            } else {
                Toast.makeText(this, "Notification Listener Service not running. Try starting it.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Notification Listener Service not running when request made.");
            }
        } else {
            Toast.makeText(this, "Notification Access not enabled. Cannot fetch notifications.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Notification Access not enabled when request made.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permissions granted.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "SMS permissions granted.");
            } else {
                Toast.makeText(this, "SMS permissions denied. Some features may not work.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "SMS permissions denied.");
            }
        }
    }
}