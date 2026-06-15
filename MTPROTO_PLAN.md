# MTProto UserBot Implementation Plan for 'mt'

This document outlines the transition of the `mt` project from a simple Telegram Bot API sender to a full-featured **UserBot** using the **MTProto** protocol. This will allow the app to interact with other Telegram bots as if it were a real user.

## 1. Core Objectives
- **Interaction:** Send commands (e.g., `/start`), read bot responses, and click inline buttons.
- **Identity:** Act as a User (via phone number) rather than a Bot.
- **Protocol:** Use MTProto (via TDLib/TDLight).

## 2. Prerequisites (REQUIRED)
To move forward, the following credentials must be obtained from [my.telegram.org](https://my.telegram.org):
- **API_ID:** `[PENDING]`
- **API_HASH:** `[PENDING]`

## 3. Technology Stack
- **Library:** [TDLight-Java](https://github.com/tdlight-team/tdlight-java) (A Java wrapper for TDLib).
- **Runtime:** Android (Java).
- **Native Components:** Requires `.so` files (JNI) for ARM/ARM64 architectures.

## 4. Implementation Steps

### Phase 1: Dependency Setup
- Add TDLight-Java to `build.gradle`.
- Integrate the native TDLib binaries (`.so` files) into `app/src/main/jniLibs/`.

### Phase 2: Client Initialization
- Implement `TdClient` initialization.
- Handle the Telegram login flow (Phone number -> SMS Code -> 2FA Password).
- Set up local database storage for session persistence.

### Phase 3: Interaction Logic
- **Sending Commands:** Create a utility to send text messages to a specific bot username.
- **Reading Responses:** Implement an `UpdateHandler` to listen for `UpdateNewMessage` events from the target bot.
- **Button Interaction:** Implement `GetCallbackQueryAnswer` to simulate clicking inline buttons.

### Phase 4: SMS Integration
- Update `SmsProcessingWorker` to trigger MTProto commands instead of Bot API messages.

## 5. Architectural Notes
Unlike the Bot API, TDLib is stateful and heavy.
- **Battery:** Persistent connections will impact battery life.
- **Size:** Including native libraries will increase the APK size by ~50-100MB.
- **Stability:** TDLib handles network switches and encryption automatically, making it more robust than pure Java implementations.
