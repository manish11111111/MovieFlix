package com.manish.demo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class GetStartedActivity : AppCompatActivity() {

    // Define Data
    private val onboardingData = listOf(
        OnboardingItem(
            R.drawable.poster_collage,
            "Unlimited Movies",
            "Stream customized Nepali and International content."
        ),
        OnboardingItem(
            R.drawable.poster_action,
            "Download & Watch Offline",
            "Save your favorites and watch without internet."
        ),
        OnboardingItem(
            R.drawable.poster_devices,
            "Watch Everywhere",
            "Stream on your phone and tablet."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_started)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val btnGetStarted = findViewById<Button>(R.id.btnGetStarted)

        // Setup Adapter
        viewPager.adapter = OnboardingAdapter(onboardingData)
        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        // 1. Initial State: Button Hidden, Dots Visible
        btnGetStarted.visibility = View.GONE
        btnGetStarted.alpha = 0f
        tabLayout.visibility = View.VISIBLE
        tabLayout.alpha = 1f

        // 2. Page Change Listener (The Swap Logic)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                if (position == onboardingData.size - 1) {
                    // --- LAST PAGE (Tab 3): Hide Dots, Show Button ---

                    // Fade out dots
                    tabLayout.animate().alpha(0f).setDuration(300).withEndAction {
                        tabLayout.visibility = View.INVISIBLE
                    }.start()

                    // Fade in button
                    btnGetStarted.visibility = View.VISIBLE
                    btnGetStarted.animate().alpha(1f).setDuration(300).start()

                } else {
                    // --- OTHER PAGES (Tab 1 & 2): Show Dots, Hide Button ---

                    // Fade in dots
                    tabLayout.visibility = View.VISIBLE
                    tabLayout.animate().alpha(1f).setDuration(300).start()

                    // Fade out button
                    btnGetStarted.animate().alpha(0f).setDuration(300).withEndAction {
                        btnGetStarted.visibility = View.GONE
                    }.start()
                }
            }
        })

        // 3. Button Click
        btnGetStarted.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

// --- REQUIRED DATA CLASS & ADAPTER ---
data class OnboardingItem(val imageRes: Int, val title: String, val desc: String)

class OnboardingAdapter(private val items: List<OnboardingItem>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivOnboarding)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val desc: TextView = view.findViewById(R.id.tvDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val item = items[position]
        holder.image.setImageResource(item.imageRes)
        holder.title.text = item.title
        holder.desc.text = item.desc
    }

    override fun getItemCount(): Int = items.size
}