package com.fahim.mt.mtproto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fahim.mt.BuildConfig;
import com.fahim.mt.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private BotInteractionListener interactionListener;
    private String phoneNumber;
    private long targetBotChatId = 0;
    private boolean isBotRunning = false;
    private Set<String> seenListingIds = new HashSet<>();
    private String lastScannedMarkupHash = "";
    private static final Set<String> ALLOWED_BINS = new HashSet<>(java.util.Arrays.asList(
        "533985", "435880", "491277", "461126", "511332"
    ));

    private enum BotState {
        IDLE,
        STARTING,       // Just sent /start
        WAIT_LISTINGS,  // Waiting to click Available Listings
        WAIT_SORT,      // Waiting to click Latest First
        SCANNING        // Main loop: Scan and Next
    }
    private BotState currentBotState = BotState.IDLE;

    public interface BotInteractionListener {
        void onStepCompleted(); // For start/entering listings
        void onMatchFound(int count, List<Integer> uniqueIndices, List<Integer> duplicateIndices);
        void onNextPage();
    }

    public void setInteractionListener(BotInteractionListener listener) {
        this.interactionListener = listener;
    }

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
                // For edited messages or inline updates
                client.send(new TdApi.GetMessage(update.chatId, update.messageId), msgObj -> {
                    if (msgObj instanceof TdApi.Message) {
                        TdApi.Message msg = (TdApi.Message) msgObj;
                        scanButtons(msg.chatId, msg.id, msg.replyMarkup);
                    }
                });
            }
        } else if (object instanceof TdApi.UpdateMessageEdited) {
            TdApi.UpdateMessageEdited update = (TdApi.UpdateMessageEdited) object;
            if (update.chatId == targetBotChatId) {
                scanButtons(update.chatId, update.messageId, update.replyMarkup);
            }
        } else if (object instanceof TdApi.UpdateChatReplyMarkup) {
            TdApi.UpdateChatReplyMarkup update = (TdApi.UpdateChatReplyMarkup) object;
            if (update.chatId == targetBotChatId && update.replyMarkupMessage != null) {
                scanButtons(update.chatId, update.replyMarkupMessage.id, update.replyMarkupMessage.replyMarkup);
            }
        } else if (object instanceof TdApi.Error) {
            TdApi.Error error = (TdApi.Error) object;
            Log.e(TAG, "TDLib Error: " + error.code + " " + error.message);
            if (authCallback != null) authCallback.onError(error.message);
        }
    }

    private void onException(Throwable e) {
        Log.e(TAG, "TDLib Exception", e);
    }

    public void startAuthentication(String phoneNumber, AuthCallback callback) {
        this.phoneNumber = phoneNumber;
        this.authCallback = callback;
        if (client == null) init();
        else client.send(new TdApi.GetAuthorizationState(), this::onResult);
    }

    public void sendCode(String code) {
        if (client != null) client.send(new TdApi.CheckAuthenticationCode(code), this::onResult);
    }

    public void interactWithBot(String username) {
        if (client == null) return;
        isBotRunning = true;
        currentBotState = BotState.STARTING;
        seenListingIds.clear();
        lastScannedMarkupHash = "";

        client.send(new TdApi.SearchPublicChat(username), object -> {
            if (object instanceof TdApi.Chat) {
                TdApi.Chat chat = (TdApi.Chat) object;
                this.targetBotChatId = chat.id;
                
                TdApi.InputMessageText text = new TdApi.InputMessageText(new TdApi.FormattedText("/start", null), null, true);
                TdApi.SendMessage request = new TdApi.SendMessage();
                request.chatId = targetBotChatId;
                request.inputMessageContent = text;

                client.send(request, result -> {
                    if (result instanceof TdApi.Message) {
                        Log.i(TAG, "Sent /start. Moving to WAIT_LISTINGS.");
                        currentBotState = BotState.WAIT_LISTINGS;
                        if (interactionListener != null) interactionListener.onStepCompleted();
                    }
                });
            }
        });
    }

    public void stopBot() {
        isBotRunning = false;
        currentBotState = BotState.IDLE;
        lastScannedMarkupHash = "";
        com.fahim.mt.MainActivity.appendBotChat("Bot Stopped.");
    }

    private void scanButtons(long chatId, long messageId, TdApi.ReplyMarkup replyMarkup) {
        if (!isBotRunning || replyMarkup == null) return;
        if (!(replyMarkup instanceof TdApi.ReplyMarkupInlineKeyboard)) return;

        TdApi.ReplyMarkupInlineKeyboard inlineKeyboard = (TdApi.ReplyMarkupInlineKeyboard) replyMarkup;
        String currentHash = getMarkupHash(inlineKeyboard);
        
        // Prevent rescanning the exact same page state
        if (currentHash.equals(lastScannedMarkupHash)) {
            return;
        }
        lastScannedMarkupHash = currentHash;
        
        switch (currentBotState) {
            case WAIT_LISTINGS:
                for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                    for (TdApi.InlineKeyboardButton button : row) {
                        if (button.text.contains("Available Listings")) {
                            Log.i(TAG, "Clicking Available Listings -> Moving to WAIT_SORT");
                            currentBotState = BotState.WAIT_SORT;
                            clickInlineButton(chatId, messageId, ((TdApi.InlineKeyboardButtonTypeCallback)button.type).data);
                            return;
                        }
                    }
                }
                break;

            case WAIT_SORT:
                for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                    for (TdApi.InlineKeyboardButton button : row) {
                        if (button.text.contains("Latest First")) {
                            Log.i(TAG, "Clicking Latest First -> Moving to SCANNING");
                            currentBotState = BotState.SCANNING;
                            if (interactionListener != null) interactionListener.onStepCompleted();
                            clickInlineButton(chatId, messageId, ((TdApi.InlineKeyboardButtonTypeCallback)button.type).data);
                            return;
                        }
                    }
                }
                break;

            case SCANNING:
                List<Integer> uniqueIndices = new ArrayList<>();
                List<Integer> duplicateIndices = new ArrayList<>();
                TdApi.InlineKeyboardButton nextButton = null;
                int listingCount = 0;

                // Match pattern: index.BINxx:USD$Price:False
                // Example: 103.403446xx:USD$15.02:False
                java.util.regex.Pattern matchPattern = java.util.regex.Pattern.compile(".*(\\d{6})xx:USD\\$([\\d.]+):False.*");

                for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                    for (TdApi.InlineKeyboardButton button : row) {
                        String btnText = button.text;
                        
                        // New Match Criteria:
                        // 1. No 🇬 or 🔐
                        // 2. BIN matches one of the 5 numbers
                        // 3. Price < 12
                        // 4. Contains "False"
                        if (btnText.contains("False") && !btnText.contains("🇬") && !btnText.contains("🔐")) {
                            java.util.regex.Matcher m = matchPattern.matcher(btnText);
                            if (m.find()) {
                                String bin = m.group(1);
                                double price = Double.parseDouble(m.group(2));
                                
                                if (ALLOWED_BINS.contains(bin) && price < 12.0) {
                                    String listingId = bytesToHex(((TdApi.InlineKeyboardButtonTypeCallback)button.type).data);
                                    com.fahim.mt.MainActivity.appendBotChat("MATCH FOUND: BIN " + bin + " | Price $" + price + " | Text: " + btnText);
                                    if (seenListingIds.contains(listingId)) {
                                        duplicateIndices.add(listingCount % 20);
                                    } else {
                                        seenListingIds.add(listingId);
                                        uniqueIndices.add(listingCount % 20);
                                    }
                                }
                            }
                        } else if (btnText.contains("Next") || btnText.contains("➡️")) {
                            nextButton = button;
                        }

                        if (btnText.matches(".*\\d+.*") && !btnText.contains("Next") && !btnText.contains("➡️")) {
                            listingCount++;
                        }
                    }
                }

                if (!uniqueIndices.isEmpty() || !duplicateIndices.isEmpty()) {
                    if (interactionListener != null) interactionListener.onMatchFound(uniqueIndices.size() + duplicateIndices.size(), uniqueIndices, duplicateIndices);
                }

                if (nextButton != null) {
                    TdApi.InlineKeyboardButton finalNext = nextButton;
                    Log.i(TAG, "Scanning finished. Clicking Next...");
                    if (interactionListener != null) interactionListener.onNextPage();
                    handler.postDelayed(() -> clickInlineButton(chatId, messageId, ((TdApi.InlineKeyboardButtonTypeCallback)finalNext.type).data), 1500);
                }
                break;
        }
    }

    private String getMarkupHash(TdApi.ReplyMarkupInlineKeyboard inlineKeyboard) {
        StringBuilder sb = new StringBuilder();
        for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
            for (TdApi.InlineKeyboardButton button : row) {
                if (button.type instanceof TdApi.InlineKeyboardButtonTypeCallback) {
                    sb.append(bytesToHex(((TdApi.InlineKeyboardButtonTypeCallback) button.type).data));
                }
            }
        }
        return sb.toString();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public void clickInlineButton(long chatId, long messageId, byte[] data) {
        client.send(new TdApi.GetCallbackQueryAnswer(chatId, messageId, new TdApi.CallbackQueryPayloadData(data)), result -> {});
    }

    public void sendPassword(String password) {
        if (client != null) client.send(new TdApi.CheckAuthenticationPassword(password), this::onResult);
    }

    private void handleAuthorizationState(TdApi.AuthorizationState state) {
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
            client.send(parameters, this::onResult);
        } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
            if (phoneNumber != null) client.send(new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), this::onResult);
        } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
            if (authCallback != null) authCallback.onCodeRequested();
        } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
            if (authCallback != null) authCallback.onPasswordRequested();
        } else if (state instanceof TdApi.AuthorizationStateReady) {
            if (authCallback != null) authCallback.onSuccess();
        } else if (state instanceof TdApi.AuthorizationStateClosed) {
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
