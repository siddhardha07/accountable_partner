# Copilot Instructions for Accountable Android App

## Project Overview
**Accountable** is an Android app (Java, not Kotlin) for app usage accountability between friends. Users can select apps to restrict, save selections to Firebase, and allow friends to grant temporary access via notifications and access codes.

## Architecture & Core Components

### Data Flow Pattern
- **MainActivity** → Navigation hub with "My Apps" and "Friend Control" buttons
- **MyAppsActivity** → Lists installed apps with checkboxes, saves selections to Firestore
- **FriendControlActivity** → Manages friend details and generates access codes via SharedPreferences
- **AccessRequestActivity** → Handles friend approval/denial of app access requests
- **Firebase Integration** → Firestore for app selections, Realtime Database for access requests, FCM for notifications

### Key Model Classes
```java
// Core data models in app/src/main/java/com/example/accountable/
AppModel.java     // App info with selection state (Firestore-compatible)
AppInfo.java      // Runtime app info with Drawable icons (non-serializable)
AccessRequest.java // Friend access request data structure
FriendControl.java // Friend relationship management
```

### Adapter Pattern Usage
- **AppAdapter** (MyAppsActivity) - Displays installed apps with checkboxes, handles selection state
- **FriendAppAdapter** (FriendActivity) - Shows restricted apps to friends (read-only)
- Both use `item_app.xml` and `item_friend_app.xml` respectively

## Development Conventions

### Firebase Integration Patterns
```java
// Firestore document structure for user app selections
"users/{userId}" → { "selectedApps": ["com.package.name1", "com.package.name2"] }

// Realtime Database for access requests  
"requests/{requestId}" → AccessRequest object with status tracking
```

### State Management
- **SharedPreferences** for friend details (`AccountablePrefs`)
- **Firestore** for persistent app selections (static userId "user1" for now)
- **Firebase Realtime Database** for real-time access requests between friends
- **FCM tokens** for push notifications (handled by AccountableFirebaseMessagingService)

### UI Patterns
- **Toolbar integration** - All activities use Material Design toolbars with back navigation
- **RecyclerView + LinearLayoutManager** - Standard pattern for all list displays
- **Material Design components** - TextInputLayout, MaterialButton for consistent UI
- **Theme inheritance** - `Theme.Accountable` extends `Theme.MaterialComponents.Light.NoActionBar`

## Build & Development Workflow

### Essential Commands
```bash
# Build the project (from project root)
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

### Package Management with gradle/libs.versions.toml
```toml
# Version catalog approach - modify gradle/libs.versions.toml for dependency updates
[versions]
agp = "8.13.0"           # Android Gradle Plugin
kotlin = "2.0.21"        # Kotlin version
firebase-bom = "33.1.0"  # Firebase Bill of Materials

[libraries]
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
firebase-database = { group = "com.google.firebase", name = "firebase-database" }
```

### Critical Permissions & Manifest Setup
- `QUERY_ALL_PACKAGES` permission required for listing installed apps (Android 11+)
- Firebase services registered in AndroidManifest.xml
- Activities that should be classes are incorrectly registered (needs cleanup)

## Firebase Configuration Specifics

### Authentication & User Management
- Currently uses static userId "user1" - **expand to proper Firebase Auth later**
- Friend relationships managed through SharedPreferences and access codes
- Access codes are 6-digit random numbers for temporary friend authentication

### Data Persistence Strategy
```java
// Save pattern in MyAppsActivity
List<String> selectedPackages = adapter.getSelectedPackageNames();
Map<String, Object> data = new HashMap<>();
data.put("selectedApps", selectedPackages);
db.collection("users").document(userId).set(data);

// Load pattern with null checking
List<String> selectedPackages = (List<String>) documentSnapshot.get("selectedApps");
if (selectedPackages != null) {
    // Update UI state
}
```

### Notification System
- **AccountableFirebaseMessagingService** handles FCM notifications
- **AccessRequestActivity** processes friend requests via notification taps
- MainActivity.java:
  - Purpose: Main interface with comprehensive notification listener management and reliability features
  - Current State: Enhanced with lifecycle methods, health checks, and automatic reconnection for maximum reliability
  - Key Code Segments: setupAccessRequestListener() with FCM token matching, startListenerHealthCheck() for periodic monitoring, automatic reconnection on failures
  - Dependencies: Firestore pendingNotifications collection, Handler for periodic checks, notification permission handling
- Notification channel "access_requests" for Android 8.0+ compatibility
- Automatic reconnection on listener failures with 5-second retry delay
- Periodic health checks every 30 seconds to ensure listener stays active

## Common Debugging Patterns

### Firebase Debug Checklist
1. Verify `google-services.json` is in `app/` directory
2. Check Firebase project configuration matches package name `com.example.accountable`
3. Ensure Firestore rules allow read/write for development
4. Test Firebase connection with simple document read/write operations

### RecyclerView State Issues
- **Selection persistence** - Use `setOnCheckedChangeListener(null)` before programmatic checkbox updates
- **Item click conflicts** - Handle both checkbox and row click events properly
- **Data updates** - Call `notifyDataSetChanged()` after adapter data modifications

### Activity Navigation
- All activities use `finish()` for back navigation rather than fragment transactions
- Toolbar back buttons implemented via `getSupportActionBar().setDisplayHomeAsUpEnabled(true)`

## Extending the App

### Adding New Features
1. **New Activities** - Follow the toolbar + RecyclerView pattern established
2. **Firebase Collections** - Use consistent document structure with error handling
3. **Model Classes** - Separate serializable (Firebase) from runtime-only (Drawable) data
4. **Permissions** - Add new permissions to AndroidManifest.xml with proper API level handling

### Code Quality Guidelines
- **Java conventions** - Use explicit imports, proper access modifiers, and consistent naming
- **Error handling** - Always include Firebase failure listeners with user-friendly messages
- **Resource naming** - Follow Android naming conventions (snake_case for layouts, camelCase for IDs)
- **Memory management** - Avoid static references to Context, use appropriate lifecycle awareness

## Testing & Validation
- **Unit tests** in `app/src/test/java/com/example/accountable/ExampleUnitTest.kt`
- **Instrumented tests** in `app/src/androidTest/java/com/example/accountable/ExampleInstrumentedTest.kt`
- **Manual testing** - Test Firebase connectivity, app listing, and friend workflows on physical devices
- **Permission testing** - Verify QUERY_ALL_PACKAGES works across different Android versions (API 26+)