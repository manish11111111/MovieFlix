package com.manish.demo

import android.Manifest
import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.*
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
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var ivProfile: ImageView
    private lateinit var etFullName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etDob: TextInputEditText
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilDob: TextInputLayout
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var imageUri: Uri? = null
    private var isPhoneAvailable = true
    private val handler = Handler(Looper.getMainLooper())
    private var tempCameraUri: Uri? = null

    // 1. Image Result Launchers
    private val pickGalleryImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempCameraUri?.let { processSelectedImage(it) }
    }

    // 2. Permission Launchers
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) launchCamera()
        else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) showSettingsDialog("Camera")
            else showCustomToast("Camera permission denied")
        }
    }

    private val galleryPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (isGranted) pickGalleryImage.launch("image/*")
        else {
            if (!shouldShowRequestPermissionRationale(perm)) showSettingsDialog("Gallery/Storage")
            else showCustomToast("Storage permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        ivProfile = findViewById(R.id.ivProfile)
        etFullName = findViewById(R.id.etFullName)
        etPhone = findViewById(R.id.etPhone)
        etDob = findViewById(R.id.etDob)
        tilFullName = findViewById(R.id.tilFullName)
        tilPhone = findViewById(R.id.tilPhone)
        tilDob = findViewById(R.id.tilDob)
        btnSave = findViewById(R.id.btnSaveProfile)
        progressBar = findViewById(R.id.profileProgressBar)

        // Tapping photo shows source selection
        findViewById<View>(R.id.profileSection).setOnClickListener { showSourceSelectionDialog() }

        etDob.setOnClickListener { showDatePicker() }
        btnSave.setOnClickListener { validateAndSave() }
        findViewById<View>(R.id.btnBack).setOnClickListener { showLogoutConfirmationDialog() }

        etPhone.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) checkPhoneUniqueness() }
    }

    private fun showSourceSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Profile Photo")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> handleCameraChoice()
                    1 -> handleGalleryChoice()
                    else -> dialog.dismiss()
                }
            }.show()
    }

    private fun handleCameraChoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun handleGalleryChoice() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            pickGalleryImage.launch("image/*")
        } else {
            galleryPermissionLauncher.launch(perm)
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = File(externalCacheDir, "temp_profile.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
            tempCameraUri = uri
            takePhoto.launch(uri)
        } catch (e: Exception) {
            showCustomToast("Error opening camera")
        }
    }

    private fun processSelectedImage(uri: Uri) {
        imageUri = uri
        ivProfile.setImageURI(uri)
        ivProfile.setPadding(0, 0, 0, 0)
        ivProfile.imageTintList = null
    }

    private fun checkPhoneUniqueness() {
        val phone = etPhone.text.toString().trim()
        if (phone.length != 10) return
        db.collection("users").whereEqualTo("phone", phone).get().addOnSuccessListener { docs ->
            val currentUid = auth.currentUser?.uid
            var taken = false
            for (doc in docs) { if (doc.id != currentUid) taken = true }
            if (taken) { tilPhone.error = "Number already in use"; isPhoneAvailable = false }
            else { tilPhone.error = null; isPhoneAvailable = true }
        }
    }

    private fun validateAndSave() {
        val name = etFullName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val dob = etDob.text.toString().trim()
        val age = getAge(dob)

        // CLEAR ERRORS: Just set to null.
        // Do NOT set isErrorEnabled = false here, it hides the space needed for the text.
        tilFullName.error = null
        tilPhone.error = null
        tilDob.error = null

        // Validations
        if (name.isEmpty()) {
            showCustomToast("Please enter your full name")
            tilFullName.error = "Name is required" // Setting error automatically shows it
            shakeView(tilFullName)
            return
        }
        if (phone.length != 10) {
            showCustomToast("Phone number must be 10 digits")
            tilPhone.error = "Must be 10 digits"
            shakeView(tilPhone)
            return
        }
        if (dob.isEmpty()) {
            showCustomToast("Please select your date of birth")
            tilDob.error = "Required"
            shakeView(tilDob)
            return
        }
        if (age < 18 || age > 90) {
            showCustomToast("Age must be between 18 and 90 years")
            tilDob.error = "Age must be 18-90"
            shakeView(tilDob)
            return
        }
        if (imageUri == null) {
            showCustomToast("Please upload a profile photo")
            shakeView(ivProfile)
            return
        }

        // 1. SWAP BUTTON FOR LOADER
        btnSave.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        // 2. FINAL PHONE UNIQUENESS CHECK
        db.collection("users").whereEqualTo("phone", phone).get()
            .addOnSuccessListener { docs ->
                val currentUid = auth.currentUser?.uid
                var isTaken = false
                for (doc in docs) {
                    if (doc.id != currentUid) {
                        isTaken = true
                        break
                    }
                }

                if (isTaken) {
                    btnSave.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE

                    showCustomToast("This phone number is already in use")
                    tilPhone.error = "Already in use"
                    shakeView(tilPhone)
                } else {
                    saveUserData(name, phone, dob)
                }
            }
            .addOnFailureListener { e ->
                btnSave.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                showCustomToast("Connection Error: ${e.localizedMessage}")
            }
    }

    private fun saveUserData(name: String, phone: String, dob: String) {
        val uri = imageUri ?: return

        Thread {
            try {
                val base64 = uriToBase64(uri)
                runOnUiThread {
                    if (base64 != null) {
                        saveToFirestore(name, phone, dob, base64)
                    } else {
                        btnSave.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        showCustomToast("Failed to process image")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnSave.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    showCustomToast("Error: ${e.localizedMessage}")
                }
            }
        }.start()
    }

    private fun saveToFirestore(name: String, phone: String, dob: String, img: String) {
        val uid = auth.currentUser?.uid ?: return
        val currentEmail = auth.currentUser?.email ?: ""  //
        // Explicitly setting role to USER
        val userData = hashMapOf(
            "fullName" to name,
            "email" to currentEmail,
            "phone" to phone,
            "dob" to dob,
            "profileImage" to img,
            "profileCompleted" to true,
            "role" to "USER",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid).set(userData, SetOptions.merge())
            .addOnSuccessListener {
                showCustomToast("Profile Saved Successfully!")
                handler.postDelayed({
                    val intent = Intent(this, HomeActivity1::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }, 1500)
            }
            .addOnFailureListener { e ->
                btnSave.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                showCustomToast("Database Error: ${e.localizedMessage}")
            }
    }

    private fun getAge(dob: String): Int {
        return try {
            val parts = dob.split("/")
            val dobCal = Calendar.getInstance().apply { set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt()) }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) age--
            age
        } catch (e: Exception) { -1 }
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val stream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(original, 500, (500 * (original.height.toFloat() / original.width)).toInt(), true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) { null }
    }

    private fun showSettingsDialog(type: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("$type Permission Required")
            .setMessage("You have permanently denied $type access. Please enable it in settings.")
            .setPositiveButton("SETTINGS") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun showDatePicker() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d -> tilDob.error = null; etDob.setText("$d/${m + 1}/$y") },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showLogoutConfirmationDialog() {
        val d = Dialog(this); d.setContentView(R.layout.dialog_logout_confirmation)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.findViewById<View>(R.id.btnCancel).setOnClickListener { d.dismiss() }
        d.findViewById<View>(R.id.btnLogout).setOnClickListener {
            auth.signOut(); startActivity(Intent(this, LoginActivity::class.java)); finish()
        }
        d.show()
    }

    private fun showCustomToast(msg: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.custom_toast_view, null)
        view.findViewById<TextView>(R.id.toastText).text = msg
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        handler.postDelayed({ if(dialog.isShowing) dialog.dismiss() }, 2000)
    }

    private fun shakeView(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, 20f, -20f, 20f, -20f, 0f).setDuration(400).start()
    }
}