package com.fahim.mt;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

public class TelegramSendWorker extends Worker {

    private static final String TAG = "TelegramSendWorker";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_CHAT_ID = "chatId";

    // IMPORTANT: Replace with your actual Bot Token
    private static final String BOT_TOKEN = "8421082834:AAEh5J4fV7YvJIXLFzz-CMDuDdaZk-7eUNo";

    public TelegramSendWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String message = getInputData().getString(KEY_MESSAGE);
        String chatId = getInputData().getString(KEY_CHAT_ID);

        if (message == null || chatId == null) {
            Log.e(TAG, "Invalid input data: message or chatId is null.");
            Logger.log(getApplicationContext(), "TelegramSendWorker: Invalid input data (message or chatId is null).");
            return Result.failure();
        }

        try {
            TelegramBot bot = new TelegramBot(BOT_TOKEN);
            SendMessage request = new SendMessage(chatId, message);
            SendResponse response = bot.execute(request);

            if (response.isOk()) {
                Log.d(TAG, "Telegram message sent successfully. Chat ID: " + chatId + ", Message: " + message);
                Logger.log(getApplicationContext(), "TelegramSendWorker: Message sent successfully to " + chatId + ".");
                return Result.success();
            } else {
                Log.e(TAG, "Failed to send Telegram message. Error code: " + response.errorCode() + ", Description: " + response.description());
                Logger.log(getApplicationContext(), "TelegramSendWorker: Failed to send message to " + chatId + ". Error: " + response.description());
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending Telegram message: " + e.getMessage(), e);
            Logger.log(getApplicationContext(), "TelegramSendWorker: Exception sending message to " + chatId + ". Error: " + e.getMessage());
            return Result.retry();
        }
    }
}
