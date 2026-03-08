package com.example.sharedkhatm

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Dark / Light mode state and application.
 * Single responsibility: persist and apply night mode; no UI.
 */
object ThemeManager {

    private const val PREFS_NAME = "ThemePrefs"
    private const val KEY_DARK_MODE = "darkMode"

    fun isDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, dark)
            .apply()
        applyNightMode(dark)
    }

    /**
     * Applies the stored preference. Call from Application.onCreate() so cold start uses correct theme.
     */
    fun applyStoredMode(context: Context) {
        applyNightMode(isDarkMode(context))
    }

    private fun applyNightMode(dark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
