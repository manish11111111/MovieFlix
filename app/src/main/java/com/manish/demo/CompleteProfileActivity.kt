package com.manish.demo

import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.manish.demo.R
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

class CompleteProfileActivity : AppCompatActivity() {

    // UI
    private lateinit var ivProfile: ImageView
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etDob: TextInputEditText
    private lateinit var tilDob: TextInputLayout
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Variables
    private var imageUri: Uri? = null
    private var isCheckingPhone = false
    private var isPhoneAvailable = true
    private val phoneNumbersInUse = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    // Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pickImage.launch("image/*")
            } else {
                showCustomToast("Permission Denied. Cannot access gallery.")
            }
        }

    // Image Picker
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            ivProfile.setImageURI(uri)
            ivProfile.setPadding(0,0,0,0)
            ivProfile.imageTintList = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_profile)

        // Init Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Init Views
        ivProfile = findViewById(R.id.ivProfile)
        etFullName = findViewById(R.id.etFullName)
        etPhone = findViewById(R.id.etPhone)
        etDob = findViewById(R.id.etDob)
        tilDob = findViewById(R.id.tilDob)
        btnSave = findViewById(R.id.btnSaveProfile)
        progressBar = findViewById(R.id.profileProgressBar)

        // Load existing phone numbers to check uniqueness
        loadExistingPhoneNumbers()

        // 1. Image Click
        findViewById<View>(R.id.cvProfileImage).setOnClickListener {
            checkPermissionAndPickImage()
        }

        // 2. Date Picker
        etDob.setOnClickListener {
            showDatePicker()
        }

        // 3. Save Button
        btnSave.setOnClickListener {
            validateAndSave()
        }

        // 4. Phone number uniqueness check
        etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkPhoneUniqueness()
            }
        }

        // Also check on text change
        etPhone.setOnEditorActionListener { _, _, _ ->
            checkPhoneUniqueness()
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val date = "$selectedDay/${selectedMonth + 1}/$selectedYear"
            etDob.setText(date)
            tilDob.error = null
        }, year, month, day)

        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun loadExistingPhoneNumbers() {
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                phoneNumbersInUse.clear()
                for (document in documents) {
                    val phone = document.getString("phone")
                    if (!phone.isNullOrEmpty()) {
                        phoneNumbersInUse.add(phone)
                    }
                }
                Log.d("CompleteProfile", "Loaded ${phoneNumbersInUse.size} existing phone numbers")
            }
            .addOnFailureListener { e ->
                Log.e("CompleteProfile", "Failed to load existing phone numbers", e)
            }
    }

    private fun checkPhoneUniqueness() {
        val phone = etPhone.text.toString().trim()
        if (phone.length != 10) return

        if (isCheckingPhone) return
        isCheckingPhone = true

        // Check in local cache first
        if (phoneNumbersInUse.contains(phone)) {
            etPhone.error = "Phone number already in use"
            isPhoneAvailable = false
            isCheckingPhone = false
            return
        }

        // Check in Firestore
        db.collection("users")
            .whereEqualTo("phone", phone)
            .get()
            .addOnSuccessListener { documents ->
                isCheckingPhone = false
                if (documents.isEmpty()) {
                    etPhone.error = null
                    isPhoneAvailable = true
                } else {
                    // Don't error if it's the current user's phone
                    val userId = auth.currentUser?.uid
                    var isCurrentUser = false
                    for (document in documents) {
                        if (document.id == userId) {
                            isCurrentUser = true
                            break
                        }
                    }

                    if (!isCurrentUser) {
                        etPhone.error = "Phone number already in use"
                        isPhoneAvailable = false
                        phoneNumbersInUse.add(phone) // Add to cache
                    } else {
                        etPhone.error = null
                        isPhoneAvailable = true
                    }
                }
            }
            .addOnFailureListener { e ->
                isCheckingPhone = false
                Log.e("CompleteProfile", "Error checking phone number", e)
            }
    }

    private fun validateAndSave() {
        val name = etFullName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val dobString = etDob.text.toString().trim()
        var isError = false

        etFullName.error = null
        etPhone.error = null
        tilDob.error = null

        if (name.isEmpty()) {
            etFullName.error = "Required"
            isError = true
        }

        if (phone.length != 10) {
            etPhone.error = "Must be 10 digits"
            isError = true
        } else if (!isPhoneAvailable && !isCheckingPhone) {
            etPhone.error = "Phone number already in use"
            isError = true
        }

        if (dobString.isEmpty()) {
            tilDob.error = "Required"
            isError = true
        } else {
            try {
                val parts = dobString.split("/")
                val day = parts[0].toInt()
                val month = parts[1].toInt() - 1
                val year = parts[2].toInt()

                val dobCalendar = Calendar.getInstance()
                dobCalendar.set(year, month, day)
                val today = Calendar.getInstance()

                var age = today.get(Calendar.YEAR) - dobCalendar.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < dobCalendar.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }

                if (age < 18) {
                    tilDob.error = "You must be at least 18 years old"
                    isError = true
                }
            } catch (e: Exception) {
                tilDob.error = "Invalid date format"
                isError = true
            }
        }

        if (imageUri == null) {
            showCustomToast("Please select a profile photo")
            isError = true
        }

        if (isError) {
            shakeView(btnSave)
            return
        }

        // Final phone check before saving
        if (!isPhoneAvailable) {
            showCustomToast("Phone number is already in use")
            return
        }

        // Hide button and show loader
        btnSave.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        // Disable other UI elements
        ivProfile.isEnabled = false
        etFullName.isEnabled = false
        etPhone.isEnabled = false
        etDob.isEnabled = false

        // Show initial toast
        showCustomToast("Saving profile...")

        processImageAndSaveData(name, phone, dobString)
    }

    private fun shakeView(view: View) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 500
        shake.start()
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Log.e("CompleteProfile", "Failed to decode bitmap from URI")
                return null
            }

            // Resize bitmap if too large
            val maxSize = 1024
            var width = bitmap.width
            var height = bitmap.height
            val bitmapRatio = width.toFloat() / height.toFloat()

            val resizedBitmap = if (width > maxSize || height > maxSize) {
                if (bitmapRatio > 1) {
                    width = maxSize
                    height = (width / bitmapRatio).toInt()
                } else {
                    height = maxSize
                    width = (height * bitmapRatio).toInt()
                }
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }

            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)

            // Check if base64 string is reasonable size
            if (base64String.length > 1000000) { // Approx 1MB
                Log.e("CompleteProfile", "Base64 string too large: ${base64String.length} chars")
                return null
            }

            base64String
        } catch (e: Exception) {
            Log.e("CompleteProfile", "Error converting URI to Base64", e)
            null
        }
    }

    private fun processImageAndSaveData(name: String, phone: String, dob: String) {
        Thread {
            val base64Image = if (imageUri != null) {
                uriToBase64(imageUri!!)
            } else {
                null
            }

            runOnUiThread {
                if (base64Image != null) {
                    saveToFirestore(name, phone, dob, base64Image)
                } else {
                    // Re-enable UI and show error
                    progressBar.visibility = View.GONE
                    btnSave.visibility = View.VISIBLE
                    ivProfile.isEnabled = true
                    etFullName.isEnabled = true
                    etPhone.isEnabled = true
                    etDob.isEnabled = true
                    showCustomToast("Failed to process image. Please try another image.")
                }
            }
        }.start()
    }


    private fun saveToFirestore(name: String, phone: String, dob: String, imageString: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            // Show button again, hide progress bar
            progressBar.visibility = View.GONE
            btnSave.visibility = View.VISIBLE
            ivProfile.isEnabled = true
            etFullName.isEnabled = true
            etPhone.isEnabled = true
            etDob.isEnabled = true
            showCustomToast("User not logged in")
            return
        }

        // FINAL CHECK: Verify phone number is still available before saving
        db.collection("users")
            .whereEqualTo("phone", phone)
            .get()
            .addOnSuccessListener { documents ->
                // Check if phone is used by someone else
                var phoneAlreadyUsed = false
                for (document in documents) {
                    if (document.id != userId) { // Different user using this phone
                        phoneAlreadyUsed = true
                        break
                    }
                }
                if (phoneAlreadyUsed) {
                    // Show button again, hide progress bar
                    progressBar.visibility = View.GONE
                    btnSave.visibility = View.VISIBLE
                    ivProfile.isEnabled = true
                    etFullName.isEnabled = true
                    etPhone.isEnabled = true
                    etDob.isEnabled = true
                    showCustomToast("Phone number is already in use by another user")
                    return@addOnSuccessListener
                }

                // Phone is available, proceed with saving
                proceedWithSave(userId, name, phone, dob, imageString)
            }
            .addOnFailureListener { e ->
                // Show button again, hide progress bar
                progressBar.visibility = View.GONE
                btnSave.visibility = View.VISIBLE
                ivProfile.isEnabled = true
                etFullName.isEnabled = true
                etPhone.isEnabled = true
                etDob.isEnabled = true
                showCustomToast("Error checking phone number availability")
                Log.e("CompleteProfile", "Error checking phone availability", e)
            }
    }
    private fun proceedWithSave(userId: String, name: String, phone: String, dob: String, imageString: String) {
        db.collection("users").document(userId).get(Source.SERVER)
            .addOnSuccessListener { document ->
                val currentEmail = auth.currentUser?.email ?: ""
                val currentRole = document?.getString("role") ?: "USER"

                // Create user data
                val userData = hashMapOf<String, Any>(
                    "fullName" to name,
                    "phone" to phone,
                    "dob" to dob,
                    "profileImage" to imageString,
                    "profileCompleted" to true,
                    "email" to currentEmail,
                    "role" to currentRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                // Add createdAt if it doesn't exist
                if (document?.get("createdAt") == null) {
                    userData["createdAt"] = FieldValue.serverTimestamp()
                }

                // Add passwordLastUpdated if it exists
                val lastUpdated = document?.getTimestamp("passwordLastUpdated")
                if (lastUpdated != null) {
                    userData["passwordLastUpdated"] = lastUpdated
                }

                db.collection("users").document(userId).set(userData, SetOptions.merge())
                    .addOnSuccessListener {
                        // Show success toast
                        showCustomToast("Profile Completed Successfully!")

                        // Add phone to cache
                        phoneNumbersInUse.add(phone)

                        // Small delay before navigation (2 seconds to show toast)
                        handler.postDelayed({
                            if (currentRole == "ADMIN") {
                                navigateToAdmin()
                            } else {
                                navigateToHome()
                            }
                        }, 2000)
                    }
                    .addOnFailureListener { e ->
                        // Re-enable UI on error
                        progressBar.visibility = View.GONE
                        btnSave.visibility = View.VISIBLE
                        ivProfile.isEnabled = true
                        etFullName.isEnabled = true
                        etPhone.isEnabled = true
                        etDob.isEnabled = true

                        // Log the full error
                        Log.e("CompleteProfile", "Firestore error", e)

                        // Show user-friendly message
                        val errorMessage = when {
                            e.message?.contains("permission", ignoreCase = true) == true ->
                                "Permission denied. Please check Firebase rules."
                            e.message?.contains("invalid", ignoreCase = true) == true ->
                                "Invalid data format. Please try again."
                            e.message?.contains("quota", ignoreCase = true) == true ->
                                "Storage quota exceeded. Please try again later."
                            e.message?.contains("already exists", ignoreCase = true) == true ->
                                "Phone number already exists. Please use a different number."
                            else -> "Error saving data: ${e.localizedMessage ?: "Unknown error"}"
                        }

                        showCustomToast(errorMessage)
                    }
            }
            .addOnFailureListener { e ->
                // Re-enable UI on error
                progressBar.visibility = View.GONE
                btnSave.visibility = View.VISIBLE
                ivProfile.isEnabled = true
                etFullName.isEnabled = true
                etPhone.isEnabled = true
                etDob.isEnabled = true
                showCustomToast("Failed to check user data: ${e.localizedMessage}")
                Log.e("CompleteProfile", "Error fetching user document", e)
            }
    }
    private fun showCustomToast(message: String) {
        runOnUiThread {
            // Inflate custom toast layout
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val toastView = inflater.inflate(R.layout.custom_toast_view, null)

            // Find views
            val toastCard = toastView.findViewById<MaterialCardView>(R.id.toastCard)
            val toastIcon = toastView.findViewById<ImageView>(R.id.toastIcon)
            val toastText = toastView.findViewById<TextView>(R.id.toastText)

            // Set message
            toastText.text = message

            // Set up the toast card appearance
            toastCard.radius = 24f // 24dp corner radius
            toastCard.elevation = 8f // 8dp elevation
            toastCard.strokeWidth = 2 // 2px stroke width
            toastCard.strokeColor = ContextCompat.getColor(this, android.R.color.white)

            // Create and show dialog
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(toastView)
                .create()

            // Make dialog background transparent
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setDimAmount(0f) // No background dim
            dialog.setCancelable(false)
            dialog.show()

            // Auto-dismiss after 2 seconds (same as your Compose version)
            handler.postDelayed({
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }, 2000)
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity1::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToAdmin() {
        val intent = Intent(this, AdminHomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}