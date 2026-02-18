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

class LoginActivity : AppCompatActivity() {

    private var tilEmail: TextInputLayout? = null
    private var etEmail: TextInputEditText? = null
    private var tilPassword: TextInputLayout? = null
    private var etPassword: TextInputEditText? = null
    private var btnLogin: MaterialButton? = null
    private var tvSignUp: TextView? = null
    private var progressBar: ProgressBar? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvSignUp = findViewById(R.id.tvSignUp)
        progressBar = findViewById(R.id.loginProgressBar)

        if (auth.currentUser != null) checkUserStatusAndRedirect(auth.currentUser!!.uid)

        btnLogin?.setOnClickListener { validateAndLogin() }
        tvSignUp?.setOnClickListener { showSignUpBottomSheet() }
    }

    private fun checkUserStatusAndRedirect(userId: String) {
        setLoginLoading(true)
        db.collection("users").document(userId).get()
            .addOnCompleteListener { task ->
                setLoginLoading(false)
                if (task.isSuccessful) {
                    val doc = task.result
                    if (doc != null && doc.exists()) {
                        if (doc.getBoolean("isBanned") == true) {
                            auth.signOut()
                            showCustomDialog(false, "Access Denied: You are banned.")
                        } else {
                            val role = doc.getString("role") ?: "USER"
                            val isCompleted = doc.getBoolean("profileCompleted") ?: false
                            when {
                                !isCompleted -> navigateToCompleteProfile()
                                role.equals("admin", true) -> navigateToAdmin()
                                else -> navigateToMain()
                            }
                        }
                    } else navigateToCompleteProfile()
                } else handleFailure(task.exception?.message ?: "Connection Error")
            }
    }

    private fun setLoginLoading(isLoading: Boolean) {
        progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin?.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        tilEmail?.isEnabled = !isLoading
        tilPassword?.isEnabled = !isLoading
    }

    private fun validateAndLogin() {
        val email = etEmail?.text.toString().trim()
        val password = etPassword?.text.toString().trim()

        tilEmail?.error = null
        tilPassword?.error = null

        if (email.isEmpty()) {
            tilEmail?.error = "Required"; return
        }
        if (password.isEmpty()) {
            tilPassword?.error = "Required"; return
        }

        setLoginLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) checkUserStatusAndRedirect(auth.currentUser!!.uid)
                else {
                    setLoginLoading(false)
                    showCustomDialog(false, task.exception?.message ?: "Login Failed")
                    shakeView(btnLogin!!)
                }
            }
    }

    private fun showSignUpBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_signup, null)
        bottomSheetDialog.setContentView(view)

        bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        bottomSheetDialog.setOnShowListener {
            val d = it as BottomSheetDialog
            val sheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let { s ->
                val behavior = BottomSheetBehavior.from(s)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        // --- INITIALIZE VIEWS ---
        val btnClose = view.findViewById<ImageView>(R.id.btnClose)
        val btnGetStarted = view.findViewById<MaterialButton>(R.id.btnGetStarted)
        val signUpProgressBar = view.findViewById<ProgressBar>(R.id.signUpProgressBar)
        val tilSheetEmail = view.findViewById<TextInputLayout>(R.id.tilSheetEmail)
        val etSheetEmail = view.findViewById<TextInputEditText>(R.id.etSheetEmail)
        val tilSheetPassword = view.findViewById<TextInputLayout>(R.id.tilSheetPassword)
        val etSheetPassword = view.findViewById<TextInputEditText>(R.id.etSheetPassword)
        val tilSheetConfirmPassword =
            view.findViewById<TextInputLayout>(R.id.tilSheetConfirmPassword)
        val etSheetConfirmPassword =
            view.findViewById<TextInputEditText>(R.id.etSheetConfirmPassword)

        // --- APPLY ROUNDED CORNERS USING setBoxCornerRadii() ---
        val cornerRadius = 15f // 15dp corner radius

        // Apply to all three fields
        tilSheetEmail.setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        tilSheetPassword.setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        tilSheetConfirmPassword.setBoxCornerRadii(
            cornerRadius,
            cornerRadius,
            cornerRadius,
            cornerRadius
        )

        // --- STYLING ---
        val blackColor = ColorStateList.valueOf(Color.BLACK)
        listOf(tilSheetEmail, tilSheetPassword, tilSheetConfirmPassword).forEach {
            it.defaultHintTextColor = blackColor
            it.hintTextColor = blackColor
            it.setBoxStrokeColor(Color.BLACK)
            it.placeholderTextColor = blackColor
            it.setStartIconTintList(blackColor) // Make icons black for white background
            it.setEndIconTintList(blackColor)
        }
        listOf(etSheetEmail, etSheetPassword, etSheetConfirmPassword).forEach {
            it.setTextColor(
                Color.BLACK
            )
        }

        fun setSignupLoading(isLoading: Boolean) {
            signUpProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnGetStarted.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
            etSheetEmail.isEnabled = !isLoading
            etSheetPassword.isEnabled = !isLoading
            etSheetConfirmPassword.isEnabled = !isLoading
        }

        // --- CLICK LISTENERS ---
        btnClose.setOnClickListener { bottomSheetDialog.dismiss() }

        btnGetStarted.setOnClickListener {
            tilSheetEmail.error = null
            tilSheetPassword.error = null
            tilSheetConfirmPassword.error = null

            val email = etSheetEmail.text.toString().trim()
            val password = etSheetPassword.text.toString().trim()
            val confirm = etSheetConfirmPassword.text.toString().trim()

            // Regex: 8+ chars, at least one letter and one number
            val passRegex = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$".toRegex()

            // Top-Down Validation
            if (email.isEmpty()) {
                tilSheetEmail.error = "Required"
                etSheetEmail.requestFocus()
                return@setOnClickListener
            }
            if (!password.matches(passRegex)) {
                tilSheetPassword.error = "8+ chars with letters & numbers"
                etSheetPassword.requestFocus()
                return@setOnClickListener
            }
            if (password != confirm) {
                tilSheetConfirmPassword.error = "Passwords don't match"
                etSheetConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            setSignupLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser!!.uid
                        val userMap = hashMapOf(
                            "email" to email,
                            "profileCompleted" to false,
                            "role" to "USER",
                            "isBanned" to false
                        )

                        db.collection("users").document(userId).set(userMap)
                            .addOnCompleteListener { dbTask ->
                                setSignupLoading(false)
                                if (dbTask.isSuccessful) {
                                    auth.signOut()
                                    bottomSheetDialog.dismiss()
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        showCustomDialog(true, "Account Created Successfully!") {
                                            this.etEmail?.setText(email)
                                            this.etPassword?.requestFocus()
                                        }
                                    }, 400)
                                } else {
                                    showCustomDialog(
                                        false,
                                        "Database Error: ${dbTask.exception?.message}"
                                    )
                                }
                            }
                    } else {
                        setSignupLoading(false) // Stop loader immediately
                        val ex = task.exception
                        if (ex is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            tilSheetEmail.error = "Email already in use"
                            etSheetEmail.requestFocus()
                            shakeView(tilSheetEmail)
                        } else {
                            showCustomDialog(false, ex?.message ?: "Signup Failed")
                        }
                    }
                }
        }
        bottomSheetDialog.show()
    }

    private fun handleFailure(message: String) {
        progressBar?.visibility = View.GONE
        btnLogin?.visibility = View.VISIBLE
        showCustomDialog(false, message)
    }

    private fun showCustomDialog(
        isSuccess: Boolean,
        message: String,
        action: (() -> Unit)? = null
    ) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_custom_message)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
            val tvMsg = dialog.findViewById<TextView>(R.id.tvDialogMessage)
            val btnOk = dialog.findViewById<MaterialButton>(R.id.btnDialogOk)
            val ivIcon = dialog.findViewById<ImageView>(R.id.ivDialogIcon)

            tvMsg.text = message
            if (isSuccess) {
                tvTitle.text = "Success!"
                tvTitle.setTextColor(Color.parseColor("#4CAF50"))
                ivIcon.setImageResource(R.drawable.ic_success_check)
                btnOk.setBackgroundColor(Color.parseColor("#4CAF50"))
            } else {
                tvTitle.text = "Failed"
                tvTitle.setTextColor(Color.parseColor("#F44336"))
                ivIcon.setImageResource(R.drawable.ic_error_x)
                btnOk.setBackgroundColor(Color.parseColor("#F44336"))
            }

            btnOk.setOnClickListener {
                dialog.dismiss()
                action?.invoke()
            }
            dialog.show()
        }
    }

    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 0f)
            .apply {
                duration = 500
                start()
            }
    }

    private fun navigateToMain() = startActivity(Intent(this, HomeActivity1::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })

    private fun navigateToAdmin() =
        startActivity(Intent(this, AdminHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })

    private fun navigateToCompleteProfile() =
        startActivity(Intent(this, CompleteProfileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
}