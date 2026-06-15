package com.fahim.mt;

import android.app.Application;
import java.io.File;
import com.fahim.mt.mtproto.TelegramClientManager;

public class MtApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize MTProto Client Manager
        TelegramClientManager.getInstance(this).init();
    }
}
