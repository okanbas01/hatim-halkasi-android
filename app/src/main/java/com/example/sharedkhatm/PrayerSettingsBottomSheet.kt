package com.example.sharedkhatm

import android.Manifest
import android.app.AlarmManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch

class PrayerSettingsBottomSheet : BottomSheetDialogFragment() {

    private val turkeyCities = arrayOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin", "Aydın", "Balıkesir",
        "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli",
        "Diyarbakır", "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane", "Hakkari",
        "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir", "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir",
        "Kocaeli", "Konya", "Kütahya", "Malatya", "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş", "Nevşehir",
        "Niğde", "Ordu", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat",
        "Trabzon", "Tunceli", "Şanlıurfa", "Uşak", "Van", "Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman",
        "Kırıkkale", "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır", "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce"
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                activateAllNotificationsIfReady()
                // Bildirim izni verildi → vakit bildirimleri için "Alarmlar ve anımsatıcılar" ekranına yönlendir
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission()) {
                    openExactAlarmSettings()
                    Toast.makeText(
                        requireContext(),
                        "Namaz vakitleri için 'Alarmlar ve anımsatıcılar' iznini açın.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    scheduleIfSavedTimingsExist()
                }
            } else {
                showPermissionRedirectDialog()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_prayer_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refreshAllRows()

        val prefs = requireContext().getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
        val hasPermission = checkNotificationPermission()

        // Kerahat switch
        val switchKerahat = view.findViewById<MaterialSwitch>(R.id.switchKerahat)
        switchKerahat.isChecked = if (hasPermission) prefs.getBoolean("notif_kerahat", false) else false
        switchKerahat.setOnClickListener { handleSwitchClick(switchKerahat, "notif_kerahat", prefs) }

        // 15 dk önce uyarı switch
        val switchPreWarning = view.findViewById<MaterialSwitch>(R.id.switchPreWarning)
        switchPreWarning.isChecked = if (hasPermission) prefs.getBoolean("notif_prayer", true) else false
        switchPreWarning.setOnClickListener { handleSwitchClick(switchPreWarning, "notif_prayer", prefs) }
    }

    private fun handleSwitchClick(
        switchView: MaterialSwitch,
        key: String,
        prefs: android.content.SharedPreferences
    ) {
        val wantsEnable = switchView.isChecked

        if (wantsEnable) {

            // 1) Bildirim izni
            if (!checkNotificationPermission()) {
                switchView.isChecked = false
                askForNotificationPermission()
                return
            }

            // 2) Şehir/konum seçili mi
            if (!checkLocationOrCity()) {
                switchView.isChecked = false
                showCitySelectDialog()
                return
            }

            // 3) Android 12+ Exact alarm izni
            if (!hasExactAlarmPermission()) {
                switchView.isChecked = false
                openExactAlarmSettings()
                Toast.makeText(
                    context,
                    "Namaz vakitlerini tam dakikasında almak için 'Kesin Alarmlar'a izin verin.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // OK -> kaydet
            prefs.edit().putBoolean(key, true).apply()

            // ✅ izin + şehir + exact alarm hazırsa default beep uygula (sadece yoksa)
            activateAllNotificationsIfReady()

            // kayıtlı vakit varsa anında alarm kur
            scheduleIfSavedTimingsExist()

        } else {
            prefs.edit().putBoolean(key, false).apply()

            val prayerEnabled = prefs.getBoolean("notif_prayer", true)
            val kerahatEnabled = prefs.getBoolean("notif_kerahat", false)

            if (!prayerEnabled && !kerahatEnabled) {
                PrayerReminderScheduler(requireContext()).cancelAll()
            } else {
                scheduleIfSavedTimingsExist()
            }
        }
    }

    private fun scheduleIfSavedTimingsExist() {
        val locPrefs = requireContext().getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)

        val fajr = locPrefs.getString("saved_fajr", null)
        val sunrise = locPrefs.getString("saved_sunrise", null)
        val dhuhr = locPrefs.getString("saved_dhuhr", null)
        val asr = locPrefs.getString("saved_asr", null)
        val maghrib = locPrefs.getString("saved_maghrib", null)
        val isha = locPrefs.getString("saved_isha", null)

        if (fajr != null && sunrise != null && dhuhr != null && asr != null && maghrib != null && isha != null) {
            val lite = PrayerTimesLite(
                fajr = fajr,
                sunrise = sunrise,
                dhuhr = dhuhr,
                asr = asr,
                maghrib = maghrib,
                isha = isha
            )
            val ctx = requireContext()
            PrayerTimesCache.write(ctx, lite)
            prayerScheduleExecutor.execute {
                try {
                    PrayerReminderScheduler(ctx).rescheduleAll(lite)
                } catch (_: Exception) { }
            }
        }
    }

    companion object {
        private val prayerScheduleExecutor = Executors.newSingleThreadExecutor()
    }

    private fun refreshAllRows() {
        view?.let { v ->
            setupRow(v.findViewById(R.id.rowFajr), "Sabah Namazı", "sound_fajr")
            setupRow(v.findViewById(R.id.rowDhuhr), "Öğle Namazı", "sound_dhuhr")
            setupRow(v.findViewById(R.id.rowAsr), "İkindi Namazı", "sound_asr")
            setupRow(v.findViewById(R.id.rowMaghrib), "Akşam Namazı", "sound_maghrib")
            setupRow(v.findViewById(R.id.rowIsha), "Yatsı Namazı", "sound_isha")
        }
    }

    private fun setupRow(row: View, name: String, key: String) {
        val txtName = row.findViewById<TextView>(R.id.txtName)
        val btnSound = row.findViewById<ImageView>(R.id.btnSoundType)
        txtName.text = name

        val prefs = requireContext().getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
        val hasPermission = checkNotificationPermission()
        val hasLocation = checkLocationOrCity()

        // ✅ Varsayılan: eğer key hiç yoksa beep(1) gibi davran
        val currentMode = if (hasPermission && hasLocation) {
            if (prefs.contains(key)) prefs.getInt(key, 1) else 1
        } else 0

        updateIcon(btnSound, currentMode)

        btnSound.setOnClickListener {
            if (!checkNotificationPermission()) {
                askForNotificationPermission()
                return@setOnClickListener
            }
            if (!checkLocationOrCity()) {
                showCitySelectDialog()
                return@setOnClickListener
            }
            if (!hasExactAlarmPermission()) {
                openExactAlarmSettings()
                Toast.makeText(
                    context,
                    "Namaz vakitlerini tam dakikasında almak için 'Kesin Alarmlar'a izin verin.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            var mode = if (prefs.contains(key)) prefs.getInt(key, 1) else 1
            mode = (mode + 1) % 3
            prefs.edit().putInt(key, mode).apply()
            updateIcon(btnSound, mode)

            scheduleIfSavedTimingsExist()
        }
    }

    private fun activateAllNotificationsIfReady() {
        if (checkNotificationPermission() && checkLocationOrCity() && hasExactAlarmPermission()) {
            val prefs = requireContext().getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            val keys = listOf("sound_fajr", "sound_dhuhr", "sound_asr", "sound_maghrib", "sound_isha")

            // ✅ Kullanıcı izin verdiğinde default beep:
            // SADECE key hiç yoksa -> 1 yaz.
            // (Kullanıcı daha önce 0/2 seçtiyse EZMEYELİM.)
            for (key in keys) {
                if (!prefs.contains(key)) {
                    editor.putInt(key, 1)
                }
            }
            editor.apply()

            view?.post {
                refreshAllRows()
                Toast.makeText(context, "Ayarlar güncellendi.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.setFragmentResult(
                    "city_update_request",
                    Bundle().apply { putBoolean("refresh", true) }
                )
            }
        }
    }

    private fun updateIcon(img: ImageView, mode: Int) {
        img.alpha = 1.0f
        when (mode) {
            0 -> { img.setImageResource(R.drawable.ic_sound_silent); img.alpha = 0.5f }
            1 -> img.setImageResource(R.drawable.ic_sound_beep)
            2 -> img.setImageResource(R.drawable.ic_sound_adhan)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun checkLocationOrCity(): Boolean {
        val prefs = requireContext().getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
        return prefs.contains("savedCity")
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun showPermissionRedirectDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_permission_redirect)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.btnDialogSettings).setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (_: Exception) {}
        }
        dialog.show()
    }

    private fun showCitySelectDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_city_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.70).toInt()
        dialog.window?.setLayout(width, height)

        val listView = dialog.findViewById<android.widget.ListView>(R.id.listCities)
        val btnCancel = dialog.findViewById<View>(R.id.btnCancelCity)

        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.item_city_row,
            R.id.txtCityName,
            turkeyCities
        )
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedCity = turkeyCities[position]
            saveCityAndRetry(selectedCity)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            val switchKerahat = view?.findViewById<MaterialSwitch>(R.id.switchKerahat)
            val switchPreWarning = view?.findViewById<MaterialSwitch>(R.id.switchPreWarning)
            if (!checkLocationOrCity()) {
                switchKerahat?.isChecked = false
                switchPreWarning?.isChecked = false
            }
        }
        dialog.show()
    }

    private fun saveCityAndRetry(city: String) {
        val prefs = requireContext().getSharedPreferences("LocationPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("savedCity", city)
            .putString("locationMode", "MANUAL")
            .apply()

        Toast.makeText(context, "$city seçildi.", Toast.LENGTH_SHORT).show()

        parentFragmentManager.setFragmentResult(
            "city_update_request",
            Bundle().apply { putBoolean("refresh", true) }
        )

        // Şehir seçildi → artık ready olabilir → default beep yaz (sadece yoksa)
        activateAllNotificationsIfReady()

        scheduleIfSavedTimingsExist()
    }
}
