const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendAdminNotification = functions.firestore
    .document("activities/{activityId}")
    .onCreate((snap, context) => {
        const newValue = snap.data();
        const title = "New Admin Activity";
        const message = newValue.title; // e.g., "User Rahul was Banned"

        const payload = {
            notification: {
                title: title,
                body: message,
                icon: "default",
                click_action: "FLUTTER_NOTIFICATION_CLICK" // Standard for Android
            },
            topic: "admins" // Matches the subscription in Step 2
        };

        return admin.messaging().send(payload);
    });