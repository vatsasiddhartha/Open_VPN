package com.dzboot.ovpn.helpers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.dzboot.ovpn.R


object ThemeHelper {

   private const val LIGHT = "light"
   private const val DARK = "dark"
   private const val DEFAULT = "default"

   private val DISPLAY_MODES = linkedMapOf(
         DEFAULT to R.string.default_mode,
         DARK to R.string.dark,
         LIGHT to R.string.light
   )

   @StringRes
   fun getDisplayModeResId(prefValue: String?) = DISPLAY_MODES[prefValue] ?: R.string.default_mode

   fun getLanguagesEntries(context: Context) = DISPLAY_MODES.map { context.getString(it.value) }.toTypedArray()

   fun getDisplayValues() = DISPLAY_MODES.keys.toTypedArray()

   fun Context.isSystemDarkThemeOn() =
         resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES

   @JvmStatic
   fun applyTheme(themePref: String?) {
      when (themePref) {
         LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
         DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
         else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
         } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
         }
      }
   }
}