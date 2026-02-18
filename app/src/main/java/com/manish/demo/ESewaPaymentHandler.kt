package com.manish.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.f1soft.esewapaymentsdk.EsewaConfiguration
import com.f1soft.esewapaymentsdk.EsewaPayment
import com.f1soft.esewapaymentsdk.ui.screens.EsewaPaymentActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.manish.demo.ui.components.CustomToastCompose
import okhttp3.*
import java.io.IOException

class ESewaPaymentHandler : ComponentActivity() {

    private val REQUEST_CODE_PAYMENT = 102
    private val client = OkHttpClient()

    // Test Credentials
    private val TEST_CLIENT_ID = "JB0BBQ4aD0UqIThFJwAKBgAXEUkEGQUBBAwdOgABHD4DChwUAB0R"
    private val TEST_SECRET_KEY = "BhwIWQQADhIYSxILExMcAgFXFhcOBwAKBgAXEQ=="

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
        val planName = intent.getStringExtra("PLAN_NAME") ?: "Subscription"
        val passedPrice = intent.getDoubleExtra("PLAN_PRICE", 0.0)
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
                        showCustomToast("Payment Succeeded (No Ref ID)")
                        finishWithSuccess(message)
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
                    val message = data?.getStringExtra(EsewaPayment.EXTRA_RESULT_MESSAGE) ?: "Invalid Extras"
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
                    showCustomToast("Network Error - Allowing for Dev")
                    finishWithSuccess(originalMessage)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Log.i("ESewaPayment", "Server Response: $responseBody")

                runOnUiThread {
                    if (response.isSuccessful && (responseBody.contains("COMPLETE") || responseBody.contains("Success"))) {
                        showCustomToast("Payment Verified and Successful!")
                        finishWithSuccess(originalMessage)
                    } else {
                        Log.e("ESewaPayment", "Verification Failed. Code: ${response.code}, Body: $responseBody")
                        showCustomToast("Verification Error (Dev Allowed)")
                        finishWithSuccess(originalMessage)
                    }
                }
            }
        })
    }

    private fun finishWithSuccess(message: String) {
        // Small delay to let the user read the toast before closing
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val resultIntent = Intent()
            resultIntent.putExtra("PAYMENT_RESULT", message)
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
