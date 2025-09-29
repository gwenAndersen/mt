package com.fahim.telegram_automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

public class MessageReceiver extends BroadcastReceiver {

    private static final String TAG = "MessageReceiver";
    public static final String ACTION_SEND_TELEGRAM_MESSAGE = "com.fahim.telegram_automation.ACTION_SEND_TELEGRAM_MESSAGE";
    public static final String EXTRA_MESSAGE_CONTENT = "message_content";

    // IMPORTANT: Replace with your actual Bot Token and Chat ID
    // For security, consider storing these in a more secure way (e.g., BuildConfig, encrypted SharedPreferences)
    private static final String BOT_TOKEN = "8421082834:AAEh5J4fV7YvJIXLFzz-CMDuDdaZk-7eUNo"; // <<< REPLACE THIS
    private static final String CHAT_ID = "-4965986934";     // <<< REPLACE THIS (e.g., "-1001234567890" for a group)

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive triggered for action: " + intent.getAction());

        if (ACTION_SEND_TELEGRAM_MESSAGE.equals(intent.getAction())) {
            String messageContent = intent.getStringExtra(EXTRA_MESSAGE_CONTENT);
            if (messageContent != null && !messageContent.isEmpty()) {
                Log.d(TAG, "Received message for Telegram: " + messageContent);
                sendTelegramMessage(messageContent);
            } else {
                Log.w(TAG, "Received intent with empty or null message content. Not sending to Telegram.");
            }
        }
    }

    private void sendTelegramMessage(final String message) {
        if (BOT_TOKEN.equals("YOUR_TELEGRAM_BOT_TOKEN") || CHAT_ID.equals("YOUR_TELEGRAM_CHAT_ID")) {
            Log.e(TAG, "Telegram BOT_TOKEN or CHAT_ID not configured. Please update MessageReceiver.java");
            return;
        }

        Log.d(TAG, "Initiating Telegram message send in background thread.");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TelegramBot bot = new TelegramBot(BOT_TOKEN);
                    SendMessage request = new SendMessage(CHAT_ID, message);
                    Log.d(TAG, "Sending Telegram message: " + message + " to chat ID: " + CHAT_ID);
                    SendResponse response = bot.execute(request);

                    if (response.isOk()) {
                        Log.i(TAG, "Telegram message sent successfully. Message ID: " + response.message().messageId());
                    } else {
                        Log.e(TAG, "Failed to send Telegram message. Error code: " + response.errorCode() + ", Description: " + response.description());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message via BroadcastReceiver: " + e.getMessage(), e);
                }
            }
        }).start();
    }
}