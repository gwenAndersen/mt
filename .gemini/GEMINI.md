## Gemini Added Memories
- The user frequently asks about the project's remaining tasks and what to work on next.
- The user wants me to be autonomous and not ask for permission for logical steps like adding dependencies or creating new methods. I should proceed with such actions directly.

## Future Tasks
- **Implement SMS Detection and Processing:** Develop logic within `SmsReceiver.java` to filter incoming SMS messages from a specific service, extract relevant transaction information, and then send this data to a Telegram chat. This involves implementing the filtering and data extraction logic. [COMPLETED]
- **Enhance SMS Processing Reliability (Chinese OS):** Integrate WorkManager to handle the background processing and Telegram sending of SMS data. This will improve the reliability of the feature, especially on Android devices with aggressive background process management (e.g., Chinese ROMs). [COMPLETED]

## Development Notes
- **Secure Channel for Internal Testing:** A secure channel has been implemented for internal testing purposes. Messages prefixed with `#st` will be sent to the secure channel ID (`-4967099303`). This is a temporary feature for development and will be removed in the final version. This is a company version of the app, and this security mechanism is for discussing sensitive information that cannot go outside.
