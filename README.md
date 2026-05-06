# amanOS

Personal Android agent ecosystem — a monorepo of focused modules that communicate through a shared `core` contract layer.

## Build, Release, and Updates

- CI workflow: `.github/workflows/android-ci.yml`
- Release workflow: `.github/workflows/android-release.yml`
- Updater framework module: `:updater`
- Full guide: `docs/ci-cd-and-updates.md`
- Signing/distribution guide: `docs/signing-and-distribution-guide.md`

Each module is an independent Android library with its own ContentProvider and BroadcastReceiver. No module talks to another directly; everything goes through `AgentContract` URIs, actions, and events.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Ladybug (2024.2+) or newer |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | Platform 35, Build-Tools 34+ |
| Gradle | 8.7 (downloaded automatically by the wrapper) |

No separate Gradle installation needed — `gradlew.bat` / `gradlew` handles everything.

---

## Install & Build

### 1. Clone / open

```
E:\AndroidProjects\amanOS\
```

Open the root folder in Android Studio. It will sync Gradle automatically on first open (downloads ~200 MB of dependencies on a fresh machine).

### 2. Build core from the command line

```powershell
# Windows
.\gradlew.bat :core:assembleDebug

# Mac / Linux
./gradlew :core:assembleDebug
```

Output: `core/build/outputs/aar/core-debug.aar`

### 3. Build all modules at once (once more modules exist)

```powershell
.\gradlew.bat assembleDebug
```

### 4. Open in Android Studio

`File → Open → select the amanOS/ root folder`

Android Studio detects the `settings.gradle.kts` and loads every included module as a Gradle subproject. You can build individual modules via the Gradle panel or the terminal.

---

## Monorepo Structure

```
amanOS/
├── gradle/
│   ├── libs.versions.toml          ← single version catalog for ALL modules
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── core/                           ← shared contracts, built first
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/amanOS/core/
│           ├── AgentPermission.kt
│           ├── AgentContract.kt
│           ├── AgentResult.kt
│           ├── AgentEventBus.kt
│           └── BaseAgentProvider.kt
├── contacts/                       ← (future module)
├── messaging/                      ← (future module)
├── build.gradle.kts                ← root — plugin declarations only
├── settings.gradle.kts             ← include(:core), include(:contacts), ...
├── gradle.properties
└── local.properties                ← sdk.dir (git-ignored, machine-specific)
```

---

## Core Contracts Reference

### `AgentPermission`

Single custom permission all modules declare and enforce.

```kotlin
// In any module's AndroidManifest.xml — enforce on provider/receiver:
android:permission="com.amanOS.USE"

// In code:
AgentPermission.USE  // → "com.amanOS.USE"
```

---

### `AgentContract`

Single source of truth for every URI, action string, broadcast event string, and extras key. Add a new nested `object` here for each new module — never hardcode strings elsewhere.

```kotlin
// Reading contacts via ContentResolver:
val uri = Uri.parse(AgentContract.Contacts.URI_CONTACTS)
contentResolver.query(uri, ...)

// Sending an SMS action intent:
Intent(AgentContract.Messaging.ACTION_SEND).apply {
    putExtra(AgentContract.Messaging.EXTRA_TO, "+1234567890")
    putExtra(AgentContract.Messaging.EXTRA_BODY, "Hello")
}

// Shared extras keys:
AgentContract.Extras.MODULE
AgentContract.Extras.RESULT_CODE
AgentContract.ResultCode.SUCCESS   // 200
AgentContract.ResultCode.NOT_FOUND // 404
AgentContract.ResultCode.ERROR     // 500
```

---

### `AgentResult`

Sealed return type for every ContentProvider operation and intent handler.

```kotlin
// Returning from a provider:
return AgentResult.Success(Bundle().apply {
    putString("name", contact.name)
})

return AgentResult.Error(message = "Database write failed")

return AgentResult.NotFound

// Serialising over IPC:
val bundle = result.toBundle()

// Deserialising on the caller side:
val result = AgentResult.fromBundle(resultBundle)
when (result) {
    is AgentResult.Success  -> // use result.data
    is AgentResult.Error    -> // use result.code, result.message
    is AgentResult.NotFound -> // handle 404
}
```

---

### `AgentEventBus`

Fires structured broadcasts that any module can listen to.

```kotlin
// In contacts module — after a call ends:
AgentEventBus.send(
    context = context,
    module  = "contacts",
    event   = AgentContract.Contacts.EVENT_CALL_ENDED,
    extras  = Bundle().apply {
        putString(AgentContract.Contacts.EXTRA_NUMBER, number)
        putLong(AgentContract.Contacts.EXTRA_DURATION, durationMs)
        putLong(AgentContract.Contacts.EXTRA_TIMESTAMP, System.currentTimeMillis())
    }
)

// Receiving in any module's BroadcastReceiver:
class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val module = intent.getStringExtra(AgentContract.Extras.MODULE)
        val event  = intent.getStringExtra(AgentContract.Extras.EVENT)
        // handle event
    }
}
```

---

### `BaseAgentProvider`

Abstract base for every module's ContentProvider. Handles URI matching — subclasses only implement data logic.

```kotlin
class ContactsProvider : BaseAgentProvider() {

    override fun getAuthority() = AgentContract.Contacts.AUTHORITY

    override fun registerUris(matcher: UriMatcher) {
        matcher.addURI(providerAuthority, "contacts",    CODE_ALL)
        matcher.addURI(providerAuthority, "contacts/#",  CODE_ID)
    }

    override fun onInitialize(): Boolean {
        // inject DAOs here (Hilt EntryPoint or manual)
        return true
    }

    override fun onQuery(uri: Uri, code: Int, projection: Array<String>?,
                         selection: String?, selectionArgs: Array<String>?,
                         sortOrder: String?): Cursor? {
        return when (code) {
            CODE_ALL -> dao.queryAll()
            CODE_ID  -> dao.queryById(uri.lastPathSegment!!.toLong())
            else     -> noMatch(uri)
        }
    }

    override fun onInsert(uri: Uri, code: Int, values: ContentValues?): Uri? { ... }
    override fun onUpdate(...): Int { ... }
    override fun onDelete(...): Int { ... }

    companion object {
        private const val CODE_ALL = 1
        private const val CODE_ID  = 2
    }
}
```

---

## Building a New Module — Step by Step

### Step 1 — Register in `settings.gradle.kts`

```kotlin
include(":contacts")
```

### Step 2 — Create the module directory

```
amanOS/contacts/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    └── kotlin/com/amanOS/contacts/
        ├── ContactsProvider.kt
        ├── ContactsReceiver.kt
        ├── ContactsRepository.kt
        ├── ContactsModule.kt          ← Hilt module
        └── model/
            └── Contact.kt
```

### Step 3 — `contacts/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "com.amanOS.contacts"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))          // ← always first
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    // add Room here if the module has local storage:
    // implementation(libs.room.runtime)
    // ksp(libs.room.compiler)
}
```

### Step 4 — `contacts/src/main/AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Declare uses of the shared permission -->
    <uses-permission android:name="com.amanOS.USE" />

    <application>

        <provider
            android:name=".ContactsProvider"
            android:authorities="com.amanOS.contacts.provider"
            android:exported="true"
            android:permission="com.amanOS.USE" />

        <receiver
            android:name=".ContactsReceiver"
            android:exported="true"
            android:permission="com.amanOS.USE">
            <intent-filter>
                <action android:name="com.amanOS.contacts.action.CALL" />
                <action android:name="com.amanOS.contacts.action.ADD" />
                <action android:name="com.amanOS.contacts.action.EDIT" />
                <action android:name="com.amanOS.contacts.action.DELETE" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

### Step 5 — Add constants to `AgentContract` (in `:core`)

Open `core/src/main/kotlin/com/amanOS/core/AgentContract.kt` and add a new nested object:

```kotlin
object Notes {
    const val AUTHORITY = "com.amanOS.notes.provider"
    const val URI_NOTES = "content://$AUTHORITY/notes"

    const val ACTION_CREATE = "com.amanOS.notes.action.CREATE"
    const val ACTION_DELETE = "com.amanOS.notes.action.DELETE"

    const val EVENT_NOTE_CREATED = "com.amanOS.notes.event.NOTE_CREATED"
    const val EVENT_NOTE_DELETED = "com.amanOS.notes.event.NOTE_DELETED"

    const val EXTRA_NOTE_ID    = "note_id"
    const val EXTRA_TITLE      = "title"
    const val EXTRA_BODY       = "body"
    const val EXTRA_TIMESTAMP  = "timestamp"
}
```

Never hardcode the strings in the module itself — always reference `AgentContract`.

### Step 6 — Implement the ContentProvider

```kotlin
@AndroidEntryPoint
class ContactsProvider : BaseAgentProvider() {

    @Inject lateinit var repository: ContactsRepository

    override fun getAuthority() = AgentContract.Contacts.AUTHORITY

    override fun registerUris(matcher: UriMatcher) {
        matcher.addURI(providerAuthority, "contacts",   CODE_ALL)
        matcher.addURI(providerAuthority, "contacts/#", CODE_ID)
    }

    // onInitialize() is called after Hilt injection is ready
    override fun onInitialize() = true

    override fun onQuery(uri: Uri, code: Int, ...): Cursor? = when (code) {
        CODE_ALL -> repository.queryAllAsCursor()
        CODE_ID  -> repository.queryByIdAsCursor(uri.lastPathSegment!!.toLong())
        else     -> noMatch(uri)
    }

    // ... onInsert, onUpdate, onDelete
}
```

### Step 7 — Implement the BroadcastReceiver

```kotlin
class ContactsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AgentContract.Contacts.ACTION_CALL -> {
                val number = intent.getStringExtra(AgentContract.Contacts.EXTRA_NUMBER)
                // initiate call
                AgentEventBus.send(context, "contacts",
                    AgentContract.Contacts.EVENT_CALL_STARTED,
                    Bundle().apply { putString(AgentContract.Contacts.EXTRA_NUMBER, number) }
                )
            }
            AgentContract.Contacts.ACTION_ADD -> { /* ... */ }
        }
    }
}
```

### Step 8 — Build and verify

```powershell
.\gradlew.bat :contacts:assembleDebug
# → contacts/build/outputs/aar/contacts-debug.aar
```

---

## Rules (apply to every module)

| Rule | Reason |
|------|--------|
| All string constants live in `AgentContract` | Prevents drift between caller and provider |
| All providers extend `BaseAgentProvider` | Consistent URI matching, no boilerplate |
| All broadcasts go through `AgentEventBus.send()` | Consistent MODULE/EVENT/TIMESTAMP shape |
| All results use `AgentResult` | Caller-side `fromBundle()` always works |
| No module imports another module | Modules are peers; only `:core` is shared |
| No hardcoded authorities or action strings | Everything via `AgentContract` constants |
| No UI, no Activities in non-UI modules | Keep library modules headless |

---

## Module Roadmap

| Module | `include` key | Status |
|--------|---------------|--------|
| core | `:core` | **done** |
| contacts | `:contacts` | planned |
| messaging | `:messaging` | planned |
| notes | `:notes` | planned |
| tasks | `:tasks` | planned |
| device | `:device` | planned |
| calendar | `:calendar` | planned |
| location | `:location` | planned |
| activity | `:activity` | planned |
| health | `:health` | planned |
| finance | `:finance` | planned |
| camera | `:camera` | planned |
| voice | `:voice` | planned |
| browser | `:browser` | planned |
| smarthome | `:smarthome` | planned |
| automation | `:automation` | planned |
| router | `:router` | planned |
