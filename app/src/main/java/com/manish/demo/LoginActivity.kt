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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private var tilEmail: TextInputLayout? = null
    private var etEmail: TextInputEditText? = null
    private var tilPassword: TextInputLayout? = null
    private var etPassword: TextInputEditText? = null
    private var btnLogin: MaterialButton? = null
    private var btnGoogle: MaterialButton? = null // New
    private var tvSignUp: TextView? = null
    private var progressBar: ProgressBar? = null
    private var tvForgotPassword: TextView? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient // New

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // --- GOOGLE SIGN IN CONFIGURATION ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Requires google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogle = findViewById(R.id.btnGoogleLogin) // New
        tvSignUp = findViewById(R.id.tvSignUp)
        progressBar = findViewById(R.id.loginProgressBar)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        // Check if user is logged in AND verified
        if (auth.currentUser != null) {
            if (auth.currentUser!!.isEmailVerified) {
                checkUserStatusAndRedirect(auth.currentUser!!.uid)
            } else {
                auth.signOut()
            }
        }

        btnLogin?.setOnClickListener { validateAndLogin() }
        btnGoogle?.setOnClickListener { signInWithGoogle() } // New
        tvSignUp?.setOnClickListener { showSignUpBottomSheet() }
        tvForgotPassword?.setOnClickListener { showForgotPasswordBottomSheet() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
    }

    // --- GOOGLE LOGIN LOGIC ---
    private fun showExitDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Exit MovieFlix?")
            .setMessage("Are you sure you want to close MovieFlix?")
            .setCancelable(false) // User must click a button
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Yes") { _, _ ->
                finishAffinity() // Closes all activities and exits the app
            }
            .show()
    }
    private fun signInWithGoogle() {
        // 1. Sign out from Google Client first to clear the cache
        googleSignInClient.signOut().addOnCompleteListener {
            // 2. Once cleared, launch the account picker
            val signInIntent = googleSignInClient.signInIntent
            googleLauncher.launch(signInIntent)
        }
    }

    private val googleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showCustomDialog(false, "Google Sign-In failed: ${e.message}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        setLoginLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid ?: ""
                    val email = user?.email ?: ""

                    // Check if this Google user already exists in Firestore
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                // Existing User: Go to check for Bans/ProfileCompletion
                                checkUserStatusAndRedirect(userId)
                            } else {
                                // New User: Create record (Google accounts are pre-verified)
                                initializeUserInFirestore(userId, email)
                            }
                        }
                        .addOnFailureListener {
                            setLoginLoading(false)
                            showCustomDialog(false, "Database error. Please try again.")
                        }
                } else {
                    setLoginLoading(false)
                    showCustomDialog(false, task.exception?.message ?: "Authentication Failed")
                }
            }
    }

    // --- EXISTING LOGIC (KEEPING YOUR RECENT UPDATES) ---

    private fun showForgotPasswordBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_forgot_password, null)
        bottomSheetDialog.setContentView(view)

        bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val btnClose = view.findViewById<ImageView>(R.id.btnCloseForgot)
        val btnSend = view.findViewById<MaterialButton>(R.id.btnSendResetLink)
        val forgotProgressBar = view.findViewById<ProgressBar>(R.id.forgotProgressBar)
        val tilEmailForgot = view.findViewById<TextInputLayout>(R.id.tilForgotEmail)
        val etEmailForgot = view.findViewById<TextInputEditText>(R.id.etForgotEmail)

        val blackColor = ColorStateList.valueOf(Color.BLACK)
        val cornerRadius = 15f
        tilEmailForgot.setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        tilEmailForgot.defaultHintTextColor = blackColor
        tilEmailForgot.hintTextColor = blackColor
        tilEmailForgot.setBoxStrokeColor(Color.BLACK)
        etEmailForgot.setTextColor(Color.BLACK)

        btnClose.setOnClickListener { bottomSheetDialog.dismiss() }

        btnSend.setOnClickListener {
            val email = etEmailForgot.text.toString().trim().lowercase()

            if (email.isEmpty()) {
                tilEmailForgot.error = "Required"
                return@setOnClickListener
            }

            forgotProgressBar.visibility = View.VISIBLE
            btnSend.visibility = View.INVISIBLE
            etEmailForgot.isEnabled = false
            tilEmailForgot.error = null

            db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && !task.result.isEmpty) {
                        auth.sendPasswordResetEmail(email)
                            .addOnCompleteListener { resetTask ->
                                forgotProgressBar.visibility = View.GONE
                                btnSend.visibility = View.VISIBLE
                                etEmailForgot.isEnabled = true
                                if (resetTask.isSuccessful) {
                                    bottomSheetDialog.dismiss()
                                    showCustomDialog(true, "Reset link sent to your email!")
                                } else {
                                    showCustomDialog(false, resetTask.exception?.message ?: "Error")
                                }
                            }
                    } else {
                        forgotProgressBar.visibility = View.GONE
                        btnSend.visibility = View.VISIBLE
                        etEmailForgot.isEnabled = true
                        tilEmailForgot.error = "No account found"
                        shakeView(tilEmailForgot)
                    }
                }
        }
        bottomSheetDialog.show()
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
                    } else {
                        initializeUserInFirestore(userId, auth.currentUser?.email ?: "")
                    }
                } else handleFailure(task.exception?.message ?: "Connection Error")
            }
    }

    private fun initializeUserInFirestore(userId: String, email: String) {
        setLoginLoading(true)
        val userMap = hashMapOf(
            "email" to email,
            "profileCompleted" to false,
            "role" to "USER",
            "isBanned" to false
        )

        db.collection("users").document(userId).set(userMap)
            .addOnCompleteListener { task ->
                setLoginLoading(false)
                if (task.isSuccessful) {
                    navigateToCompleteProfile()
                } else {
                    showCustomDialog(false, "Database Error: ${task.exception?.message}")
                }
            }
    }

    private fun setLoginLoading(isLoading: Boolean) {
        progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnLogin?.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        btnGoogle?.isEnabled = !isLoading // Disable Google too
        tilEmail?.isEnabled = !isLoading
        tilPassword?.isEnabled = !isLoading
    }

    private fun validateAndLogin() {
        val email = etEmail?.text.toString().trim()
        val password = etPassword?.text.toString().trim()

        tilEmail?.error = null
        tilPassword?.error = null

        if (email.isEmpty()) { tilEmail?.error = "Required"; return }
        if (password.isEmpty()) { tilPassword?.error = "Required"; return }

        setLoginLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        checkUserStatusAndRedirect(user.uid)
                    } else {
                        setLoginLoading(false)
                        auth.signOut()
                        showCustomDialog(false, "Please verify your email address first.")
                        shakeView(btnLogin!!)
                    }
                } else {
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

        val btnClose = view.findViewById<ImageView>(R.id.btnClose)
        val btnGetStarted = view.findViewById<MaterialButton>(R.id.btnGetStarted)
        val signUpProgressBar = view.findViewById<ProgressBar>(R.id.signUpProgressBar)
        val tilSheetEmail = view.findViewById<TextInputLayout>(R.id.tilSheetEmail)
        val etSheetEmail = view.findViewById<TextInputEditText>(R.id.etSheetEmail)
        val tilSheetPassword = view.findViewById<TextInputLayout>(R.id.tilSheetPassword)
        val etSheetPassword = view.findViewById<TextInputEditText>(R.id.etSheetPassword)
        val tilSheetConfirmPassword = view.findViewById<TextInputLayout>(R.id.tilSheetConfirmPassword)
        val etSheetConfirmPassword = view.findViewById<TextInputEditText>(R.id.etSheetConfirmPassword)

        val cornerRadius = 15f
        listOf(tilSheetEmail, tilSheetPassword, tilSheetConfirmPassword).forEach {
            it.setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
            it.defaultHintTextColor = ColorStateList.valueOf(Color.BLACK)
            it.hintTextColor = ColorStateList.valueOf(Color.BLACK)
            it.setBoxStrokeColor(Color.BLACK)
            it.setStartIconTintList(ColorStateList.valueOf(Color.BLACK))
            it.setEndIconTintList(ColorStateList.valueOf(Color.BLACK))
        }
        listOf(etSheetEmail, etSheetPassword, etSheetConfirmPassword).forEach {
            it.setTextColor(Color.BLACK)
        }

        fun setSignupLoading(isLoading: Boolean) {
            signUpProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnGetStarted.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
            etSheetEmail.isEnabled = !isLoading
            etSheetPassword.isEnabled = !isLoading
            etSheetConfirmPassword.isEnabled = !isLoading
        }

        btnClose.setOnClickListener { bottomSheetDialog.dismiss() }

        btnGetStarted.setOnClickListener {
            val email = etSheetEmail.text.toString().trim()
            val password = etSheetPassword.text.toString().trim()
            val confirm = etSheetConfirmPassword.text.toString().trim()
            val passRegex = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$".toRegex()

            if (email.isEmpty()) { tilSheetEmail.error = "Required"; return@setOnClickListener }
            if (!password.matches(passRegex)) { tilSheetPassword.error = "8+ chars with letters & numbers"; return@setOnClickListener }
            if (password != confirm) { tilSheetConfirmPassword.error = "No match"; return@setOnClickListener }

            setSignupLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { emailTask ->
                            setSignupLoading(false)
                            if (emailTask.isSuccessful) {
                                auth.signOut()
                                bottomSheetDialog.dismiss()
                                showCustomDialog(true, "Verification link sent to $email.")
                            }
                        }
                    } else {
                        setSignupLoading(false)
                        showCustomDialog(false, task.exception?.message ?: "Signup Failed")
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

    private fun showCustomDialog(isSuccess: Boolean, message: String, action: (() -> Unit)? = null) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_custom_message)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

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
            .apply { duration = 500; start() }
    }

    private fun navigateToMain() = startActivity(Intent(this, HomeActivity1::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })

    private fun navigateToAdmin() = startActivity(Intent(this, AdminHomeActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })

    private fun navigateToCompleteProfile() = startActivity(Intent(this, CompleteProfileActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
}