# Messaging Module

Package: `com.amanOS.messaging`

Implements:
- SMS thread/message storage with Room
- incremental Telephony SMS sync with DataStore state + WorkManager
- send/receive pipelines with `AgentEventBus` contract events
- `MessagingProvider` based on `BaseAgentProvider`
- action handling via `AgentIntentActivity`
- minimal Compose thread list/detail UI

Build command:

```bash
./gradlew :messaging:assembleDebug
```

