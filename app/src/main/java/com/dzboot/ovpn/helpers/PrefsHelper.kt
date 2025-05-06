package com.dzboot.ovpn.helpers

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.base.BaseApplication
import com.dzboot.ovpn.data.models.AdIds
import com.dzboot.ovpn.data.models.Server

import org.lsposed.lsparanoid.Obfuscate


@Obfuscate
object PrefsHelper {

	private const val FIRST_RUN = "first_run"
	private const val FIRST_CONNECT = "first_connect"
	private const val APPS_NOT_USING = "not_allowed_apps"
	private const val CONNECT_SERVER_ID = "connect_server_id"
	private const val ORIGINAL_IP = "original_ip"
	private const val ADS_INITIALIZED = "setup"
	private const val SAVED_USERNAME_KEY = "save_auth_username"
	private const val SAVED_PASSWORD_KEY = "save_auth_password"

	private const val ADMOB_BANNER_ID = "admob_banner_id"
	private const val ADMOB_INTER_ID = "admob_inter_id"
	private const val ADMOB_REWARDED_ID = "admob_rewarded_id"
	private const val ADMOB_REWARDED_INTER_ID = "admob_rewarded_inter_id"
	private const val ADMOB_NATIVE_ID = "admob_native_id"
	private const val ADMOB_APP_OPEN_ID = "admob_app_open_id"

	private val sp: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(BaseApplication.instance) }


	fun isFirstRun() = sp.getBoolean(FIRST_RUN, true)
	fun disableFirstRun() = sp.edit().putBoolean(FIRST_RUN, false).apply()

	fun isFirstConnect() = sp.getBoolean(FIRST_CONNECT, true)
	fun disableFirstConnect() = sp.edit().putBoolean(FIRST_CONNECT, false).apply()

	fun setAllAppsUsing() = sp.edit().putStringSet(APPS_NOT_USING, null).apply()
	fun saveAppsNotUsing(inactiveApps: HashSet<String>) = sp.edit().putStringSet(APPS_NOT_USING, inactiveApps).apply()
	fun getAppsNotUsing(): HashSet<String> = sp.getStringSet(APPS_NOT_USING, HashSet<String>()) as HashSet<String>

	fun getDisplayMode() = sp.getString(BuildConfig.DISPLAY_MODE_KEY, "default")
	fun getLanguage() = sp.getString(BuildConfig.LANG_KEY, "default")
	fun getAutoMode() = sp.getString(BuildConfig.AUTO_MODE_KEY, Server.LOAD)

	fun shouldShowPersistentNotif() = sp.getBoolean(BuildConfig.PERSISTENT_NOTIF_KEY, true)
	fun setShowPersistentNotif(value: Boolean) = sp.edit().putBoolean(BuildConfig.PERSISTENT_NOTIF_KEY, value).apply()
	fun getReconnectTimeout() = sp.getInt(BuildConfig.RECONNECT_TIMEOUT_KEY, 8)
	fun getReconnectRetries() = sp.getInt(BuildConfig.RECONNECT_RETRIES_KEY, 5)

	fun notificationPermissionRationalAcknowledged() = sp.getBoolean(
		BuildConfig.NOTIF_PERMISSION_RATIONALE_ACKNOWLEDGED, false
	)

	fun setNotificationPermissionRationalAcknowledged(value: Boolean) = sp.edit().putBoolean(
		BuildConfig.NOTIF_PERMISSION_RATIONALE_ACKNOWLEDGED, value
	).apply()

	fun getSavedServerId() = sp.getInt(CONNECT_SERVER_ID, -1)
	fun saveServer(id: Int) = sp.edit().putInt(CONNECT_SERVER_ID, id).apply()

	fun saveOriginalIP(ip: String?) = sp.edit().putString(ORIGINAL_IP, ip).apply()
	fun getOriginalIP() = sp.getString(ORIGINAL_IP, null)

	fun setAdsInitialized(value: Boolean) = sp.edit().putBoolean(ADS_INITIALIZED, value).apply()
	fun getAdsInitialization() = sp.getBoolean(ADS_INITIALIZED, false)

	fun notConsentedToPersonalizedAds() = sp.getString("IABTCF_VendorConsents", null) == "0"

	fun getSavedUsername() = sp.getString(SAVED_USERNAME_KEY, "") ?: ""
	fun getSavedPassword() = sp.getString(SAVED_PASSWORD_KEY, "") ?: ""

	fun saveUserCredentials(username: String, password: String) =
		sp.edit().putString(SAVED_USERNAME_KEY, username).putString(SAVED_PASSWORD_KEY, password).apply()

	fun storeAdIds(adIds: AdIds) {
		sp.edit()
			.putString(ADMOB_BANNER_ID, adIds.abi)
			.putString(ADMOB_INTER_ID, adIds.aii)
			.putString(ADMOB_APP_OPEN_ID, adIds.aai)
			.putString(ADMOB_NATIVE_ID, adIds.ani)
			.putString(ADMOB_REWARDED_ID, adIds.ari)
			.putString(ADMOB_REWARDED_INTER_ID, adIds.arii)
			.apply()
	}

	fun getAdmobBannerId() = sp.getString(ADMOB_BANNER_ID, "") ?: ""
	fun getAdmobInterId() = sp.getString(ADMOB_INTER_ID, "") ?: ""
	fun getAdmobAppOpenId() = sp.getString(ADMOB_APP_OPEN_ID, "") ?: ""
	fun getAdmobNativeId() = sp.getString(ADMOB_NATIVE_ID, "") ?: ""
	fun getAdmobRewardedId() = sp.getString(ADMOB_REWARDED_ID, "") ?: ""
	fun getAdmobRewardedInterId() = sp.getString(ADMOB_REWARDED_INTER_ID, "") ?: ""
}