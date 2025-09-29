package com.fahim.telegram_automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    public static final String SMS_BUNDLE = "pdus";

    // IMPORTANT: Replace with your actual Bot Token and Chat ID
    private static final String BOT_TOKEN = "8421082834:AAEh5J4fV7YvJIXLFzz-CMDuDdaZk-7eUNo"; // <<< REPLACE THIS
    private static final String CHAT_ID = "-4965986934";     // <<< REPLACE THIS (e.g., "-1001234567890" for a group)

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get(SMS_BUNDLE);
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String sender = sms.getOriginatingAddress();
                        String messageBody = sms.getMessageBody();

                        Log.d(TAG, "SMS Received - Sender: " + sender + ", Message: " + messageBody);
                        Toast.makeText(context, "SMS from: " + sender + "\nMessage: " + messageBody, Toast.LENGTH_LONG).show();

                        // Implement filtering and extraction logic for NAGAD
                        if (sender != null && sender.equalsIgnoreCase("NAGAD")) { // Assuming sender is "NAGAD"
                            Log.d(TAG, "NAGAD SMS detected.");
                            Pattern amountPattern = Pattern.compile("Amount: (?:Tk )?(\\d+[.,]\\d{1,2}|\\d+)");
                            Pattern senderNumPattern = Pattern.compile("(?:Sender|From):\\s*([+\\d\\s]+)");

                            Matcher amountMatcher = amountPattern.matcher(messageBody);
                            Matcher senderNumMatcher = senderNumPattern.matcher(messageBody);

                            String amount = null;
                            if (amountMatcher.find()) {
                                amount = amountMatcher.group(1);
                            }

                            String fullSenderNum = null;
                            if (senderNumMatcher.find()) {
                                fullSenderNum = senderNumMatcher.group(1);
                            }

                            if (amount != null && fullSenderNum != null) {
                                String lastFourDigits = fullSenderNum.substring(Math.max(0, fullSenderNum.length() - 4));
                                String formattedMessage = "rcv " + amount + " TK ***" + lastFourDigits;
                                Log.d(TAG, "Formatted Message: " + formattedMessage);
                                sendTelegramMessage(context, formattedMessage);
                            } else {
                                Log.w(TAG, "Could not extract amount or sender number from NAGAD SMS.");
                            }
                        } else if (sender != null && sender.equalsIgnoreCase("bKash")) { // Assuming sender is "bKash"
                            Log.d(TAG, "bKash SMS detected.");
                            // Example message: "Cash In Tk 500.00 from 01707467676 successful. Fee Tk 0.00. Balance Tk 519.02. TrxID CBO7580CGL at 24/02/2025 20:13. Download App: https://bKa.sh/8app"

                            Pattern amountPattern = Pattern.compile("Tk (\\d+\\.\\d{2})"); // Matches "Tk 500.00"
                            Pattern senderNumPattern = Pattern.compile("from (\\d+) successful"); // Matches "from 01707467676 successful"

                            Matcher amountMatcher = amountPattern.matcher(messageBody);
                            Matcher senderNumMatcher = senderNumPattern.matcher(messageBody);

                            String amount = null;
                            if (amountMatcher.find()) {
                                amount = amountMatcher.group(1);
                            }

                            String fullSenderNum = null;
                            if (senderNumMatcher.find()) {
                                fullSenderNum = senderNumMatcher.group(1);
                            }

                            if (amount != null && fullSenderNum != null) {
                                String lastFourDigits = fullSenderNum.substring(Math.max(0, fullSenderNum.length() - 4));
                                String formattedMessage = "rcv " + amount + " TK ***" + lastFourDigits;
                                Log.d(TAG, "Formatted Message: " + formattedMessage);
                                sendTelegramMessage(context, formattedMessage);
                            } else {
                                Log.w(TAG, "Could not extract amount or sender number from bKash SMS.");
                            }
                        }
                    }
                }
            }
        }
    }

    private void sendTelegramMessage(Context context, String message) {
        if (BOT_TOKEN.equals("YOUR_TELEGRAM_BOT_TOKEN") || CHAT_ID.equals("YOUR_TELEGRAM_CHAT_ID")) {
            Log.e(TAG, "Telegram BOT_TOKEN or CHAT_ID not configured. Please update SmsReceiver.java");
            // No toast here as it's a background receiver
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
                    Log.e(TAG, "Error sending message from SmsReceiver: " + e.getMessage(), e);
                }
            }
        }).start();
    }
}
