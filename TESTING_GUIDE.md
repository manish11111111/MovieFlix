# 🧪 Subscription System Testing Guide

## Quick Test Checklist

### **Prerequisites:**

- ✅ Build successful (confirmed)
- ✅ Firebase Firestore configured
- ✅ eSewa SDK integrated
- ✅ Test credentials available

---

## 📋 Test Scenarios

### **Scenario 1: User WITHOUT Active Subscription**

**Steps:**

1. Open MovieFlix app
2. Login with a user account that has NO subscription
3. Navigate to "Subscribe" tab (bottom navigation)

**Expected Results:**

- ✅ Header shows "Premium Plans"
- ✅ Available subscription plans are displayed
- ✅ "Pay with eSewa" buttons are visible
- ✅ No "You're All Set!" message shown
- ✅ No current plan card displayed

---

### **Scenario 2: Purchase a Subscription (eSewa Test)**

**Steps:**

1. User is on Subscribe tab with NO active subscription
2. Click "Pay with eSewa" on any plan
3. eSewa SDK screen opens
4. Use eSewa test credentials:
    - **Client ID:** `JB0BBQ4aD0UqIThFJwAKBgAXEUkEGQUBBAwdOgABHD4DChwUAB0R`
    - **Secret Key:** `BhwIWQQADhIYSxILExMcAgFXFhcOBwAKBgAXEQ==`
    - **Environment:** TEST
5. Complete payment in eSewa test mode
6. Return to app

**Expected Results:**

- ✅ Toast message: "Subscription Activated Successfully!"
- ✅ UI automatically refreshes
- ✅ Subscribe tab now shows "Active Subscription" header
- ✅ Current plan card is displayed with plan name and expiry date
- ✅ "You're All Set!" message is displayed
- ✅ Subscription plans are NO LONGER visible

**Firebase Verification:**

1. Open Firebase Console → Firestore
2. Check `subscriptions` collection:
   ```javascript
   {
     userId: "user_uid",
     planId: "plan_doc_id",
     status: "active",
     endDate: Timestamp (30 days from now),
     createdAt: Timestamp
   }
   ```
3. Check `users` collection → user document:
   ```javascript
   {
     hasActiveSubscription: true,
     lastSubscriptionUpdate: Timestamp
   }
   ```
4. Check `Dashboard_stats`:
   ```javascript
   {
     activeSubs: (incremented by 1)
   }
   ```
5. Check `activities` collection:
   ```javascript
   {
     title: "User Name purchased Plan Name",
     type: "sub",
     timestamp: Timestamp
   }
   ```

---

### **Scenario 3: User WITH Active Subscription**

**Steps:**

1. Login with a user who has an active subscription (from Scenario 2)
2. Navigate to "Subscribe" tab

**Expected Results:**

- ✅ Header shows "Active Subscription" (not "Premium Plans")
- ✅ Current plan card is displayed with:
    - Plan name (fetched from subscription_plans)
    - Expiry date formatted as "MMM dd, yyyy"
    - Green checkmark icon
- ✅ "You're All Set!" message box displayed
- ✅ Message text: "Your subscription is active. Enjoy unlimited access to all movies and shows."
- ✅ Subscription plans are NOT displayed
- ✅ No "Pay with eSewa" buttons visible

---

### **Scenario 4: Payment Cancellation**

**Steps:**

1. User with NO subscription clicks "Pay with eSewa"
2. eSewa screen opens
3. User clicks "Cancel" or back button

**Expected Results:**

- ✅ Toast message: "Payment Cancelled"
- ✅ Returns to Subscribe tab
- ✅ No subscription created in Firebase
- ✅ Plans still displayed (user can try again)

---

### **Scenario 5: Expired Subscription**

**Manual Test Setup:**

1. Create a subscription in Firebase with `endDate` in the past
2. Set `status: "active"`

**Steps:**

1. Login with this user
2. Navigate to Subscribe tab

**Expected Results:**

- ✅ Subscription is NOT recognized as active (date validation)
- ✅ UI shows "Premium Plans" header
- ✅ Available plans are displayed
- ✅ User can purchase a new subscription

---

## 🔧 Firebase Manual Testing

### **Create Test Subscription Plans:**

In Firebase Console → Firestore → `subscription_plans`:

**Plan 1: Basic**

```javascript
{
  name: "Basic Plan",
  price: 199,
  duration: 30,
  description: "SD quality streaming"
}
```

**Plan 2: Premium**

```javascript
{
  name: "Premium Plan",
  price: 499,
  duration: 30,
  description: "HD quality streaming"
}
```

**Plan 3: VIP**

```javascript
{
  name: "VIP Plan",
  price: 999,
  duration: 90,
  description: "4K quality streaming"
}
```

---

### **Manually Create Test Subscription:**

In Firebase Console → Firestore → `subscriptions`:

```javascript
{
  userId: "paste_user_uid_here",
  planId: "paste_plan_doc_id_here",
  status: "active",
  endDate: Timestamp (set to future date),
  createdAt: Timestamp (now)
}
```

Then update `users` collection → user document:

```javascript
{
  hasActiveSubscription: true,
  lastSubscriptionUpdate: Timestamp (now)
}
```

---

## 📱 UI Testing Checklist

### **Visual Elements:**

**When NO subscription:**

- [ ] Gold/yellow icon displayed
- [ ] "Premium Plans" title
- [ ] Plan cards with pricing
- [ ] Green "Pay with eSewa" buttons
- [ ] No active subscription card

**When HAS subscription:**

- [ ] Green checkmark icon
- [ ] "Active Subscription" title
- [ ] Current plan card with gradient border
- [ ] Plan name displayed correctly
- [ ] Expiry date formatted correctly
- [ ] "You're All Set!" message box
- [ ] No plan cards displayed
- [ ] No payment buttons

---

## 🐛 Debug Tips

### **If Subscription Not Created:**

1. Check Logcat for errors:
   ```
   Tag: ESewaPayment
   Look for: "Subscription created: {id}"
   ```
2. Verify Firebase rules allow write to `subscriptions`
3. Check user authentication status
4. Verify plan data is passed correctly

### **If UI Not Updating:**

1. Check snapshot listener is active
2. Verify `hasActiveSubscription` state updates
3. Check `endDate` field exists and is in future
4. Verify `planId` exists in subscription document
5. Check plan exists in `subscription_plans` collection

### **If Plan Name Not Showing:**

1. Verify `planId` in subscription matches a plan document ID
2. Check plan document has `name` field
3. Look for Firestore permission errors in Logcat

---

## 📊 Admin Dashboard Verification

After subscription creation:

1. Login as admin
2. Navigate to Admin Panel → Subscriptions
3. Verify new subscription appears with:
    - ✅ User name (relational join works)
    - ✅ Plan name (relational join works)
    - ✅ Plan price
    - ✅ Status: Active
    - ✅ End date

4. Check Activities tab:
    - ✅ "User purchased Plan" entry exists

5. Check Dashboard stats:
    - ✅ Active Subs count incremented

---

## 🎯 Success Criteria

All scenarios pass ��:

- [ ] User without subscription sees plans
- [ ] Purchase flow completes successfully
- [ ] Subscription created in Firebase with correct structure
- [ ] User document updated
- [ ] Dashboard stats updated
- [ ] Activity logged
- [ ] UI updates automatically after purchase
- [ ] User with active subscription sees status (not plans)
- [ ] Plan name fetched relationally from subscription_plans
- [ ] Expired subscriptions not counted as active
- [ ] Payment cancellation handled gracefully

---

## 📞 Support

If issues occur:

1. Check `SUBSCRIPTION_IMPLEMENTATION_COMPLETE.md` for structure details
2. Verify all field names match SubscriptionViewModel
3. Check Firebase Firestore rules
4. Review Logcat for detailed error messages

---

**Last Updated:** February 18, 2026  
**Status:** Ready for Testing ✅

