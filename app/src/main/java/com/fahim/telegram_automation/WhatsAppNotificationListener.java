package com.fahim.telegram_automation;

import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.app.Notification;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.ArrayList;

public class WhatsAppNotificationListener extends NotificationListenerService {

    private static final String TAG = "WhatsAppNotifListener";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";

    // IMPORTANT: Replace with your actual Bot Token and Chat ID
    // For security, consider storing these in a more secure way (e.g., BuildConfig, encrypted SharedPreferences)
    private static final String BOT_TOKEN = "8421082834:AAEh5J4fV7YvJIXLFzz-CMDuDdaZk-7eUNo"; // <<< REPLACE THIS
    private static final String CHAT_ID = "-4965986934";     // <<< REPLACE THIS (e.g., "-1001234567890" for a group)

    private BroadcastReceiver requestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MainActivity.ACTION_REQUEST_NOTIFICATIONS.equals(intent.getAction())) {
                Log.d(TAG, "Received request for active notifications from MainActivity.");
                ArrayList<String> notificationsList = new ArrayList<>();
                StatusBarNotification[] activeNotifications = getActiveNotifications();
                Log.d(TAG, "getActiveNotifications() returned " + activeNotifications.length + " notifications.");

                if (activeNotifications.length > 0) {
                    for (StatusBarNotification sbn : activeNotifications) {
                        String packageName = sbn.getPackageName();
                        Notification notification = sbn.getNotification();
                        Bundle extras = notification.extras;
                        String title = extras.getString(Notification.EXTRA_TITLE);
                        String text = extras.getString(Notification.EXTRA_TEXT);

                        String notificationInfo = "Package: " + packageName + ", Title: " + title + ", Text: " + text;
                        notificationsList.add(notificationInfo);
                        Log.d(TAG, "Adding to list: " + notificationInfo);
                    }
                } else {
                    Log.d(TAG, "No active notifications to send to MainActivity.");
                }

                Intent responseIntent = new Intent(MainActivity.ACTION_RECEIVE_NOTIFICATIONS);
                responseIntent.putStringArrayListExtra(MainActivity.EXTRA_NOTIFICATIONS_LIST, notificationsList);
                LocalBroadcastManager.getInstance(context).sendBroadcast(responseIntent);
                Log.d(TAG, "Sent active notifications list (size: " + notificationsList.size() + ") to MainActivity.");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WhatsAppNotificationListener created.");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Notification Listener Service Created", Toast.LENGTH_SHORT).show();
            }
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(requestReceiver,
                new IntentFilter(MainActivity.ACTION_REQUEST_NOTIFICATIONS));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "Notification Posted: Package: " + sbn.getPackageName());

        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String title = extras.getString(Notification.EXTRA_TITLE);
        String text = extras.getString(Notification.EXTRA_TEXT);

        final String logMessage = "Notification - Package: " + packageName + ", Title: " + title + ", Text: " + text;
        Log.i(TAG, logMessage);

        if (text != null && text.equalsIgnoreCase("test")) {
            sendTelegramMessage("test received");
        }

        if (packageName.equals(WHATSAPP_PACKAGE)) {
            Log.d(TAG, "WhatsApp Notification detected.");

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), logMessage, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Log.d(TAG, "Notification from non-WhatsApp package: " + packageName);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "Notification Removed: Package: " + sbn.getPackageName());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WhatsAppNotificationListener destroyed.");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Notification Listener Service Destroyed", Toast.LENGTH_SHORT).show();
            }
        });
        LocalBroadcastManager.getInstance(this).unregisterReceiver(requestReceiver);
    }

    private void sendTelegramMessage(String message) {
        if (BOT_TOKEN.equals("YOUR_TELEGRAM_BOT_TOKEN") || CHAT_ID.equals("YOUR_TELEGRAM_CHAT_ID")) {
            Log.e(TAG, "Telegram BOT_TOKEN or CHAT_ID not configured. Please update WhatsAppNotificationListener.java");
            // No toast here as it's a service
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TelegramBot bot = new TelegramBot(BOT_TOKEN);
                    bot.execute(new SendMessage(CHAT_ID, message));
                    Log.d(TAG, "Attempted to send message to Telegram.");
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message from WhatsAppNotificationListener: " + e.getMessage(), e);
                }
            }
        }).start();
    }
}