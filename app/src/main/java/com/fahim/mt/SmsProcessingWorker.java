package com.fahim.mt;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Data;
import androidx.work.Constraints;
import androidx.work.NetworkType;

public class SmsProcessingWorker extends Worker {

    private static final String TAG = "SmsProcessingWorker";
    private static final String DEFAULT_CHAT_ID = "-4965986934";

    public SmsProcessingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sender = getInputData().getString("sender");
        String messageBody = getInputData().getString("messageBody");

        Log.i(TAG, "SmsProcessingWorker: Processing SMS from " + sender);
        Logger.log(getApplicationContext(), "SmsProcessingWorker: Received SMS from " + sender);

        // Use centralized parser
        String formattedMessage = TransactionParser.parse(sender, messageBody);

        // If it's a known transaction format, send it. 
        // If not, we could choose to send the raw message or skip.
        // Given "remove everything except...", I'll just send everything but formatted if possible.
        String messageToSend = (formattedMessage != null) ? formattedMessage : messageBody;

        if (messageToSend != null) {
            enqueueTelegramSendWorker(messageToSend);
        }

        return Result.success();
    }

    private void enqueueTelegramSendWorker(String content) {
        Data inputData = new Data.Builder()
                .putString(TelegramSendWorker.KEY_MESSAGE, content)
                .putString(TelegramSendWorker.KEY_CHAT_ID, DEFAULT_CHAT_ID)
                .build();

        OneTimeWorkRequest telegramWorkRequest = new OneTimeWorkRequest.Builder(TelegramSendWorker.class)
                .setInputData(inputData)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();

        WorkManager.getInstance(getApplicationContext()).enqueue(telegramWorkRequest);
        Logger.log(getApplicationContext(), "Enqueued TelegramSendWorker.");
    }
}
