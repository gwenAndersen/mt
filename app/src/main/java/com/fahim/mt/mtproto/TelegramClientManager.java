package com.fahim.mt.mtproto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInitialized = false;
    private AuthCallback authCallback;
    private String phoneNumber;
    private long targetBotChatId = 0;

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
        // Log all updates for debugging
        if (object.getClass().getSimpleName().startsWith("Update")) {
            Log.d(TAG, "Update Received: " + object.getClass().getSimpleName());
        }

        if (object instanceof TdApi.UpdateAuthorizationState) {
            handleAuthorizationState(((TdApi.UpdateAuthorizationState) object).authorizationState);
        } else if (object instanceof TdApi.AuthorizationState) {
            handleAuthorizationState((TdApi.AuthorizationState) object);
        } else if (object instanceof TdApi.UpdateNewMessage) {
            TdApi.Message message = ((TdApi.UpdateNewMessage) object).message;
            if (message.chatId == targetBotChatId) {
                scanButtons(message.chatId, message.id, message.replyMarkup);
            }
        } else if (object instanceof TdApi.UpdateMessageContent) {
            TdApi.UpdateMessageContent update = (TdApi.UpdateMessageContent) object;
            if (update.chatId == targetBotChatId) {
                Log.i(TAG, "Message content updated for bot message: " + update.messageId);
            }
        } else if (object instanceof TdApi.UpdateMessageEdited) {
            TdApi.UpdateMessageEdited update = (TdApi.UpdateMessageEdited) object;
            if (update.chatId == targetBotChatId) {
                Log.i(TAG, "Message edited for bot message: " + update.messageId);
                scanButtons(update.chatId, update.messageId, update.replyMarkup);
            }
        } else if (object instanceof TdApi.UpdateChatReplyMarkup) {
            TdApi.UpdateChatReplyMarkup update = (TdApi.UpdateChatReplyMarkup) object;
            if (update.chatId == targetBotChatId && update.replyMarkupMessage != null) {
                Log.i(TAG, "Chat reply markup updated for bot message: " + update.replyMarkupMessage.id);
                scanButtons(update.chatId, update.replyMarkupMessage.id, update.replyMarkupMessage.replyMarkup);
            }
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

    public void interactWithBot(String username) {
        Log.i(TAG, "interactWithBot called for: " + username);
        if (client == null) return;

        client.send(new TdApi.SearchPublicChat(username), object -> {
            if (object instanceof TdApi.Chat) {
                TdApi.Chat chat = (TdApi.Chat) object;
                this.targetBotChatId = chat.id;
                Log.i(TAG, "Found chat for " + username + " with ID: " + targetBotChatId);

                // Send /start command
                TdApi.InputMessageText text = new TdApi.InputMessageText(new TdApi.FormattedText("/start", null), null, true);
                TdApi.SendMessage request = new TdApi.SendMessage();
                request.chatId = targetBotChatId;
                request.inputMessageContent = text;

                client.send(request, result -> {
                    if (result instanceof TdApi.Message) {
                        Log.i(TAG, "Sent /start to " + username + " successfully.");
                    } else if (result instanceof TdApi.Error) {
                        Log.e(TAG, "Failed to send /start: " + ((TdApi.Error) result).message);
                    }
                });
            } else if (object instanceof TdApi.Error) {
                Log.e(TAG, "Failed to find bot " + username + ": " + ((TdApi.Error) object).message);
            }
        });
    }

    private void scanButtons(long chatId, long messageId, TdApi.ReplyMarkup replyMarkup) {
        if (replyMarkup == null) return;
        if (replyMarkup instanceof TdApi.ReplyMarkupInlineKeyboard) {
            TdApi.ReplyMarkupInlineKeyboard inlineKeyboard = (TdApi.ReplyMarkupInlineKeyboard) replyMarkup;
            boolean foundNext = false;
            TdApi.InlineKeyboardButton nextButton = null;

            for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                for (TdApi.InlineKeyboardButton button : row) {
                    if (button.type instanceof TdApi.InlineKeyboardButtonTypeCallback) {
                        TdApi.InlineKeyboardButtonTypeCallback callback = (TdApi.InlineKeyboardButtonTypeCallback) button.type;
                        String btnText = button.text;

                        // 1. Auto-click "Available Listings" (Initial Entry)
                        if (btnText.contains("Available Listings")) {
                            Log.i(TAG, "Entering Listings...");
                            com.fahim.mt.MainActivity.appendBotChat("Scanning Listings...");
                            clickInlineButton(chatId, messageId, callback.data);
                        } 
                        // 2. Filter specific listings (False + ✅)
                        else if (btnText.contains("False") && btnText.contains("✅")) {
                            Log.i(TAG, "MATCH FOUND: " + btnText);
                            com.fahim.mt.MainActivity.appendBotChat("Match: " + btnText);
                        }
                        // 3. Track Next button for pagination
                        else if (btnText.contains("Next") || btnText.contains("➡️")) {
                            nextButton = button;
                            foundNext = true;
                        }
                    }
                }
            }

            // 4. Auto-paginate if Next button found
            if (foundNext && nextButton != null) {
                TdApi.InlineKeyboardButtonTypeCallback nextCallback = (TdApi.InlineKeyboardButtonTypeCallback) nextButton.type;
                Log.i(TAG, "Auto-paginating to next page: " + nextButton.text);
                // Small delay to prevent flood and make it visible
                handler.postDelayed(() -> clickInlineButton(chatId, messageId, nextCallback.data), 1500);
            }
        }
    }

    private void handleNewMessage(TdApi.Message message) {
        // scanButtons logic moved to onResult/scanButtons
    }

    public void clickInlineButton(long chatId, long messageId, byte[] data) {
        Log.i(TAG, "Clicking inline button for message: " + messageId);
        client.send(new TdApi.GetCallbackQueryAnswer(chatId, messageId, new TdApi.CallbackQueryPayloadData(data)), result -> {
            if (result instanceof TdApi.CallbackQueryAnswer) {
                TdApi.CallbackQueryAnswer answer = (TdApi.CallbackQueryAnswer) result;
                Log.i(TAG, "Callback answer: " + answer.text);
                if (answer.text != null && !answer.text.isEmpty()) {
                    Logger.log(context, "Bot Answer: " + answer.text);
                }
            } else if (result instanceof TdApi.Error) {
                Log.e(TAG, "Failed to click button: " + ((TdApi.Error) result).message);
            }
        });
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
