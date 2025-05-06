package com.dzboot.ovpn.helpers

import android.content.Context
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.dzboot.ovpn.R
import java.util.*


object LocalesHelper {

    private val LOCALES = arrayListOf("default", "en", "ar", "fr", "de", "fa", "ru", "tk", "zh", "tr", "lt")

    private fun getDisplayLanguage(context: Context, code: String?): String =
        if (code.isNullOrBlank() || code == "default")
            context.getString(R.string.default_mode)
        else with(Locale(code)) { getDisplayLanguage(this) }

    fun getLanguagesEntries(context: Context) = LOCALES.map { getDisplayLanguage(context, it) }.toTypedArray()

    fun getLanguagesValues() = LOCALES.toTypedArray()

    fun getDefaultLanguage(context: Context) = getDisplayLanguage(context, PrefsHelper.getLanguage())

    fun getLocaleFromLanguageCode(code: String?): Locale = if (code.isNullOrBlank() || code == "default") {
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        if (locales.isEmpty) Locale.getDefault() else locales[0]!!
    } else
        Locale(code)
}