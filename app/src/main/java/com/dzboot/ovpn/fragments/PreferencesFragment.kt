package com.dzboot.ovpn.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.R
import com.dzboot.ovpn.activities.MainActivity
import com.dzboot.ovpn.base.BaseFragmentInterface
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.helpers.AdsManager.Companion.resetGDPRConsent
import com.dzboot.ovpn.helpers.AdsManager.Companion.showResetGDPRConsent
import com.dzboot.ovpn.helpers.DialogHelper
import com.dzboot.ovpn.helpers.LocalesHelper
import com.dzboot.ovpn.helpers.NotificationsHelper
import com.dzboot.ovpn.helpers.PrefsHelper
import com.dzboot.ovpn.helpers.ThemeHelper
import com.dzboot.ovpn.helpers.Utils.getToSubscriptionsPage
import com.dzboot.ovpn.helpers.Utils.goToAppInfoPage
import com.dzboot.ovpn.helpers.VPNHelper.openVPNSettings
import com.takisoft.preferencex.PreferenceCategory
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SwitchPreferenceCompat


class PreferencesFragment : PreferenceFragmentCompat(), BaseFragmentInterface,
                            SharedPreferences.OnSharedPreferenceChangeListener {

	companion object {

		const val STATIC_TAG = "PreferencesFragment"
	}

	override val TAG = STATIC_TAG
	override fun getPageTitle() = R.string.settings
	override fun toString() = TAG

	private val displayPref by lazy { findPreference<ListPreference>(BuildConfig.DISPLAY_MODE_KEY) }
	private val autoModePref by lazy { findPreference<ListPreference>(BuildConfig.AUTO_MODE_KEY) }
	private val reconnectTimeout by lazy { findPreference<SeekBarPreference>(BuildConfig.RECONNECT_TIMEOUT_KEY) }
	private val reconnectRetries by lazy { findPreference<SeekBarPreference>(BuildConfig.RECONNECT_RETRIES_KEY) }
	private val persistentNotif by lazy { findPreference<SwitchPreferenceCompat>(BuildConfig.PERSISTENT_NOTIF_KEY) }

	private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (it) {
			persistentNotif?.isChecked = true
			PrefsHelper.setShowPersistentNotif(true)
			// given that the permission has been granted, clear the heuristic from persistent storage
			PrefsHelper.setNotificationPermissionRationalAcknowledged(false)
		} else {
			persistentNotif?.isChecked = false
			PrefsHelper.setShowPersistentNotif(false)
		}
	}


	override fun onResume() {
		super.onResume()
		preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
	}

	override fun onPause() {
		super.onPause()
		preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
	}

	@SuppressLint("InlinedApi")
	override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences, rootKey)

		setReconnectTimeoutSummary()
		setReconnectRetriesSummary()

		if ((activity as MainActivity).showResetGDPRConsent()) {
			findPreference<PreferenceCategory>("ads_category")?.isVisible = true
			findPreference<Preference>(BuildConfig.RESET_GDPR_KEY)?.isVisible = true
		}

		val langPref = findPreference<ListPreference>(BuildConfig.LANG_KEY)
		langPref?.entries = LocalesHelper.getLanguagesEntries(requireContext())
		langPref?.entryValues = LocalesHelper.getLanguagesValues()
		langPref?.summary = getString(R.string.lang_summary, LocalesHelper.getDefaultLanguage(requireContext()))

		autoModePref?.entries = Server.getAutoModeEntries(requireContext())
		autoModePref?.entryValues = Server.getAutoModeValues()
		setAutoModeSummary()

		displayPref?.entries = ThemeHelper.getLanguagesEntries(requireContext())
		displayPref?.entryValues = ThemeHelper.getDisplayValues()
		PrefsHelper.getDisplayMode()?.let { setDisplayModeSummary(it) }

		persistentNotif?.setOnPreferenceChangeListener { _, newValue ->
			val isChecked = newValue as Boolean

			return@setOnPreferenceChangeListener when {
				!isChecked -> true

				(activity as MainActivity?)?.isNotificationsPermissionGranted() == true -> {
					// given that the permission has been granted, clear the heuristic from persistent storage
					PrefsHelper.setNotificationPermissionRationalAcknowledged(false)
					true
				}

				shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
					activity?.let {
						DialogHelper.notificationPermission(it) {
							requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
						}
					}
					false
				}

				PrefsHelper.notificationPermissionRationalAcknowledged() -> {
					activity?.let {
						DialogHelper.permissionActivelyDenied(it) {
							persistentNotif?.isChecked = false
						}
					}
					false
				}

				else -> {
					requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
					false
				}
			}
		}
	}

	@SuppressLint("NewApi")
	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		when (preference.key) {
			BuildConfig.RESET_GDPR_KEY -> with(activity as MainActivity) { resetGDPRConsent() }
			BuildConfig.GO_TO_VPN_SETTINGS_KEY -> activity?.openVPNSettings()
			BuildConfig.GO_TO_INFO_PAGE_KEY -> activity?.goToAppInfoPage()
			BuildConfig.MY_SUBSCRIPTIONS_KEY -> activity?.getToSubscriptionsPage()
			else -> return super.onPreferenceTreeClick(preference)
		}
		return true
	}

	override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {

		when (key) {
			BuildConfig.RECONNECT_TIMEOUT_KEY -> setReconnectTimeoutSummary()
			BuildConfig.RECONNECT_RETRIES_KEY -> setReconnectRetriesSummary()
			BuildConfig.LANG_KEY -> (activity as MainActivity).updateLocale(
				LocalesHelper.getLocaleFromLanguageCode(sp.getString(BuildConfig.LANG_KEY, "default"))
			)

			BuildConfig.DISPLAY_MODE_KEY -> {
				val theme = sp.getString(BuildConfig.DISPLAY_MODE_KEY, "default")
				setDisplayModeSummary(theme)
				ThemeHelper.applyTheme(theme)
			}

			BuildConfig.PERSISTENT_NOTIF_KEY ->
				if (sp.getBoolean(BuildConfig.PERSISTENT_NOTIF_KEY, true))
					NotificationsHelper.showPersistentNotification(requireContext(), MainActivity::class.java)
				else
					NotificationsHelper.cancelPersistentNotification(requireContext())
			//            BuildConfig.AUTO_MODE_KEY -> setAutoModeSummary(sp.getString(key, Server.LOAD))
			BuildConfig.AUTO_MODE_KEY -> setAutoModeSummary()
		}
	}

	private fun setReconnectTimeoutSummary() {
		reconnectTimeout?.summary = getString(R.string.reconnect_timeout_summary, PrefsHelper.getReconnectTimeout())
	}

	private fun setReconnectRetriesSummary() {
		reconnectRetries?.summary = getString(R.string.reconnect_retries_summary, PrefsHelper.getReconnectRetries())
	}

	private fun setDisplayModeSummary(themeMode: String?) {
		displayPref?.summary =
			getString(R.string.display_mode_summary, getString(ThemeHelper.getDisplayModeResId(themeMode)))
	}

	private fun setAutoModeSummary() {
		autoModePref?.summary =
			getString(R.string.auto_mode_summary, Server.getAutoModeString(requireContext(), PrefsHelper.getAutoMode()))
	}
}