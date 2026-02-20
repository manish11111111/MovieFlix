package com.manish.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.f1soft.esewapaymentsdk.EsewaConfiguration
import com.f1soft.esewapaymentsdk.EsewaPayment
import com.f1soft.esewapaymentsdk.ui.screens.EsewaPaymentActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.manish.demo.ui.components.CustomToastCompose
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Calendar
import java.util.Date

class ESewaPaymentHandler : ComponentActivity() {

    private val REQUEST_CODE_PAYMENT = 102
    private val client = OkHttpClient()

    // Test Credentials
    private val TEST_CLIENT_ID = "JB0BBQ4aD0UqIThFJwAKBgAXEUkEGQUBBAwdOgABHD4DChwUAB0R"
    private val TEST_SECRET_KEY = "BhwIWQQADhIYSxILExMcAgFXFhcOBwAKBgAXEQ=="

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Plan details
    private var planId: String = ""
    private var planName: String = ""
    private var planPrice: Double = 0.0
    private var planDuration: Int = 30

    // ✅ NEW: Extension details
    private var isExtension: Boolean = false
    private var existingSubscriptionId: String? = null

    // Compose State for Toast
    private var showToastState by mutableStateOf(false)
    private var toastMessageState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up Compose UI just to hold the Toast
        setContent {
            CustomToastUI()
        }

        // 1. Get data passed from your Compose UI
        planId = intent.getStringExtra("PLAN_ID") ?: ""
        planName = intent.getStringExtra("PLAN_NAME") ?: "Subscription"
        planPrice = intent.getDoubleExtra("PLAN_PRICE", 0.0)
        planDuration = intent.getIntExtra("PLAN_DURATION", 30)

        // ✅ NEW: Check if this is an extension
        isExtension = intent.getBooleanExtra("IS_EXTENSION", false)
        existingSubscriptionId = intent.getStringExtra("SUBSCRIPTION_ID")

        val passedPrice = planPrice
        val finalPrice = if (passedPrice > 0) passedPrice.toInt().toString() else "10"

        val uniqueSuffix = System.currentTimeMillis()
        val planId = (intent.getStringExtra("PLAN_ID") ?: "test_item") + "_$uniqueSuffix"

        Log.d("ESewaPayment", "Starting Payment: Name=$planName, Price=$finalPrice, ID=$planId")

        // 2. Configure eSewa
        val eSewaConfiguration = EsewaConfiguration(
            clientId = TEST_CLIENT_ID,
            secretKey = TEST_SECRET_KEY,
            environment = EsewaConfiguration.ENVIRONMENT_TEST
        )

        // 3. Setup Payment
        val eSewaPayment = EsewaPayment(
            finalPrice,
            planName,
            planId,
            "https://google.com"
        )

        // 4. Launch eSewa SDK Activity
        try {
            val intent = Intent(this, EsewaPaymentActivity::class.java)
            intent.putExtra(EsewaConfiguration.ESEWA_CONFIGURATION, eSewaConfiguration)
            intent.putExtra(EsewaPayment.ESEWA_PAYMENT, eSewaPayment)
            startActivityForResult(intent, REQUEST_CODE_PAYMENT)
        } catch (e: Exception) {
            Log.e("ESewaPayment", "Failed to launch SDK", e)
            showCustomToast("Failed to launch eSewa: ${e.message}")
            finish()
        }
    }

    @Composable
    fun CustomToastUI() {
        if (showToastState) {
            CustomToastCompose(
                message = toastMessageState,
                showToast = showToastState,
                onDismiss = { showToastState = false },
                durationMillis = 3000L
            )
        }
    }

    // Helper to show toast from anywhere in the class
    private fun showCustomToast(message: String) {
        toastMessageState = message
        showToastState = true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PAYMENT) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val message = data?.getStringExtra(EsewaPayment.EXTRA_RESULT_MESSAGE) ?: ""
                    Log.i("ESewaPayment", "SDK Response: $message")

                    val refId = extractRefId(message)
                    if (refId.isNotEmpty()) {
                        showCustomToast("Verifying Payment...")
                        verifyPaymentWithEsewa(refId, message)
                    } else {
                        showCustomToast("Payment Succeeded - Creating Subscription...")
                        createSubscriptionInFirebase(message)
                    }
                }

                Activity.RESULT_CANCELED -> {
                    showCustomToast("Canceled By User")
                    // Delay finishing slightly so user sees the toast
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }, 1500)
                }

                EsewaPayment.RESULT_EXTRAS_INVALID -> {
                    val message =
                        data?.getStringExtra(EsewaPayment.EXTRA_RESULT_MESSAGE) ?: "Invalid Extras"
                    Log.e("ESewaPayment", "Error: $message")
                    showCustomToast("eSewa Error: $message")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        setResult(Activity.RESULT_FIRST_USER)
                        finish()
                    }, 1500)
                }
            }
        }
    }

    private fun verifyPaymentWithEsewa(refId: String, originalMessage: String) {
        val url = "https://rc.esewa.com.np/mobile/transaction?txnRefId=$refId"
        Log.d("ESewaPayment", "Verifying at: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("merchantId", TEST_CLIENT_ID)
            .addHeader("merchantSecret", TEST_SECRET_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ESewaPayment", "Verification Network Error: ${e.message}")
                runOnUiThread {
                    showCustomToast("Network Error - Creating Subscription...")
                    createSubscriptionInFirebase(originalMessage)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Log.i("ESewaPayment", "Server Response: $responseBody")

                runOnUiThread {
                    if (response.isSuccessful && (responseBody.contains("COMPLETE") || responseBody.contains(
                            "Success"
                        ))
                    ) {
                        showCustomToast("Payment Verified - Creating Subscription...")
                        createSubscriptionInFirebase(originalMessage)
                    } else {
                        Log.e(
                            "ESewaPayment",
                            "Verification Failed. Code: ${response.code}, Body: $responseBody"
                        )
                        showCustomToast("Creating Subscription...")
                        createSubscriptionInFirebase(originalMessage)
                    }
                }
            }
        })
    }

    /**
     * ✅ CREATE OR EXTEND SUBSCRIPTION IN FIREBASE
     */
    private fun createSubscriptionInFirebase(paymentMessage: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            showCustomToast("Error: User not logged in")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                setResult(Activity.RESULT_FIRST_USER)
                finish()
            }, 1500)
            return
        }

        // ✅ Check if this is extension or new subscription
        if (isExtension && !existingSubscriptionId.isNullOrEmpty()) {
            extendExistingSubscription(existingSubscriptionId!!, paymentMessage)
        } else {
            createNewSubscription(userId, paymentMessage)
        }
    }

    /**
     * ✅ CREATE NEW SUBSCRIPTION
     */
    private fun createNewSubscription(userId: String, paymentMessage: String) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, planDuration)
        val endDate = calendar.time

        val transactionId =
            extractRefId(paymentMessage).ifEmpty { "esewa_${System.currentTimeMillis()}" }

        val subscriptionData = hashMapOf(
            "userId" to userId,
            "planId" to planId,
            "planName" to planName,
            "status" to "active",
            "startDate" to Timestamp(Calendar.getInstance().time),
            "endDate" to Timestamp(endDate),
            "paymentMethod" to "Esewa",
            "transactionId" to transactionId,
            "price" to planPrice.toInt(),
            "duration" to planDuration,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("subscriptions")
            .add(subscriptionData)
            .addOnSuccessListener { documentReference ->
                val subscriptionId = documentReference.id
                Log.d("ESewaPayment", "Subscription created: $subscriptionId")

                // ✅ Write to payments collection with method: "Esewa"
                recordPayment(
                    userId = userId,
                    subscriptionId = subscriptionId,
                    transactionId = transactionId,
                    isExtension = false
                )

                updateUserDocument(userId)
                updateSubscriptionStats()
                logSubscriptionActivity(userId, planName)

                showCustomToast("Subscription Activated Successfully!")
                finishWithSuccess(paymentMessage, subscriptionCreated = true)
            }
            .addOnFailureListener { e ->
                Log.e("ESewaPayment", "Failed to create subscription", e)
                showCustomToast("Error: ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    setResult(Activity.RESULT_FIRST_USER)
                    finish()
                }, 1500)
            }
    }

    /**
     * ✅ EXTEND EXISTING SUBSCRIPTION - Adds days to current end date
     */
    private fun extendExistingSubscription(subscriptionId: String, paymentMessage: String) {
        db.collection("subscriptions").document(subscriptionId).get()
            .addOnSuccessListener { document ->
                // ✅ Get EXISTING end date (not current date)
                val currentEndDate = document.getTimestamp("endDate")?.toDate() ?: Date()

                // ✅ Add days to EXISTING end date
                val calendar = Calendar.getInstance()
                calendar.time = currentEndDate
                calendar.add(Calendar.DAY_OF_YEAR, planDuration)
                val newEndDate = calendar.time

                val transactionId =
                    extractRefId(paymentMessage).ifEmpty { "esewa_ext_${System.currentTimeMillis()}" }

                Log.d(
                    "ESewaPayment",
                    "Extending subscription: Old end=$currentEndDate, New end=$newEndDate, Added=$planDuration days"
                )

                // Update subscription with new end date + payment info
                db.collection("subscriptions").document(subscriptionId)
                    .update(
                        mapOf(
                            "endDate" to Timestamp(newEndDate),
                            "status" to "active",
                            "paymentMethod" to "Esewa",
                            "transactionId" to transactionId,
                            "lastUpdated" to FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener {
                        Log.d("ESewaPayment", "Subscription extended successfully")

                        // Log extension activity
                        val userId = document.getString("userId")
                        if (userId != null) {
                            // ✅ Write to payments collection with method: "Esewa"
                            recordPayment(
                                userId = userId,
                                subscriptionId = subscriptionId,
                                transactionId = transactionId,
                                isExtension = true
                            )
                            logExtensionActivity(userId, planName, planDuration)
                        }

                        // ✅ Update totalRevenue in Dashboard_stats for extensions
                        updateRevenueForExtension()

                        showCustomToast("Subscription Extended Successfully!")
                        finishWithSuccess(paymentMessage, subscriptionExtended = true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ESewaPayment", "Failed to extend subscription", e)
                        showCustomToast("Error: ${e.message}")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            setResult(Activity.RESULT_FIRST_USER)
                            finish()
                        }, 1500)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ESewaPayment", "Failed to fetch subscription", e)
                showCustomToast("Error: ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    setResult(Activity.RESULT_FIRST_USER)
                    finish()
                }, 1500)
            }
    }

    /**
     * ✅ Writes a document to the payments collection.
     * Field name "method" = "Esewa" as required by the payments collection schema.
     */
    private fun recordPayment(
        userId: String,
        subscriptionId: String,
        transactionId: String,
        isExtension: Boolean
    ) {
        val paymentData = hashMapOf(
            "userId" to userId,
            "subscriptionId" to subscriptionId,
            "planId" to planId,
            "planName" to planName,
            "amount" to planPrice.toInt(),
            "method" to "Esewa",
            "transactionId" to transactionId,
            "type" to if (isExtension) "extension" else "new",
            "status" to "success",
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("payments")
            .add(paymentData)
            .addOnSuccessListener { ref ->
                Log.d("ESewaPayment", "Payment recorded: ${ref.id} | method=Esewa")
            }
            .addOnFailureListener { e ->
                Log.e("ESewaPayment", "Failed to save payment record", e)
            }
    }

    /**
     * Update user document to mark they have a subscription
     */
    private fun updateUserDocument(userId: String) {
        db.collection("users").document(userId)
            .update(
                mapOf(
                    "hasActiveSubscription" to true,
                    "lastSubscriptionUpdate" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Log.d("ESewaPayment", "User document updated")
            }
            .addOnFailureListener { e ->
                Log.e("ESewaPayment", "Failed to update user", e)
            }
    }

    /**
     * Update Dashboard stats — increments both activeSubs and totalRevenue
     */
    private fun updateSubscriptionStats() {
        val statsRef = db.collection("Dashboard_stats").document("YbIkiRVdxGQqvza8K85i")
        statsRef.update(
            mapOf(
                "activeSubs" to FieldValue.increment(1),
                "totalRevenue" to FieldValue.increment(planPrice),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
        )
    }

    /**
     * Updates only totalRevenue for extensions.
     * Does NOT increment activeSubs — the subscription already exists.
     */
    private fun updateRevenueForExtension() {
        val statsRef = db.collection("Dashboard_stats").document("YbIkiRVdxGQqvza8K85i")
        statsRef.update(
            mapOf(
                "totalRevenue" to FieldValue.increment(planPrice),
                "lastUpdated" to FieldValue.serverTimestamp()
            )
        )
    }

    /**
     * Log activity (matches SubscriptionViewModel pattern)
     */
    private fun logSubscriptionActivity(userId: String, planName: String) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userName = doc.getString("fullName") ?: doc.getString("name") ?: "User"

                val activity = hashMapOf(
                    "title" to "$userName purchased $planName",
                    "type" to "sub",  // Matches SubscriptionViewModel's "sub" type
                    "timestamp" to Timestamp.now()
                )

                db.collection("activities").add(activity)
            }
    }

    /**
     * ✅ Log extension activity
     */
    private fun logExtensionActivity(userId: String, planName: String, days: Int) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val userName = doc.getString("fullName") ?: doc.getString("name") ?: "User"

                val activity = hashMapOf(
                    "title" to "$userName extended subscription with $planName (+$days days)",
                    "type" to "sub",
                    "timestamp" to Timestamp.now()
                )

                db.collection("activities").add(activity)
            }
    }

    private fun finishWithSuccess(
        message: String,
        subscriptionCreated: Boolean = false,
        subscriptionExtended: Boolean = false
    ) {
        // Small delay to let the user read the toast before closing
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val resultIntent = Intent()
            resultIntent.putExtra("PAYMENT_RESULT", message)
            resultIntent.putExtra("SUBSCRIPTION_CREATED", subscriptionCreated)
            resultIntent.putExtra("SUBSCRIPTION_EXTENDED", subscriptionExtended)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }, 2000)
    }

    private fun extractRefId(jsonString: String): String {
        return try {
            val gson = Gson()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(jsonString, mapType)
            val transactionDetails = data["transactionDetails"] as? Map<*, *>
            val refId = transactionDetails?.get("referenceId")?.toString()
            if (refId == null) {
                return data["refId"]?.toString() ?: ""
            }
            refId
        } catch (e: Exception) {
            Log.e("ESewaPaymentHandler", "Error parsing JSON", e)
            ""
        }
    }
}
