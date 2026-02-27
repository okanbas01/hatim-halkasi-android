package com.example.sharedkhatm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val PREFS_APP = "AppGlobalPrefs"

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openExactAlarmSettingsIfNeeded()
        }
        markFirstRunDone()
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> askNotificationPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        bottomNav = findViewById(R.id.bottomNavigation)

        checkIfPermissionsGrantedAlready()

        val prefs = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            askLocationPermission()
        }

        handleNavigationIntent(intent)

        if (savedInstanceState == null && intent.getStringExtra("openTab") == null) {
            loadFragmentRoot(DashboardFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { loadFragmentRoot(DashboardFragment()); true }
                R.id.nav_quran -> { loadFragmentRoot(QuranHubFragment()); true }
                R.id.nav_spirit -> { loadFragmentRoot(SpiritualityFragment()); true }
                R.id.nav_community -> { loadFragmentRoot(FriendsFragment()); true }
                else -> false
            }
        }
    }

    // ✅ Dashboard gibi bir yerden "push" gibi açmak için kullan (backstack'li)
    fun openFragment(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun checkIfPermissionsGrantedAlready() {
        val locGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (locGranted || notifGranted) {
            markFirstRunDone()
        }
    }

    private fun askLocationPermission() {
        val permissionsToRequest = mutableListOf<String>()

        val fineNotGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        val coarseNotGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED

        if (fineNotGranted && coarseNotGranted) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestLocationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            askNotificationPermission()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (notGranted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                markFirstRunDone()
            }
        } else {
            markFirstRunDone()
        }
    }

    private fun markFirstRunDone() {
        val prefs = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("isFirstRun", false).apply()
    }

    /** Bildirim izni verildiyse, vakit bildirimleri için "Alarmlar ve anımsatıcılar" ekranına yönlendir. */
    private fun openExactAlarmSettingsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (am.canScheduleExactAlarms()) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Namaz vakitleri için 'Alarmlar ve anımsatıcılar' iznini açın.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent) {
        val openTab = intent.getStringExtra("openTab")

        if (openTab == "friends") {
            loadFragmentRoot(FriendsFragment())
            bottomNav.selectedItemId = R.id.nav_community
        } else if (openTab == "surah") {
            val prefs = getSharedPreferences("AppNavigation", MODE_PRIVATE)
            prefs.edit().putBoolean("openSurahTab", true).apply()
            loadFragmentRoot(QuranHubFragment())
            bottomNav.selectedItemId = R.id.nav_quran
        } else if (openTab == "yasin") {
            val prefs = getSharedPreferences("AppNavigation", MODE_PRIVATE)
            prefs.edit().putBoolean("openYasinTab", true).apply()
            loadFragmentRoot(QuranHubFragment())
            bottomNav.selectedItemId = R.id.nav_quran
        }
    }

    private fun loadFragmentRoot(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun showBadge(count: Int) {
        val badge = bottomNav.getOrCreateBadge(R.id.nav_community)
        if (count > 0) {
            badge.isVisible = true
            badge.number = count
        } else {
            badge.isVisible = false
            badge.clearNumber()
        }
    }

    fun clearBadge() {
        bottomNav.getBadge(R.id.nav_community)?.apply {
            isVisible = false
            clearNumber()
        }
    }
}
