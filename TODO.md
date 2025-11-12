# Accountable App - Feature Roadmap & TODO

## 📋 **Current Discussion Items**

Based on our conversation, here are the prioritized features to implement:

---

## 🔥 **HIGH PRIORITY** (Core UX Improvements)

### 1. **Exit on Save Navigation** ⭐

**Status:** Not Started
**Complexity:** Low (1-2 hours)
**Description:** When user clicks save in MyAppsActivity, automatically return to MainActivity after successful save.

- **Files to modify:** `MyAppsActivity.java`
- **Implementation:** Add `finish()` or `startActivity(MainActivity)` after save success
- **Priority:** HIGH (immediate UX improvement)

### 2. **Unsave App Requires AP Permission** ⭐⭐

**Status:** Not Started
**Complexity:** Medium (4-6 hours)
**Description:** When user wants to unselect/unsave an app, they need AP's approval.

- **Flow:** User unchecks app → Request sent to AP → AP approves/denies → App unsaved
- **Files to modify:** `MyAppsActivity.java`, `AppAdapter.java`, Firebase collections
- **Priority:** HIGH (core accountability feature)

**🤔 Questions:**

- Should this work for ALL apps or only "restricted" apps?
- What happens if AP denies the request - should the checkbox stay checked?
- Should there be a "pending" state while waiting for AP approval?

---

## 🎨 **MEDIUM PRIORITY** (UI/UX Enhancements)

### 3. **UI Enhancements** ⭐⭐

**Status:** Not Started
**Complexity:** Medium-High (8-12 hours)
**Description:** General UI improvements (to be discussed in detail later)

- **Priority:** MEDIUM (discuss after core features)

### 4. **App Logo Design** ⭐

**Status:** Not Started
**Complexity:** Low (2-3 hours)
**Description:** Create and implement app logo

- **Files to modify:** `app/src/main/res/drawable/`, `AndroidManifest.xml`
- **Priority:** MEDIUM (branding)

### 5. **Profile Side Navigation** ⭐⭐⭐

**Status:** Not Started
**Complexity:** High (6-8 hours)
**Description:** Hamburger menu in top-left with user details, settings, sign out

- **Implementation:** NavigationDrawer with user profile info
- **Files to create:** `activity_main.xml` (drawer layout), profile fragments
- **Priority:** MEDIUM (nice-to-have UX)

---

## 🚀 **ADVANCED FEATURES** (Complex Multi-AP System)

### 6. **Multiple Accountability Partners (Max 4)** ⭐⭐⭐⭐⭐

**Status:** Not Started
**Complexity:** Very High (20-30 hours)
**Description:** Revolutionary multi-AP system with hierarchy and fallback

#### **6a. Main AP Designation**

- First added AP becomes "main AP"
- Main AP has primary authority over user's apps

#### **6b. Adding Secondary APs**

- User can add up to 3 additional APs
- **🤔 Question:** Does adding secondary APs require main AP's permission?
- Each AP addition sends notification to existing APs

#### **6c. Main AP Transfer**

- User can change main AP designation
- **Requires current main AP's approval**
- **🤔 Question:** What if current main AP is unavailable for approval?

#### **6d. Request Escalation System**

- If main AP doesn't respond within 2 minutes → forward to AP #2
- If AP #2 doesn't respond within 2 minutes → forward to AP #3
- Continue until last AP or someone responds
- **🤔 Questions:**
  - What happens if NO AP responds? Auto-deny or allow access?
  - Should escalation timing be configurable?
  - How to handle timezone differences between user and APs?

**Database Schema Changes Needed:**

```javascript
// User document structure
{
  accountabilityPartners: [
    { userId: "ap1_id", role: "main", addedAt: timestamp },
    { userId: "ap2_id", role: "secondary", addedAt: timestamp },
    { userId: "ap3_id", role: "secondary", addedAt: timestamp },
    { userId: "ap4_id", role: "secondary", addedAt: timestamp }
  ],
  // ... other fields
}

// Access request structure
{
  requestId: "req_123",
  userId: "user_id",
  appName: "Instagram",
  status: "pending",
  currentApIndex: 0, // Which AP is currently handling
  escalationTimestamp: timestamp,
  // ... other fields
}
```

### 7. **Notifications Center** ⭐⭐⭐

**Status:** Not Started
**Complexity:** High (8-12 hours)
**Description:** Bell icon in top-right showing all notifications

**Notification Types:**

- AP Partnership Requests: "John wants you as AP" [Approve] [Deny]
- Main AP Change Requests: "Sarah wants you as main AP" [Approve] [Deny]
- App Access Requests: "Mike wants Instagram access for 5 min" [Approve] [Deny]
- Secondary AP Addition: "Lisa wants to add you as secondary AP" [Approve] [Deny]

**🤔 Questions:**

- Should notifications have read/unread states?
- Auto-clear notifications after action taken?
- Should there be notification categories/filtering?

---

## 🧹 **CODE QUALITY** (Future Maintenance)

### 8. **Complete Codebase Refactor & Testing** ⭐⭐⭐⭐

**Status:** Not Started
**Complexity:** Very High (40-60 hours)
**Description:** Professional code cleanup and comprehensive testing

#### **8a. Code Refactoring**

- Extract common Firebase operations into service classes
- Implement proper error handling and retry mechanisms
- Add comprehensive logging framework
- Follow Android architecture patterns (MVVM/MVP)
- Code documentation and inline comments

#### **8b. Unit Testing**

- Test Firebase operations (mocked)
- Test UI component behavior
- Test business logic and validation
- Target: 80%+ code coverage

#### **8c. Integration Testing**

- Test Firebase integration
- Test notification delivery
- Test multi-device scenarios
- Test offline/online state handling

#### **8d. End-to-End Testing**

- Complete user flows (signup → partner setup → app blocking → approval)
- Multi-AP escalation testing
- Cross-device notification testing
- Performance testing under load

---

## 🤔 **CLARIFICATION QUESTIONS**

1. **Unsave App Permission (Feature #2):**

   - Should ALL app changes require AP approval, or only removing apps from restricted list?
   - What's the user experience if AP denies unsave request?

2. **Multiple APs (Feature #6):**

   - Does adding secondary APs require main AP permission?
   - What happens if main AP is permanently unavailable for transfer approval?
   - Should escalation timing (2 minutes) be user-configurable?
   - What's the fallback if no AP responds to a request?

3. **Notifications (Feature #7):**

   - Should we implement in-app notifications, push notifications, or both?
   - How long should notifications persist before auto-expiring?

4. **Implementation Priority:**
   - Which 2-3 features should we tackle first?
   - Should we complete all simple features before starting complex multi-AP system?

---

## 📊 **SUGGESTED IMPLEMENTATION ORDER**

**Phase 1 (Quick Wins - 1 week):**

1. Exit on Save (#1)
2. App Logo (#4)
3. Basic UI polish (#3)

**Phase 2 (Core Features - 2 weeks):** 4. Unsave App Permission (#2) 5. Profile Side Navigation (#5)

**Phase 3 (Advanced - 3-4 weeks):** 6. Notifications Center (#7) 7. Multiple APs System (#6)

**Phase 4 (Quality - 2-3 weeks):** 8. Code Refactor & Testing (#8)

---

**Total Estimated Timeline:** 8-10 weeks for complete implementation

Let me know your thoughts on priority and which features to start with! 🚀
