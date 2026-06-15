package com.fahim.mt.mtproto;

import android.content.Context;
import android.util.Log;

import com.fahim.mt.BuildConfig;
import com.fahim.mt.Logger;

import java.io.File;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

public class TelegramClientManager {

    private static final String TAG = "TelegramClientManager";
    private static TelegramClientManager instance;
    private Client client;
    private final Context context;
    private boolean isInitialized = false;
    private AuthCallback authCallback;
    private String phoneNumber;

    private TelegramClientManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized TelegramClientManager getInstance(Context context) {
        if (instance == null) {
            instance = new TelegramClientManager(context);
        }
        return instance;
    }

    public void init() {
        if (isInitialized) return;

        try {
            Log.i(TAG, "Initializing TDLib...");
            Logger.log(context, "MTProto: Initializing TDLib...");

            // Load native libraries manually
            try {
                System.loadLibrary("cryptox");
                System.loadLibrary("sslx");
                System.loadLibrary("tdjni");
                Log.i(TAG, "Native libraries loaded successfully.");
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Failed to load native libraries: " + e.getMessage());
            }

            // Create the official client
            client = Client.create(this::onResult, this::onException, this::onException);

            isInitialized = true;
            Log.i(TAG, "Client created successfully.");
            
            // Kick off the authorization state machine immediately to resume session
            client.send(new TdApi.GetAuthorizationState(), this::onResult);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize TDLib", e);
            Logger.log(context, "MTProto: Init Failed: " + e.getMessage());
        }
    }

    private void onResult(TdApi.Object object) {
        Log.d(TAG, "onResult: Received object of type " + object.getClass().getSimpleName());
        if (object instanceof TdApi.UpdateAuthorizationState) {
            handleAuthorizationState(((TdApi.UpdateAuthorizationState) object).authorizationState);
        } else if (object instanceof TdApi.AuthorizationState) {
            handleAuthorizationState((TdApi.AuthorizationState) object);
        } else if (object instanceof TdApi.Error) {
            TdApi.Error error = (TdApi.Error) object;
            Log.e(TAG, "TDLib Error: " + error.code + " " + error.message);
            if (authCallback != null) authCallback.onError(error.message);
        } else if (object instanceof TdApi.Ok) {
            Log.d(TAG, "onResult: Received Ok");
        } else {
            Log.d(TAG, "onResult: Received unhandled object: " + object.toString());
        }
    }

    private void onException(Throwable e) {
        Log.e(TAG, "TDLib Exception", e);
    }

    public void startAuthentication(String phoneNumber, AuthCallback callback) {
        Log.i(TAG, "startAuthentication called for " + phoneNumber);
        this.phoneNumber = phoneNumber;
        this.authCallback = callback;
        if (client == null) {
            init();
        } else {
            // Already initialized, check if we need to send phone number
            client.send(new TdApi.GetAuthorizationState(), this::onResult);
        }
    }

    public void sendCode(String code) {
        Log.i(TAG, "sendCode called with: " + code);
        if (client != null) {
            client.send(new TdApi.CheckAuthenticationCode(code), this::onResult);
        }
    }

    public void sendPassword(String password) {
        Log.i(TAG, "sendPassword called");
        if (client != null) {
            client.send(new TdApi.CheckAuthenticationPassword(password), this::onResult);
        }
    }

    private void handleAuthorizationState(TdApi.AuthorizationState state) {
        Log.i(TAG, "Authorization state changed to: " + state.getClass().getSimpleName());
        Logger.log(context, "MTProto State: " + state.getClass().getSimpleName());

        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            TdApi.SetTdlibParameters parameters = new TdApi.SetTdlibParameters();
            parameters.apiId = BuildConfig.TELEGRAM_API_ID;
            parameters.apiHash = BuildConfig.TELEGRAM_API_HASH;
            parameters.useMessageDatabase = true;
            parameters.useChatInfoDatabase = true;
            parameters.useSecretChats = true;
            parameters.systemLanguageCode = "en";
            parameters.deviceModel = android.os.Build.MODEL;
            parameters.systemVersion = android.os.Build.VERSION.RELEASE;
            parameters.applicationVersion = "1.0";
            parameters.databaseEncryptionKey = new byte[0];
            
            File sessionDir = new File(context.getFilesDir(), "tdlib-session");
            if (!sessionDir.exists()) sessionDir.mkdirs();
            parameters.databaseDirectory = sessionDir.getAbsolutePath();
            parameters.useFileDatabase = true;

            Log.i(TAG, "Sending SetTdlibParameters...");
            client.send(parameters, this::onResult);
        } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            if (phoneNumber != null) {
                Log.i(TAG, "Sending SetAuthenticationPhoneNumber: " + phoneNumber);
                client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), this::onResult);
            } else {
                Log.i(TAG, "Waiting for phone number input...");
                // Notify UI if we have a callback
                if (authCallback != null) {
                    // This is a bit of a hack, but it lets the UI know it needs to show the login button/field
                    // Normally the user triggers startAuthentication which sets the callback
                }
            }
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            Log.i(TAG, "SMS code requested.");
            if (authCallback != null) authCallback.onCodeRequested();
        } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            Log.i(TAG, "2FA password requested.");
            if (authCallback != null) authCallback.onPasswordRequested();
        } else if (state instanceof TdApi.AuthorizationStateReady) {
            Log.i(TAG, "Authorization Ready!");
            if (authCallback != null) authCallback.onSuccess();
        } else if (state instanceof TdApi.AuthorizationStateLoggingOut) {
            Log.i(TAG, "Logging out...");
        } else if (state instanceof TdApi.AuthorizationStateClosing) {
            Log.i(TAG, "Closing...");
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
            Log.i(TAG, "Closed.");
            isInitialized = false;
            client = null;
        }
    }

    public interface AuthCallback {
        void onCodeRequested();
        void onPasswordRequested();
        void onSuccess();
        void onError(String message);
    }
}
