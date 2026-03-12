package com.luminex.photoenhancer.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luminex.photoenhancer.R
import com.luminex.photoenhancer.databinding.ActivityMainBinding
import com.luminex.photoenhancer.fragments.DashboardFragment
import com.luminex.photoenhancer.fragments.GalleryFragment
import com.luminex.photoenhancer.fragments.HomeFragment
import com.luminex.photoenhancer.fragments.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupBottomNavigation()

        binding.profileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Load HomeFragment by default only if state is null (prevents overlapping on rotate)
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // Logic: Check if we are already on the selected fragment to avoid reloading
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)

            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> if (currentFragment is HomeFragment) return@setOnItemSelectedListener true else HomeFragment()
                R.id.nav_dashboard -> if (currentFragment is DashboardFragment) return@setOnItemSelectedListener true else DashboardFragment()
                R.id.nav_gallery -> if (currentFragment is GalleryFragment) return@setOnItemSelectedListener true else GalleryFragment()
                R.id.nav_profile -> if (currentFragment is ProfileFragment) return@setOnItemSelectedListener true else ProfileFragment()
                else -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out) // Added smooth transition
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = grantResults.any { it == PackageManager.PERMISSION_DENIED }

            if (denied) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permissions Required")
                    .setMessage("Luminex needs storage and camera access to enhance your photos. Please enable them in settings.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        // Intent to open App Info/Settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}