# 🛡️ Accountable - Partner-Based App Accountability

> A revolutionary Android app that enables friends to help each other stay accountable with their digital habits through real-time partner approval system.

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Android](https://img.shields.io/badge/platform-Android-green)]()
[![API Level](https://img.shields.io/badge/API-26%2B-blue)]()
[![Firebase](https://img.shields.io/badge/backend-Firebase-orange)]()
[![Java](https://img.shields.io/badge/language-Java-red)]()

## 📱 **What is Accountable?**

Accountable transforms digital self-control by leveraging the power of friendship. Instead of relying on willpower alone, users can designate trusted friends as "Accountability Partners" who must approve access to restricted apps. This creates a supportive system where friends help each other maintain healthy digital boundaries.

### 🎯 **Core Concept**

- **User** selects apps they want to restrict (social media, games, etc.)
- **Accountability Partner (AP)** receives real-time notifications when user wants access
- **Time-based approvals** allow temporary access (1 min to 2 hours)
- **Comprehensive blocking** prevents app usage until partner approval

---

## ✨ **Key Features**

### 🔐 **Smart App Blocking**

- **AccessibilityService-based monitoring** - Comprehensive app blocking that works across the entire system
- **Temporary access system** - Partners can grant time-limited access (minutes to hours)
- **Immediate enforcement** - Apps are blocked instantly when time expires
- **Background monitoring** - Works even when Accountable app is closed

### 👥 **Partner Approval System**

- **Real-time notifications** - Partners receive instant push notifications when user requests access
- **Okta-style approval interface** - Clean, professional approval screen with time selection
- **NumberPicker time controls** - Easy selection of minutes and seconds for access duration
- **Bi-directional accountability** - Partners can also be users, creating mutual support networks

### 🔄 **Reliable Communication**

- **Firebase Cloud Messaging (FCM)** - Push notifications with automatic reconnection
- **Health monitoring system** - Periodic checks ensure notification delivery stays active
- **Lifecycle management** - Smart reconnection when app resumes from background
- **Cross-device synchronization** - Works seamlessly across multiple devices

### 🛡️ **Robust Security**

- **Firebase Authentication** - Secure email/password authentication
- **Firestore real-time database** - Instant synchronization of app selections and access requests
- **Partnership verification** - Secure partner relationship management
- **Access code system** - 6-digit codes for partner verification

---

## 🏗️ **Technical Architecture**

### **Frontend (Android)**

```
📱 Android App (Java, API 26+)
├── 🎨 Material Design UI
├── ♿ AccessibilityService (App Monitoring)
├── 🔄 Real-time Listeners (Firestore)
├── 📱 Push Notifications (FCM)
└── 🧪 Lifecycle Management
```

### **Backend (Firebase)**

```
☁️ Firebase Suite
├── 🔐 Authentication (Email/Password)
├── 📊 Firestore (Real-time Database)
│   ├── 👤 Users Collection
│   ├── 📱 App Selections
│   ├── 🤝 Partnerships
│   ├── ⏰ Temporary Access
│   └── 📬 Pending Notifications
├── 📢 Cloud Messaging (FCM)
└── 🔄 Real-time Synchronization
```

---

## 🚀 **Getting Started**

### **Prerequisites**

- Android Studio Arctic Fox or later
- Android device/emulator (API level 26+)
- Firebase project with authentication enabled
- Google Services configuration

### **Installation**

1. **Clone the repository**

```bash
git clone https://github.com/siddhardha07/accountable_partner.git
cd accountable_partner
```

2. **Setup Firebase**

   - Create a new Firebase project at [Firebase Console](https://console.firebase.google.com)
   - Enable Authentication (Email/Password)
   - Enable Firestore Database
   - Enable Cloud Messaging
   - Download `google-services.json` and place it in `app/` directory

3. **Build and run**

```bash
./gradlew clean assembleDebug
./gradlew installDebug
```

### **Firebase Configuration**

#### **Firestore Collections Structure**

```javascript
// Users Collection
users/{userId} {
  email: "user@example.com",
  displayName: "John Doe",
  fcmToken: "fcm_token_here",
  mainPartnerId: "partner_user_id",
  partners: ["partner1_id", "partner2_id"],
  selectedApps: ["com.instagram.android", "com.snapchat.android"]
}

// Access Requests Collection
accessRequests/{requestId} {
  userId: "requesting_user_id",
  partnerId: "partner_user_id",
  appName: "Instagram",
  packageName: "com.instagram.android",
  status: "pending", // pending, approved, denied
  requestTime: timestamp,
  duration: 300 // seconds
}

// Temporary Access Collection
temporaryAccess/{userId}_{packageName} {
  userId: "user_id",
  packageName: "com.instagram.android",
  expiryTime: timestamp,
  grantedBy: "partner_user_id"
}
```

#### **Firestore Security Rules**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Access requests readable by user and partner
    match /accessRequests/{requestId} {
      allow read, write: if request.auth != null &&
        (request.auth.uid == resource.data.userId ||
         request.auth.uid == resource.data.partnerId);
    }

    // Temporary access readable by user
    match /temporaryAccess/{docId} {
      allow read, write: if request.auth != null &&
        request.auth.uid == resource.data.userId;
    }
  }
}
```

---

## 📱 **User Flow**

### **1. Initial Setup**

```
📧 Sign Up/Login → 🤝 Add Partner → 📱 Select Apps to Restrict → ✅ Enable Accessibility
```

### **2. Daily Usage**

```
📱 Try to open restricted app → 🚫 App blocked → 🔔 Request partner access → ⏱️ Partner approves with time → ✅ Temporary access granted
```

### **3. Partner Experience**

```
🔔 Receive notification → 📱 Open approval screen → ⏱️ Select time duration → ✅ Approve/Deny → 📧 User gets access
```

---

## 🔧 **Development**

### **Key Components**

#### **AppMonitoringService.java**

- AccessibilityService for comprehensive app monitoring
- Handles temporary access checking and enforcement
- Manages app blocking logic with proper precedence

#### **PartnerApprovalActivity.java**

- Okta-style approval interface with Material Design
- NumberPicker components for time selection
- Real-time request processing and response

#### **MainActivity.java**

- Navigation hub with notification listener management
- Lifecycle handling for reliability
- Health monitoring and automatic reconnection

### **Architecture Patterns**

- **Repository Pattern** - Firebase operations abstracted into service layers
- **Observer Pattern** - Real-time listeners for data synchronization
- **Singleton Pattern** - Firebase instances and shared preferences
- **Adapter Pattern** - RecyclerView adapters for dynamic lists

### **Performance Optimizations**

- ⚡ Reduced Firebase calls during startup
- 🤫 Silent operations for better UX
- ⏱️ Optimized health check frequency (2-minute intervals)
- 🧹 Minimal logging for production performance

---

## 📊 **Testing**

### **Current Testing Status**

- ✅ **Manual Testing** - Comprehensive user flow testing
- ✅ **Firebase Integration** - Real-time data synchronization verified
- ✅ **Cross-device Testing** - Notification delivery confirmed
- 🔄 **Unit Tests** - Planned (see TODO.md)
- 🔄 **Integration Tests** - Planned (see TODO.md)

### **Test Scenarios**

- Partner approval workflow (happy path)
- Temporary access expiration handling
- Network connectivity edge cases
- Background app monitoring reliability
- Notification delivery across devices

---

## 🚧 **Roadmap**

See [TODO.md](TODO.md) for detailed feature roadmap including:

- 🔄 **Multi-Partner System** - Support up to 4 accountability partners with escalation
- 🔔 **Notifications Center** - Centralized notification management
- 👤 **Profile Management** - Side navigation with user settings
- 🎨 **UI Enhancements** - Modern Material You design system
- 🧪 **Comprehensive Testing** - Unit, integration, and E2E test suites

---

## 🤝 **Contributing**

We welcome contributions! Here's how to get started:

### **Development Setup**

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Setup your Firebase project for testing
4. Make your changes with proper testing
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### **Code Style**

- Follow Android Java conventions
- Use meaningful variable and method names
- Add comments for complex logic
- Maintain consistent indentation (4 spaces)
- Include error handling for Firebase operations

### **Pull Request Guidelines**

- Describe the feature/fix in detail
- Include screenshots for UI changes
- Test on multiple devices if possible
- Update documentation if needed
- Ensure no breaking changes to existing functionality

---

## 📄 **License**

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2025 Siddhardha

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation...
```

---

## 🙏 **Acknowledgments**

- **Firebase Team** - For providing robust backend infrastructure
- **Material Design** - For beautiful, consistent UI components
- **Android Accessibility** - For enabling comprehensive app monitoring
- **Open Source Community** - For inspiration and best practices

---

## 📞 **Support & Contact**

- 🐛 **Issues**: [GitHub Issues](https://github.com/siddhardha07/accountable_partner/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/siddhardha07/accountable_partner/discussions)
- 📧 **Email**: siddhardha07@gmail.com
- 🐦 **Twitter**: [@siddhardha07](https://twitter.com/siddhardha07)

---

## 📈 **Project Stats**

![GitHub Stars](https://img.shields.io/github/stars/siddhardha07/accountable_partner?style=social)
![GitHub Forks](https://img.shields.io/github/forks/siddhardha07/accountable_partner?style=social)
![GitHub Issues](https://img.shields.io/github/issues/siddhardha07/accountable_partner)
![GitHub Pull Requests](https://img.shields.io/github/issues-pr/siddhardha07/accountable_partner)

---

<div align="center">

**Built with ❤️ for digital wellness and friendship**

_Helping people build better relationships with technology, one approval at a time._

</div>
