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
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.util.concurrent.Executors
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
        (application as? com.example.sharedkhatm.MyApplication)?.interstitialManager?.recordAppLaunch()
        (application as? com.example.sharedkhatm.MyApplication)?.appOpenManager?.maybeShowAfterDelay(this)

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

    override fun onResume() {
        super.onResume()
        applyDefaultPrayerNotificationsIfNeeded()
    }

    /**
     * "Alarmlar ve anımsatıcılar" izni + şehir varsa ve kullanıcı henüz bildirim ayarı yapmadıysa
     * varsayılanı aç: 15 dk önce + tam vakit bildirimleri açık, ses türü bildirim sesi (1).
     * Kullanıcının Vakit bildirim ayarlarına girmesine gerek kalmaz.
     */
    private fun applyDefaultPrayerNotificationsIfNeeded() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        val locPrefs = getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
        if (!locPrefs.contains("savedCity")) return
        val appPrefs = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        if (appPrefs.contains("notif_prayer")) return

        val editor = appPrefs.edit()
        editor.putBoolean("notif_prayer", true)
        editor.putBoolean("notif_kerahat", false)
        val soundKeys = listOf("sound_fajr", "sound_dhuhr", "sound_asr", "sound_maghrib", "sound_isha")
        for (key in soundKeys) {
            if (!appPrefs.contains(key)) editor.putInt(key, 1)
        }
        editor.apply()

        val lite = readPrayerTimesLiteFromPrefs(locPrefs) ?: return
        prayerScheduleExecutor.execute {
            try {
                PrayerReminderScheduler(applicationContext).rescheduleAll(lite)
            } catch (_: Exception) { }
        }
    }

    private fun readPrayerTimesLiteFromPrefs(locPrefs: android.content.SharedPreferences): PrayerTimesLite? {
        val fajr = locPrefs.getString("saved_fajr", null) ?: return null
        val dhuhr = locPrefs.getString("saved_dhuhr", null) ?: return null
        return PrayerTimesLite(
            fajr = fajr,
            sunrise = locPrefs.getString("saved_sunrise", "") ?: "",
            dhuhr = dhuhr,
            asr = locPrefs.getString("saved_asr", "") ?: "",
            maghrib = locPrefs.getString("saved_maghrib", "") ?: "",
            isha = locPrefs.getString("saved_isha", "") ?: ""
        )
    }

    companion object {
        private val prayerScheduleExecutor = Executors.newSingleThreadExecutor()
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

    override fun onDestroy() {
        (application as? com.example.sharedkhatm.MyApplication)?.appOpenManager?.onDestroy()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window?.decorView?.windowToken, 0)
        window?.decorView?.clearFocus()
        super.onDestroy()
    }
}
