package com.luminex.photoenhancer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.luminex.photoenhancer.R
import com.luminex.photoenhancer.database.AppDatabase
import com.luminex.photoenhancer.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)  // FIXED: Changed from getDatabase to getInstance

        setupAnimations()
        checkUserStatus()
    }

    private fun setupAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        binding.appLogo.startAnimation(fadeIn)
        binding.appName.startAnimation(slideUp)
        binding.tagline.startAnimation(slideUp)
    }

    private fun checkUserStatus() {
        lifecycleScope.launch {
            delay(2500) // Splash screen duration

            val currentUser = database.userDao().getCurrentUserSync()

            val intent = if (currentUser != null) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, AuthActivity::class.java)
            }

            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}