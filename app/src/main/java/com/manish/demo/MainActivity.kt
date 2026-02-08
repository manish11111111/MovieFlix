package com.manish.demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class MainActivity : AppCompatActivity() {

    private var logoContainer: LinearLayout? = null
    private var ivLogo: ImageView? = null
    private var tvAppName: TextView? = null
    private var tvTagline: TextView? = null
    private var progressBar: ProgressBar? = null

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Init Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Handle System Bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Initialize Views
        logoContainer = findViewById(R.id.logoContainer)
        ivLogo = findViewById(R.id.ivLogo)
        tvAppName = findViewById(R.id.tvAppName)
        tvTagline = findViewById(R.id.tvTagline)
        progressBar = findViewById(R.id.progressBar)

        // ============================================
        // 2. SETUP INITIAL STATE
        // ============================================

        // Hide Text
        tvAppName?.alpha = 0f

        // Make Logo BIG
        ivLogo?.scaleX = 1.5f
        ivLogo?.scaleY = 1.5f

        // Center the Logo visually:
        // We shift the container RIGHT by roughly half the width of the hidden text.
        // This ensures the "M" is dead center on the screen.
        logoContainer?.translationX = 180f

        // ============================================
        // 3. START THE "JUMP & SPIN" ANIMATION
        // ============================================

        // STEP A: Jump UP + Spin 180 degrees
        ivLogo?.animate()
            ?.translationY(-300f) // Jump Up high
            ?.rotation(180f)      // Half spin
            ?.setDuration(500)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction {

                // STEP B: Drop DOWN + Spin remaining 180 degrees
                ivLogo?.animate()
                    ?.translationY(0f)   // Back to center
                    ?.rotation(360f)     // Full spin completion
                    ?.setDuration(500)
                    ?.setInterpolator(AccelerateDecelerateInterpolator()) // Smooth landing
                    ?.withEndAction {

                        // STEP C: Shrink & Slide & Reveal Text
                        runTransitionAnimation()
                    }
                    ?.start()
            }
            ?.start()
    }

    private fun runTransitionAnimation() {
        // 1. Shrink Logo to normal size
        ivLogo?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(500)?.start()

        // 2. Slide the container to the Left (Back to natural position)
        logoContainer?.animate()
            ?.translationX(0f)
            ?.setDuration(600)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()

        // 3. Reveal the Name "MovieFlix"
        tvAppName?.animate()
            ?.alpha(1f)
            ?.setDuration(600)
            ?.setStartDelay(200) // Wait slightly for the slide to start
            ?.withEndAction {
                // Show Tagline & Loader
                tvTagline?.animate()?.alpha(1f)?.setDuration(500)?.start()
                progressBar?.visibility = View.VISIBLE

                // Navigate after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    checkUserStatusAndNavigate()
                }, 2000)
            }
            ?.start()
    }

    private fun checkUserStatusAndNavigate() {
        val sharedPreferences = getSharedPreferences("MovieFlixPrefs", MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            sharedPreferences.edit { putBoolean("isFirstRun", false) }
            navigateTo(GetStartedActivity::class.java)
        } else {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                navigateTo(LoginActivity::class.java)
            } else {
                // Force a server read to get the latest role
                db.collection("users").document(currentUser.uid).get(Source.SERVER)
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val profileCompleted = document.getBoolean("profileCompleted") ?: false
                            val role = document.getString("role") ?: "USER"

                            if (profileCompleted) {
                                if (role == "ADMIN") {
                                    navigateTo(AdminHomeActivity::class.java)
                                } else {
                                    navigateTo(HomeActivity1::class.java)
                                }
                            } else {
                                navigateTo(CompleteProfileActivity::class.java)
                            }
                        } else {
                             navigateTo(CompleteProfileActivity::class.java)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Connection Error. Please try again.", Toast.LENGTH_SHORT).show()
                        navigateTo(LoginActivity::class.java)
                    }
            }
        }
    }

    private fun <T> navigateTo(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}