package com.example.smartblindstick

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. THEME LOGIC: Check if the user has an override preference
        val prefs = getSharedPreferences("NazarSettings", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("app_theme", "System Default")

        // Apply the saved theme preference globally
        when (savedTheme) {
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        // 2. UI SETUP: Edge-to-edge support
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        // 3. NAVIGATION SETUP
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Disable icon tint to show your colorful custom PNGs
        bottomNavigation.itemIconTintList = null

        // Set default fragment on first launch
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_dashboard
            loadFragment(DashboardFragment())
        }

        // Fragment switching logic
        bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_faq -> loadFragment(FaqFragment())
                R.id.nav_map -> loadFragment(MapFragment())
                R.id.nav_dashboard -> loadFragment(DashboardFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
                R.id.nav_settings -> loadFragment(SettingsFragment())
            }
            true
        }
    }

    /**
     * Helper function to swap fragments inside the main container
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}