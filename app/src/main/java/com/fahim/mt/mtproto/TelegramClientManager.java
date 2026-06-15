package com.fahim.mt.mtproto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fahim.mt.BuildConfig;
import com.fahim.mt.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

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
    private static final Set<String> ALLOWED_BINS = new HashSet<>(Arrays.asList(
        "533985", "435880", "491277", "461126", "511332"
    ));
    private Set<String> historyMatches = new HashSet<>(); // Format: "BIN:Price"
    private Map<String, Long> historyMatchTimes = new HashMap<>(); // "BIN:Price" -> Timestamp
    
    // Persistent Grid State
    private final List<com.fahim.mt.BlockData> blockList = new ArrayList<>();
    private int totalRed = 0, totalBlue = 0, totalYellow = 0, totalGold = 0;

    public List<com.fahim.mt.BlockData> getBlockList() { return blockList; }
    public int getTotalRed() { return totalRed; }
    public int getTotalBlue() { return totalBlue; }
    public int getTotalYellow() { return totalYellow; }
    public int getTotalGold() { return totalGold; }

    private enum BotState {
        IDLE,
        STARTING,       // Just sent /start
        WAIT_LISTINGS,  // Waiting to click Available Listings
        WAIT_SORT,      // Waiting to click Latest First
        SCANNING        // Main loop: Scan and Next
    }
    private BotState currentBotState = BotState.IDLE;

    public interface BotInteractionListener {
        void onGridReset();
        void onGridUpdated();
    }

    public void setInteractionListener(BotInteractionListener listener) {
        this.interactionListener = listener;
    }

    public interface MessagesCallback {
        void onMessagesReceived(List<String> messages);
        void onError(String error);
    }

    public void fetchRecentMessages(String identifier, int limit, MessagesCallback callback) {
        if (client == null) return;
        
        if (identifier.startsWith("-") || identifier.matches("\\d+")) {
            // It's a Chat ID
            try {
                long chatId = Long.parseLong(identifier);
                fetchHistoryForId(chatId, limit, callback);
            } catch (NumberFormatException e) {
                callback.onError("Invalid Chat ID format");
            }
        } else {
            // It's a Username
            client.send(new TdApi.SearchPublicChat(identifier), object -> {
                if (object instanceof TdApi.Chat) {
                    fetchHistoryForId(((TdApi.Chat) object).id, limit, callback);
                } else if (object instanceof TdApi.Error) {
                    callback.onError(((TdApi.Error) object).message);
                }
            });
        }
    }

    private void fetchHistoryForId(long chatId, int limit, MessagesCallback callback) {
        client.send(new TdApi.GetChatHistory(chatId, 0, 0, limit, false), historyObj -> {
            if (historyObj instanceof TdApi.Messages) {
                TdApi.Messages messages = (TdApi.Messages) historyObj;
                List<String> result = new ArrayList<>();
                for (TdApi.Message msg : messages.messages) {
                    String content = "";
                    String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(msg.date * 1000L));
                    
                    if (msg.content instanceof TdApi.MessageText) {
                        content = ((TdApi.MessageText) msg.content).text.text;
                    } else if (msg.content instanceof TdApi.MessageDocument) {
                        TdApi.MessageDocument doc = (TdApi.MessageDocument) msg.content;
                        content = "[Document] " + doc.caption.text;
                    } else if (msg.content instanceof TdApi.MessagePhoto) {
                        TdApi.MessagePhoto photo = (TdApi.MessagePhoto) msg.content;
                        content = "[Photo] " + photo.caption.text;
                    } else if (msg.content instanceof TdApi.MessageVideo) {
                        TdApi.MessageVideo video = (TdApi.MessageVideo) msg.content;
                        content = "[Video] " + video.caption.text;
                    } else {
                        content = "[" + msg.content.getClass().getSimpleName() + "]";
                    }
                    result.add("[" + time + "] " + content);
                }
                callback.onMessagesReceived(result);
            } else if (historyObj instanceof TdApi.Error) {
                callback.onError(((TdApi.Error) historyObj).message);
            }
        });
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
        historyMatches.clear();
        historyMatchTimes.clear();
        
        // Reset Grid State
        blockList.clear();
        totalRed = 0;
        totalBlue = 0;
        totalYellow = 0;
        totalGold = 0;
        if (interactionListener != null) interactionListener.onGridReset();

        // 1. Fetch History from CentStock Private first
        String historyGroupId = "-1002358473714";
        com.fahim.mt.MainActivity.appendBotChat("Fetching history from CentStock Private for Blue Filter...");
        
        fetchRecentMessages(historyGroupId, 20, new MessagesCallback() {
            @Override
            public void onMessagesReceived(List<String> messages) {
                java.util.regex.Pattern binPattern = java.util.regex.Pattern.compile("💳\\s*(\\d{6})");
                java.util.regex.Pattern pricePattern = java.util.regex.Pattern.compile("📉\\s*Price:\\s*USD\\$([\\d.]+)");
                java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile("\\[(\\d{2}:\\d{2}:\\d{2})\\]");

                long now = System.currentTimeMillis();

                for (String msg : messages) {
                    java.util.regex.Matcher binMatcher = binPattern.matcher(msg);
                    java.util.regex.Matcher priceMatcher = pricePattern.matcher(msg);
                    java.util.regex.Matcher timeMatcher = timePattern.matcher(msg);
                    
                    if (binMatcher.find() && priceMatcher.find()) {
                        String bin = binMatcher.group(1);
                        String price = priceMatcher.group(1);
                        String key = bin + ":" + price;
                        historyMatches.add(key);
                        
                        if (timeMatcher.find()) {
                            try {
                                String timeStr = timeMatcher.group(1);
                                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                                Calendar cal = Calendar.getInstance();
                                Date msgDate = sdf.parse(timeStr);
                                if (msgDate != null) {
                                    Calendar msgCal = Calendar.getInstance();
                                    msgCal.setTime(msgDate);
                                    cal.set(Calendar.HOUR_OF_DAY, msgCal.get(Calendar.HOUR_OF_DAY));
                                    cal.set(Calendar.MINUTE, msgCal.get(Calendar.MINUTE));
                                    cal.set(Calendar.SECOND, msgCal.get(Calendar.SECOND));
                                    
                                    if (cal.getTimeInMillis() > now + 10000) cal.add(Calendar.DAY_OF_YEAR, -1);
                                    historyMatchTimes.put(key, cal.getTimeInMillis());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse time: " + e.getMessage());
                            }
                        }
                    }
                }
                
                com.fahim.mt.MainActivity.appendBotChat("History Filter initialized with " + historyMatches.size() + " pairs.");
                startBotInteractionSequence(username);
            }

            @Override
            public void onError(String error) {
                com.fahim.mt.MainActivity.appendBotChat("History Fetch Failed: " + error + ". Proceeding without Blue/Gold Filter.");
                startBotInteractionSequence(username);
            }
        });
    }

    private void startBotInteractionSequence(String username) {
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
                        
                        handler.post(() -> {
                            blockList.add(new com.fahim.mt.BlockData(true));
                            if (interactionListener != null) interactionListener.onGridUpdated();
                        });
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
        if (currentHash.equals(lastScannedMarkupHash)) return;
        lastScannedMarkupHash = currentHash;
        
        switch (currentBotState) {
            case WAIT_LISTINGS:
                for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                    for (TdApi.InlineKeyboardButton button : row) {
                        if (button.text.contains("Available Listings")) {
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
                            currentBotState = BotState.SCANNING;
                            handler.post(() -> {
                                blockList.add(new com.fahim.mt.BlockData(true));
                                if (interactionListener != null) interactionListener.onGridUpdated();
                            });
                            clickInlineButton(chatId, messageId, ((TdApi.InlineKeyboardButtonTypeCallback)button.type).data);
                            return;
                        }
                    }
                }
                break;

            case SCANNING:
                List<Integer> uniqueIndices = new ArrayList<>();
                List<Integer> duplicateIndices = new ArrayList<>();
                List<Integer> historyIndices = new ArrayList<>();
                List<Integer> goldIndices = new ArrayList<>();
                TdApi.InlineKeyboardButton nextButton = null;
                int listingCount = 0;
                double maxPrice = 0.0;
                double totalPrice = 0.0;

                java.util.regex.Pattern matchPattern = java.util.regex.Pattern.compile(".*(\\d{6})xx:USD\\$([\\d.]+):False.*");

                for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                    for (TdApi.InlineKeyboardButton button : row) {
                        String btnText = button.text;
                        if (btnText.contains("False") && !btnText.contains("🇬") && !btnText.contains("🔐")) {
                            java.util.regex.Matcher m = matchPattern.matcher(btnText);
                            if (m.find()) {
                                String bin = m.group(1);
                                String priceStr = m.group(2);
                                double price = Double.parseDouble(priceStr);
                                
                                String key = bin + ":" + priceStr;
                                boolean isHistoryMatch = historyMatches.contains(key);
                                boolean isMainMatch = ALLOWED_BINS.contains(bin) && price < 12.0;

                                if (isHistoryMatch || isMainMatch) {
                                    if (price > maxPrice) maxPrice = price;
                                    totalPrice += price;
                                    String listingId = bytesToHex(((TdApi.InlineKeyboardButtonTypeCallback)button.type).data);
                                    
                                    String type = "RED";
                                    if (isHistoryMatch) {
                                        Long msgTime = historyMatchTimes.get(key);
                                        boolean isGold = msgTime != null && (System.currentTimeMillis() - msgTime < 120000); // 2 mins
                                        
                                        if (isGold) {
                                            com.fahim.mt.MainActivity.appendBotChat("GOLD MATCH (Recent History): BIN " + bin + " @ $" + priceStr);
                                            goldIndices.add(listingCount % 20);
                                            type = "GOLD";
                                        } else {
                                            com.fahim.mt.MainActivity.appendBotChat("BLUE MATCH (History): BIN " + bin + " @ $" + priceStr);
                                            historyIndices.add(listingCount % 20);
                                            type = "BLUE";
                                        }
                                    } else {
                                        com.fahim.mt.MainActivity.appendBotChat("RED MATCH (Main): BIN " + bin + " @ $" + priceStr);
                                    }

                                    if (seenListingIds.contains(listingId)) {
                                        duplicateIndices.add(listingCount % 20);
                                        type = "YELLOW";
                                    } else {
                                        seenListingIds.add(listingId);
                                        if (isMainMatch && !isHistoryMatch) uniqueIndices.add(listingCount % 20);
                                    }

                                    final String finalType = type;
                                    final String finalBin = bin;
                                    final String finalPrice = priceStr;
                                    final double currentPrice = price;
                                    handler.post(() -> {
                                        if (!blockList.isEmpty()) {
                                            com.fahim.mt.BlockData currentBlock = blockList.get(blockList.size() - 1);
                                            currentBlock.matchDetails.add(
                                                new com.fahim.mt.BlockData.MatchDetail(finalBin, finalPrice, finalType)
                                            );
                                            currentBlock.totalPrice += currentPrice;
                                        }
                                    });
                                }
                            }
                        } else if (btnText.contains("Next") || btnText.contains("➡️")) {
                            nextButton = button;
                        }
                        if (btnText.matches(".*\\d+.*") && !btnText.contains("Next") && !btnText.contains("➡️")) listingCount++;
                    }
                }

                if (!uniqueIndices.isEmpty() || !duplicateIndices.isEmpty() || !historyIndices.isEmpty() || !goldIndices.isEmpty()) {
                    final double finalMaxPrice = maxPrice;
                    handler.post(() -> {
                        if (blockList.isEmpty()) {
                            blockList.add(new com.fahim.mt.BlockData(true));
                        }
                        com.fahim.mt.BlockData currentBlock = blockList.get(blockList.size() - 1);
                        
                        // Update persistent counts
                        totalRed += uniqueIndices.size();
                        totalYellow += duplicateIndices.size();
                        totalBlue += historyIndices.size();
                        totalGold += goldIndices.size();

                        currentBlock.matchCount = uniqueIndices.size() + duplicateIndices.size() + historyIndices.size() + goldIndices.size();
                        currentBlock.matchIndices.addAll(uniqueIndices);
                        currentBlock.duplicateMatchIndices.addAll(duplicateIndices);
                        currentBlock.historyMatchIndices.addAll(historyIndices);
                        currentBlock.goldMatchIndices.addAll(goldIndices);
                        currentBlock.maxPrice = finalMaxPrice;
                        currentBlock.isBlankPageGroup = false;

                        if (interactionListener != null) interactionListener.onGridUpdated();
                    });
                }

                if (nextButton != null) {
                    TdApi.InlineKeyboardButton finalNext = nextButton;
                    handler.post(() -> {
                        // Logic for blank page grouping
                        if (!blockList.isEmpty()) {
                            com.fahim.mt.BlockData lastBlock = blockList.get(blockList.size() - 1);
                            if (lastBlock.matchCount == 0) {
                                if (blockList.size() > 1) {
                                    com.fahim.mt.BlockData prevBlock = blockList.get(blockList.size() - 2);
                                    if (prevBlock.isBlankPageGroup) {
                                        prevBlock.blankPageCount++;
                                        blockList.remove(blockList.size() - 1);
                                    } else {
                                        lastBlock.isBlankPageGroup = true;
                                    }
                                } else {
                                    lastBlock.isBlankPageGroup = true;
                                }
                            }
                        }
                        blockList.add(new com.fahim.mt.BlockData(true));
                        if (interactionListener != null) interactionListener.onGridUpdated();
                    });
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
