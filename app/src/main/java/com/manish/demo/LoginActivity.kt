package com.manish.demo

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class LoginActivity : AppCompatActivity() {

    // UI Variables
    private var tilEmail: TextInputLayout? = null
    private var etEmail: TextInputEditText? = null
    private var tilPassword: TextInputLayout? = null
    private var etPassword: TextInputEditText? = null
    private var btnLogin: MaterialButton? = null
    private var tvSignUp: TextView? = null
    private var progressBar: ProgressBar? = null

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserStatusAndRedirect(currentUser.uid)
        }

        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)
        progressBar = findViewById(R.id.loginProgressBar)

        btnLogin?.setOnClickListener { validateAndLogin() }
        tvSignUp?.setOnClickListener { showSignUpBottomSheet() }
    }

    private fun checkUserStatusAndRedirect(userId: String) {
        progressBar?.visibility = View.VISIBLE
        btnLogin?.visibility = View.INVISIBLE

        // Force Source.SERVER so a banned user can't use cached data to sneak in
        db.collection("users").document(userId).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                progressBar?.visibility = View.GONE

                if (document.exists()) {
                    // --- BAN CHECK ---
                    val isBanned = document.getBoolean("isBanned") ?: false
                    if (isBanned) {
                        auth.signOut() // Clear the session
                        showCustomDialog(false, "Access Denied: Your account has been banned by the administrator.")
                        btnLogin?.visibility = View.VISIBLE // Bring back login button
                        return@addOnSuccessListener
                    }

                    val role = document.getString("role") ?: "USER"
                    val isProfileCompleted = document.getBoolean("profileCompleted") ?: false

                    if (isProfileCompleted) {
                        // Match "admin" or "ADMIN"
                        if (role.equals("admin", ignoreCase = true)) {
                            navigateToAdmin()
                        } else {
                            navigateToMain()
                        }
                    } else {
                        navigateToCompleteProfile()
                    }
                } else {
                    navigateToCompleteProfile()
                }
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                progressBar?.visibility = View.GONE
                btnLogin?.visibility = View.VISIBLE
                showCustomDialog(false, "Connection Error: ${e.message}")
            }
    }

    private fun showCustomDialog(isSuccess: Boolean, message: String, action: (() -> Unit)? = null) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread

            val dialog = Dialog(this@LoginActivity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_custom_message)

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            val ivIcon = dialog.findViewById<ImageView>(R.id.ivDialogIcon)
            val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
            val tvMessage = dialog.findViewById<TextView>(R.id.tvDialogMessage)
            val btnOk = dialog.findViewById<MaterialButton>(R.id.btnDialogOk)

            ivIcon.clearColorFilter()
            tvMessage.text = message

            if (isSuccess) {
                tvTitle.text = "Success!"
                tvTitle.setTextColor(Color.parseColor("#4CAF50"))
                ivIcon.setImageResource(R.drawable.ic_success_check)
                btnOk.text = "LOGIN"
                btnOk.setBackgroundColor(Color.parseColor("#4CAF50"))
            } else {
                tvTitle.text = "Failed"
                tvTitle.setTextColor(Color.parseColor("#F44336"))
                ivIcon.setImageResource(R.drawable.ic_error_x)
                btnOk.text = "TRY AGAIN"
                btnOk.setBackgroundColor(Color.parseColor("#F44336"))
            }

            btnOk.setOnClickListener {
                dialog.dismiss()
                action?.invoke()
            }

            dialog.show()
        }
    }

    private fun validateAndLogin() {
        val email = etEmail?.text.toString().trim()
        val password = etPassword?.text.toString().trim()
        var isValid = true

        tilEmail?.error = null
        tilPassword?.error = null

        if (email.isEmpty()) { tilEmail?.error = "Required"; isValid = false }
        if (password.isEmpty()) { tilPassword?.error = "Required"; isValid = false }

        if (isValid) {
            performLogin(email, password)
        } else {
            shakeView(btnLogin!!)
        }
    }

    private fun performLogin(email: String, pass: String) {
        btnLogin?.visibility = View.INVISIBLE
        progressBar?.visibility = View.VISIBLE
        tilEmail?.isEnabled = false
        tilPassword?.isEnabled = false

        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid
                if (userId != null) {
                    checkUserStatusAndRedirect(userId)
                }
            }
            .addOnFailureListener { e ->
                progressBar?.visibility = View.GONE
                btnLogin?.visibility = View.VISIBLE
                tilEmail?.isEnabled = true
                tilPassword?.isEnabled = true

                showCustomDialog(false, e.message ?: "Authentication failed.")
                shakeView(btnLogin!!)
            }
    }

    private fun showSignUpBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_signup, null)
        bottomSheetDialog.setContentView(view)

        bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        bottomSheetDialog.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val layoutParams = sheet.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.layoutParams = layoutParams
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        val btnClose = view.findViewById<ImageView>(R.id.btnClose)
        val btnGetStarted = view.findViewById<MaterialButton>(R.id.btnGetStarted)
        val signUpProgressBar = view.findViewById<ProgressBar>(R.id.signUpProgressBar)

        val etSheetEmail = view.findViewById<TextInputEditText>(R.id.etSheetEmail)
        val tilSheetEmail = view.findViewById<TextInputLayout>(R.id.tilSheetEmail)
        val etSheetPassword = view.findViewById<TextInputEditText>(R.id.etSheetPassword)
        val tilSheetPassword = view.findViewById<TextInputLayout>(R.id.tilSheetPassword)
        val etSheetConfirmPassword = view.findViewById<TextInputEditText>(R.id.etSheetConfirmPassword)
        val tilSheetConfirmPassword = view.findViewById<TextInputLayout>(R.id.tilSheetConfirmPassword)

        val blackColor = ColorStateList.valueOf(Color.BLACK)
        tilSheetEmail.defaultHintTextColor = blackColor
        tilSheetPassword.defaultHintTextColor = blackColor
        tilSheetConfirmPassword.defaultHintTextColor = blackColor
        etSheetEmail.setTextColor(Color.BLACK)
        etSheetPassword.setTextColor(Color.BLACK)
        etSheetConfirmPassword.setTextColor(Color.BLACK)
        tilSheetPassword.setEndIconTintList(blackColor)
        tilSheetConfirmPassword.setEndIconTintList(blackColor)

        btnClose.setOnClickListener { bottomSheetDialog.dismiss() }

        btnGetStarted.setOnClickListener {
            tilSheetEmail.error = null; tilSheetPassword.error = null; tilSheetConfirmPassword.error = null
            val email = etSheetEmail.text.toString().trim()
            val password = etSheetPassword.text.toString().trim()
            val confirmPassword = etSheetConfirmPassword.text.toString().trim()
            var isValid = true

            if (email.isEmpty()) { tilSheetEmail.error = "Required"; isValid = false }
            if (password.length < 8) { tilSheetPassword.error = "Min 8 chars"; isValid = false }
            if (password != confirmPassword) { tilSheetConfirmPassword.error = "Mismatch"; isValid = false }

            if (isValid) {
                btnGetStarted.visibility = View.INVISIBLE
                signUpProgressBar.visibility = View.VISIBLE
                etSheetEmail.isEnabled = false
                etSheetPassword.isEnabled = false
                etSheetConfirmPassword.isEnabled = false

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val userId = result.user?.uid

                        // --- UPDATED USER MAP WITH isBanned FIELD ---
                        val userMap = hashMapOf(
                            "email" to email,
                            "profileCompleted" to false,
                            "role" to "USER",
                            "isBanned" to false // New default value
                        )

                        if (userId != null) {
                            db.collection("users").document(userId).set(userMap)
                                .addOnSuccessListener {
                                    auth.signOut()
                                    bottomSheetDialog.dismiss()

                                    Handler(Looper.getMainLooper()).postDelayed({
                                        showCustomDialog(true, "Account created successfully!") {
                                            etEmail?.setText(email)
                                            etPassword?.requestFocus()
                                        }
                                    }, 300)
                                }
                                .addOnFailureListener { e ->
                                    signUpProgressBar.visibility = View.GONE
                                    btnGetStarted.visibility = View.VISIBLE
                                    etSheetEmail.isEnabled = true
                                    etSheetPassword.isEnabled = true
                                    etSheetConfirmPassword.isEnabled = true
                                    showCustomDialog(false, "DB Error: ${e.message}")
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        signUpProgressBar.visibility = View.GONE
                        btnGetStarted.visibility = View.VISIBLE
                        etSheetEmail.isEnabled = true
                        etSheetPassword.isEnabled = true
                        etSheetConfirmPassword.isEnabled = true
                        showCustomDialog(false, e.message ?: "Registration Failed")
                    }
            }
        }
        bottomSheetDialog.show()
    }

    private fun shakeView(view: View) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 500
        shake.start()
    }

    private fun navigateToMain() {
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

    private fun navigateToCompleteProfile() {
        val intent = Intent(this, CompleteProfileActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}