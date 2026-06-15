package com.fahim.mt;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.FileReader;


public class Logger {
    private static final String LOG_FILE_NAME = "logs.txt";

    public static void log(Context context, String message) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            writer.append(timestamp).append(": ").append(message).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getLogs(Context context) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        StringBuilder logContent = new StringBuilder();
        if (logFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logContent.append(line).append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return "Error reading logs: " + e.getMessage();
            }
        } else {
            return "Log file does not exist.";
        }
        return logContent.toString();
    }
}
