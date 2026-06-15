package com.fahim.mt;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {
    private static final String TAG = "TransactionParser";

    public static String parse(String sender, String body) {
        if (sender == null || body == null) return null;

        if (sender.equalsIgnoreCase("NAGAD")) {
            return parseNagad(body);
        } else if (sender.equalsIgnoreCase("bKash")) {
            return parsebKash(body);
        } else if (sender.equalsIgnoreCase("RECORD") || body.toUpperCase().startsWith("RECORD")) {
            return parseRecord(body);
        }
        
        return null;
    }

    public static String parseNagad(String body) {
        // Example: "Amount: Tk 300.00 ... 07/05/2026 10:59"
        Pattern amountPattern = Pattern.compile("Amount: Tk (\\d+\\.\\d{2})", Pattern.CASE_INSENSITIVE);
        Pattern senderPattern = Pattern.compile("Sender: (\\d{11})", Pattern.CASE_INSENSITIVE);
        // Matches dd/MM/yyyy HH:mm (or with AM/PM)
        Pattern dateTimePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2}(?:\\s+[AP]M)?)", Pattern.CASE_INSENSITIVE);

        Matcher mAmount = amountPattern.matcher(body);
        Matcher mSender = senderPattern.matcher(body);
        Matcher mDateTime = dateTimePattern.matcher(body);

        if (mAmount.find() && mSender.find()) {
            String amount = mAmount.group(1);
            String senderNumber = mSender.group(1);
            String lastFourDigits = senderNumber.substring(senderNumber.length() - 4);
            
            String formattedTime = "";
            if (mDateTime.find()) {
                String fullDateTime = mDateTime.group(0);
                String timePart = mDateTime.group(2);
                boolean hasAmPm = timePart.toUpperCase().contains("AM") || timePart.toUpperCase().contains("PM");
                formattedTime = formatDateTime(fullDateTime, hasAmPm ? "dd/MM/yyyy hh:mm a" : "dd/MM/yyyy HH:mm");
            }
            
            String result = "💸 Rcv 🟠N " + amount + " TK ****" + lastFourDigits;
            if (!formattedTime.isEmpty()) result += " (" + formattedTime + ")";
            return result;
        }
        return null;
    }

    public static String parsebKash(String body) {
        // Example: "received Tk 300.00 from 01XXXXXXXXX ... at 05/10/2025 10:20 AM"
        Pattern amountPattern = Pattern.compile("(?:received|Cash In) Tk (\\d+\\.\\d{2})", Pattern.CASE_INSENSITIVE);
        Pattern senderPattern = Pattern.compile("from (\\d{11})", Pattern.CASE_INSENSITIVE);
        // Matches dd/MM/yyyy HH:mm (or with AM/PM) - making AM/PM optional like Nagad
        Pattern dateTimePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2}(?:\\s+[AP]M)?)", Pattern.CASE_INSENSITIVE);

        Matcher mAmount = amountPattern.matcher(body);
        Matcher mSender = senderPattern.matcher(body);
        Matcher mDateTime = dateTimePattern.matcher(body);

        if (mAmount.find() && mSender.find()) {
            String amount = mAmount.group(1);
            String senderNumber = mSender.group(1);
            String lastFourDigits = senderNumber.substring(senderNumber.length() - 4);
            
            String formattedTime = "";
            if (mDateTime.find()) {
                String fullDateTime = mDateTime.group(0);
                String timePart = mDateTime.group(2);
                boolean hasAmPm = timePart.toUpperCase().contains("AM") || timePart.toUpperCase().contains("PM");
                formattedTime = formatDateTime(fullDateTime, hasAmPm ? "dd/MM/yyyy hh:mm a" : "dd/MM/yyyy HH:mm");
            }
            
            String result = "💸 Rcv 🟣B " + amount + " TK ****" + lastFourDigits;
            if (!formattedTime.isEmpty()) result += " (" + formattedTime + ")";
            return result;
        }
        return null;
    }

    private static String formatDateTime(String input, String inputFormat) {
        try {
            SimpleDateFormat in = new SimpleDateFormat(inputFormat, Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE hh:mm a", Locale.US);
            Date date = in.parse(input);
            return out.format(date);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse date: " + input + " with format " + inputFormat);
            return input; // Fallback
        }
    }

    public static String parseRecord(String body) {
        Pattern pattern = Pattern.compile("RECORD\\s+(expense|income)\\s+(\\d+)\\s+(.*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String type = matcher.group(1);
            String amount = matcher.group(2);
            String description = matcher.group(3);

            String emoji = type.equalsIgnoreCase("expense") ? "💸 Exp" : "💰 Inc";
            return emoji + " " + amount + " " + description;
        }
        return null;
    }
}
