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
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var switchMorning: MaterialSwitch
    private lateinit var switchGoal: MaterialSwitch

    // Hangi switch izin bekliyor?
    private var pendingSwitch: MaterialSwitch? = null
    private var pendingPrefKey: String? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val sw = pendingSwitch
            val key = pendingPrefKey

            pendingSwitch = null
            pendingPrefKey = null

            if (sw == null || key == null) return@registerForActivityResult

            if (isGranted) {
                // Exact alarm izni de kontrol et
                if (!ensureExactAlarmPermissionIfNeeded()) {
                    sw.isChecked = false
                    savePref(key, false)
                    return@registerForActivityResult
                }

                sw.isChecked = true
                savePref(key, true)
                enableDailyForKey(key)
            } else {
                sw.isChecked = false
                savePref(key, false)
                Toast.makeText(this, "Bildirim izni reddedildi.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnBack = findViewById<ImageView>(R.id.btnBackSettings)
        btnBack?.setOnClickListener { finish() }

        switchMorning = findViewById(R.id.switchMorning)
        switchGoal = findViewById(R.id.switchGoal)

        val layoutProfile = findViewById<LinearLayout>(R.id.layoutProfileSettings)

        setupClickListeners()
        setupProfileArea(layoutProfile)

        // İlk açılışta switchleri pref/izin durumuna göre hizala
        syncSwitchesWithSystem()
    }

    override fun onResume() {
        super.onResume()
        // Ayarlardan dönünce izinler değişmiş olabilir
        syncSwitchesWithSystem()
    }

    private fun setupClickListeners() {
        switchMorning.setOnClickListener { view ->
            handleDailySwitchClick(view as MaterialSwitch, KEY_MORNING)
        }
        switchGoal.setOnClickListener { view ->
            handleDailySwitchClick(view as MaterialSwitch, KEY_GOAL)
        }
    }

    /**
     * Morning & Goal switch’leri için ortak akış:
     * 1) Bildirim izni (Android 13+)
     * 2) Exact alarm izni (Android 12+)
     * 3) Pref kaydet + alarm kur/iptal
     */
    private fun handleDailySwitchClick(sw: MaterialSwitch, prefKey: String) {
        val wantsEnable = sw.isChecked

        if (wantsEnable) {
            // 1) Notification Permission (Android 13+)
            if (!hasNotificationPermission()) {
                sw.isChecked = false
                pendingSwitch = sw
                pendingPrefKey = prefKey

                // "Bir daha sorma" gibi durumlarda ayara yönlendir
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    showSettingsRedirectDialog()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                return
            }

            // 2) Exact Alarm Permission (Android 12+)
            if (!ensureExactAlarmPermissionIfNeeded()) {
                sw.isChecked = false
                savePref(prefKey, false)
                return
            }

            // 3) Kaydet + kur
            savePref(prefKey, true)
            enableDailyForKey(prefKey)

        } else {
            // disable
            savePref(prefKey, false)
            disableDailyForKey(prefKey)
        }
    }

    private fun enableDailyForKey(prefKey: String) {
        when (prefKey) {
            KEY_MORNING -> DailyAlarmScheduler.scheduleMorningIfEnabled(this)
            KEY_GOAL -> DailyAlarmScheduler.scheduleGoalIfEnabled(this)
        }
        Toast.makeText(this, "Bildirim ayarı aktif edildi.", Toast.LENGTH_SHORT).show()
    }

    private fun disableDailyForKey(prefKey: String) {
        when (prefKey) {
            KEY_MORNING -> DailyAlarmScheduler.cancelMorning(this)
            KEY_GOAL -> DailyAlarmScheduler.cancelGoal(this)
        }
        Toast.makeText(this, "Bildirim ayarı kapatıldı.", Toast.LENGTH_SHORT).show()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Android 12+ exact alarm izni yoksa kullanıcıyı ayara yönlendirir.
     */
    private fun ensureExactAlarmPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "Tam zamanlı bildirim için 'Kesin Alarm' izni gerekli. Ayarlardan açınız.",
                    Toast.LENGTH_LONG
                ).show()
                DailyAlarmScheduler.requestExactAlarmPermission(this)
                return false
            }
        }
        return true
    }

    private fun syncSwitchesWithSystem() {
        val prefs = getSharedPreferences(PREF_GLOBAL, Context.MODE_PRIVATE)

        val notifAllowed = hasNotificationPermission()

        if (notifAllowed) {
            switchMorning.isChecked = prefs.getBoolean(KEY_MORNING, true)
            switchGoal.isChecked = prefs.getBoolean(KEY_GOAL, true)
        } else {
            // Sistem izni yoksa switchler kapalı görünür
            switchMorning.isChecked = false
            switchGoal.isChecked = false
        }
    }

    private fun savePref(key: String, value: Boolean) {
        val prefs = getSharedPreferences(PREF_GLOBAL, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun showSettingsRedirectDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_permission_redirect)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnCancel = dialog.findViewById<View>(R.id.btnDialogCancel)
        val btnSettings = dialog.findViewById<View>(R.id.btnDialogSettings)

        btnCancel.setOnClickListener {
            dialog.dismiss()
            pendingSwitch?.isChecked = false
            pendingSwitch = null
            pendingPrefKey = null
        }

        btnSettings.setOnClickListener {
            dialog.dismiss()
            openAppSettings()
        }

        dialog.show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Ayarlar açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------- Profil Bölümü (KORUNDU) --------------------

    private fun setupProfileArea(layoutProfile: LinearLayout) {
        try {
            val user = auth.currentUser
            if (user == null || user.isAnonymous) {
                layoutProfile.visibility = View.GONE
                return
            }

            layoutProfile.visibility = View.VISIBLE

            val etName = findViewById<TextInputEditText>(R.id.etEditName)
            val etUsername = findViewById<TextInputEditText>(R.id.etEditUsername)
            val btnUpdateProfile = findViewById<Button>(R.id.btnUpdateProfile)

            val etCurrentPass = findViewById<TextInputEditText>(R.id.etCurrentPassword)
            val etNewPass = findViewById<TextInputEditText>(R.id.etNewPassword)
            val btnUpdatePass = findViewById<Button>(R.id.btnUpdatePassword)

            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (!isFinishing) {
                        etName.setText(document.getString("name"))
                        etUsername.setText(document.getString("username"))
                    }
                }
                .addOnFailureListener { e -> e.printStackTrace() }

            btnUpdateProfile.setOnClickListener {
                val name = etName.text?.toString().orEmpty().trim()
                val username = etUsername.text?.toString().orEmpty().trim()

                if (name.isNotEmpty() && username.isNotEmpty()) {
                    db.collection("users").document(user.uid)
                        .update("name", name, "username", username)
                        .addOnSuccessListener { Toast.makeText(this, "Profil güncellendi", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { Toast.makeText(this, "Güncelleme hatası", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this, "İsim ve kullanıcı adı boş olamaz.", Toast.LENGTH_SHORT).show()
                }
            }

            btnUpdatePass.setOnClickListener {
                val currentPass = etCurrentPass.text?.toString().orEmpty()
                val newPass = etNewPass.text?.toString().orEmpty()
                val email = user.email

                if (currentPass.isNotEmpty() && newPass.length >= 6 && email != null) {
                    val credential = EmailAuthProvider.getCredential(email, currentPass)
                    user.reauthenticate(credential).addOnSuccessListener {
                        user.updatePassword(newPass).addOnSuccessListener {
                            Toast.makeText(this, "Şifre değiştirildi.", Toast.LENGTH_SHORT).show()
                            etCurrentPass.text?.clear()
                            etNewPass.text?.clear()
                        }.addOnFailureListener {
                            Toast.makeText(this, "Şifre değiştirilemedi: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }.addOnFailureListener {
                        Toast.makeText(this, "Mevcut şifre hatalı.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Alanları kontrol edin (Min 6 karakter).", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val PREF_GLOBAL = "AppGlobalPrefs"
        private const val KEY_MORNING = "notif_morning"
        private const val KEY_GOAL = "notif_goal"
    }
}
