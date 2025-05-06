package com.dzboot.ovpn.activities

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.R
import com.dzboot.ovpn.base.BaseApplication.Companion.runningOnTV
import com.dzboot.ovpn.base.BaseFragmentInterface
import com.dzboot.ovpn.base.BaseMainActivity
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.remote.NetworkCaller
import com.dzboot.ovpn.databinding.ActivityMainBinding
import com.dzboot.ovpn.fragments.*
import com.dzboot.ovpn.helpers.*
import com.dzboot.ovpn.helpers.AdsManager.Companion.showInterstitialAd
import com.dzboot.ovpn.services.NotificationActionBroadcast
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.navigation.NavigationView
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.lsparanoid.Obfuscate
import timber.log.Timber
import java.util.*


@Obfuscate
class MainActivity : BaseMainActivity<ActivityMainBinding>(), NavigationView.OnNavigationItemSelectedListener,
                     Toolbar.OnMenuItemClickListener {

	companion object {

		private const val SAVED_FRAGMENT = "saved_fragment"
		const val FIRST_RUN = "first_run"
		const val SHOW_NOTIFICATION_ACTION = "show_notification_action"
		const val MESSAGING_NOTIFICATION_ACTION = "messaging_notification_action"
	}

	private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
	private val notificationReceiver = NotificationReceiver()
	private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (it) {
			// given that the permission has been granted, clear the heuristic from persistent storage
			PrefsHelper.setNotificationPermissionRationalAcknowledged(false)
		} else {
			PrefsHelper.setShowPersistentNotif(false)
		}
	}

	private val appUpdateResult = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
		if (it.resultCode != RESULT_OK) {
			DialogHelper.newUpdateRequired(this) { checkAppUpdate() }
		}
	}

	var currentFragment: BaseFragmentInterface = MainFragment()


	override fun initializeBinding(): ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)

	//region Lifecycle
	override fun onCreate(savedInstanceState: Bundle?) {
		Firebase.crashlytics.log("MainActivity onCreate")
		super.onCreate(savedInstanceState)

		//update servers list at app start
		if (!intent.getBooleanExtra(FIRST_RUN, false))
			lifecycleScope.launch(Dispatchers.Default) { loadServersFromServer() }

		//check for notifications permission
		when {
			isNotificationsPermissionGranted() -> {
				//do nothing
			}

			shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
				DialogHelper.notificationPermission(this) {
					PrefsHelper.setNotificationPermissionRationalAcknowledged(true)
					requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
				}

			else -> requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
		}

		//		AdsManager.testMediation(this)
		checkAppUpdate()

		binding.toolbar.inflateMenu(R.menu.options_menu)
		binding.toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24)
		binding.toolbar.setOnMenuItemClickListener(this)
		binding.toolbar.setNavigationOnClickListener {
			binding.drawerLayout.openDrawer(GravityCompat.START, true)
		}

		//menu items with actionView set won't receive item click events, need to set it manually
		with(binding.toolbar.menu.findItem(R.id.menu_notifications)) {
			actionView?.setOnClickListener { onMenuItemClick(this) }
		}
		with(binding.toolbar.menu.findItem(R.id.menu_vip)) {
			actionView?.setOnClickListener { onMenuItemClick(this) }
		}

		AdsManager.instance.deliverContent(this)

		val drawerToggle =
			ActionBarDrawerToggle(this, binding.drawerLayout, null, R.string.open_drawer, R.string.close_drawer)

		binding.drawerLayout.addDrawerListener(drawerToggle)
		drawerToggle.syncState()

		binding.navView.setCheckedItem(R.id.nav_home)
		binding.navView.setNavigationItemSelectedListener(this)
		binding.navView.getHeaderView(0).findViewById<TextView>(R.id.version).text =
			getString(R.string.version_1_s, BuildConfig.VERSION_NAME)

		binding.navView.menu.findItem(R.id.nav_servers).icon =
			IconicsDrawable(this, FontAwesome.Icon.faw_server).apply {
				colorInt = getColorCompat(R.color.primary)
				sizeDp = 24
			}

		if (savedInstanceState == null) {
			Timber.d("Adding main fragment")
			showMainScreen()

			if (intent != null && intent.action == SHOW_NOTIFICATION_ACTION) {
				val id = intent.getLongExtra(NotificationActionBroadcast.NOTIFICATION_ID, -1)
				changeScreen(NotificationsFragment(), false)
				NotificationManagerCompat.from(this).cancel(id.toInt())
			}
		} else {
			val fragment =
				supportFragmentManager.getFragment(savedInstanceState, SAVED_FRAGMENT) as BaseFragmentInterface
			Timber.d("Saved fragment = ${fragment.TAG}")
			changeScreen(fragment, false)
		}
	}

	override fun onMenuItemClick(item: MenuItem?): Boolean {
		return when (item?.itemId) {
			R.id.menu_purchase -> {
				changeScreen(SubscriptionFragment(), false)
				true
			}

			R.id.menu_notifications -> {
				changeScreen(NotificationsFragment(), true)
				true
			}

			R.id.menu_vip -> {
				Toast.makeText(this, R.string.thank_you, Toast.LENGTH_SHORT).show()
				true
			}

			else -> false
		}
	}

	private suspend fun loadServersFromServer() {
		try {
			val serversResponse = NetworkCaller.getApi(this).getServers()
			val servers = serversResponse.body()
			if (serversResponse.isSuccessful && servers != null) {
				withContext(Dispatchers.IO) {
					AppDB.getInstance(this@MainActivity).serversDao().also {
						it.deleteAll()
						it.insert(servers)
					}
					ShortcutsHelper.removeNonExistent(this@MainActivity, servers)
				}
			} else throw Exception("Error while loading servers list")
		} catch (e: Exception) {
			Timber.w(e)
			Timber.d("Retrying to load servers")
			delay(20000)
			loadServersFromServer()
		}
	}

	fun updateNotificationsCount() = lifecycleScope.launch(Dispatchers.IO) {
		val notificationsCount = AppDB.getInstance(this@MainActivity).notificationsDao().getUnreadNotificationsCount()
		withContext(Dispatchers.Main) { setNotificationsCount(notificationsCount) }
	}

	fun setNotificationsCount(count: Int) = lifecycleScope.launch(Dispatchers.Main) {
		binding.toolbar.menu.findItem(R.id.menu_notifications)
			.actionView
			?.findViewById<TextView>(R.id.notifications_count)
			?.text = count.toString()
	}

	override fun showNativeAd(nativeAd: NativeAd) {
		Timber.d("showNativeAd")
		(supportFragmentManager.findFragmentByTag(MainFragment.STATIC_TAG) as MainFragment?)?.showNativeAd(nativeAd)
	}

	override fun hideNativeAd() {
		Timber.d("hideNativeAd")
		(supportFragmentManager.findFragmentByTag(MainFragment.STATIC_TAG) as MainFragment?)?.hideNativeAd()
	}

	override fun updateServersList() {
		Timber.d("updateServersList")

		if (!runningOnTV)
			return

		(supportFragmentManager.findFragmentByTag(ServersFragment.STATIC_TAG) as ServersFragment?)?.updateServersList()
	}

	fun switchToMainScreen(args: Bundle? = null, promptDisconnect: Boolean = false) =
		with(supportFragmentManager.findFragmentByTag(MainFragment.STATIC_TAG) as MainFragment?) {
			Timber.d("switchToMainScreen this=${this?.TAG}")
			if (this == null) {
				showMainScreen(args)
			} else {
				val bundle = args ?: Bundle()
				bundle.putBoolean(MainFragment.PROMPT_DISCONNECT, promptDisconnect)
				arguments = bundle
				if (runningOnTV && currentFragment is MainFragment)
					(currentFragment as MainFragment).onServerSelected()
				else
					changeScreen(this, false)
			}
		}

	private fun showMainScreen(arguments: Bundle? = null) {
		Timber.d("showMainScreen")
		val transaction = supportFragmentManager.beginTransaction()
		val mainFragment = MainFragment()
		mainFragment.arguments = arguments
		currentFragment = mainFragment
		binding.toolbar.title = getString(currentFragment.getPageTitle())
		transaction.add(R.id.fragment_container, currentFragment as MainFragment, currentFragment.toString())
		if (runningOnTV)
			transaction.add(R.id.fragment_side, ServersFragment(), ServersFragment.STATIC_TAG)
		transaction.addToBackStack(null)
			.commit()
	}

	fun changeScreen(newFragment: BaseFragmentInterface, showAd: Boolean) {
		Timber.d("changeScreen start")

		if (showAd)
			showInterstitialAd()

		if (currentFragment == newFragment)
			return

		currentFragment = newFragment
		val title = currentFragment.getPageTitle()
		binding.toolbar.title = getString(title)

		if (runningOnTV) {
			if (currentFragment is MainFragment) {
				binding.fragmentSide?.visible()
				binding.guidelineMiddle?.visible()
				changeGuideLine(0.4f)
			} else {
				binding.fragmentSide?.gone()
				binding.guidelineMiddle?.gone()
				changeGuideLine(0f)
			}
		}

		supportFragmentManager.beginTransaction()
			.replace(R.id.fragment_container, currentFragment as Fragment, currentFragment.toString())
			.commit()
	}

	private fun changeGuideLine(percent: Float) =
		with(binding.guidelineMiddle?.layoutParams as ConstraintLayout.LayoutParams) {
			guidePercent = percent
			binding.guidelineMiddle?.layoutParams = this
		}

	override fun onStart() {
		super.onStart()
		//prevent showing ad on app first start
		if (PrefsHelper.isFirstRun() || SubscriptionManager.getInstance().isSubscribed || runningOnTV)
			return

		AdsManager.instance.showAppOpenAd(this)
	}

	override fun onResume() {
		super.onResume()

		//prevent showing ad on app first start
		if (PrefsHelper.isFirstRun() || SubscriptionManager.getInstance().isSubscribed || runningOnTV)
			return

		AdsManager.instance.checkAndShowConsentForm(this)

		val intentFilter = IntentFilter()
		intentFilter.addAction(MESSAGING_NOTIFICATION_ACTION)
		LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver, intentFilter)

		updateNotificationsCount()

		appUpdateManager.appUpdateInfo.addOnSuccessListener {
			if (it.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
				appUpdateManager.startUpdateFlowForResult(
					it,
					appUpdateResult,
					AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
				)
			}
		}
	}

	override fun onPause() {
		super.onPause()
		Timber.d("MainActivity onPause")
		LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)

		Timber.d("MainActivity onSaveInstanceState")
		supportFragmentManager.putFragment(outState, SAVED_FRAGMENT, currentFragment as Fragment)
	}

	override fun onBackPressAction() {
		when {
			binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> binding.drawerLayout.closeDrawer(GravityCompat.START)
			currentFragment is MainFragment -> finish()
			else -> backFromAllowedAppsFragment()
		}
	}

	override fun onDestroy() {
		Timber.d("MainActivity onDestroy")
		AdsManager.instance.destroy()
		super.onDestroy()
	}
	//endregion

	override fun showPurchase() {
		Timber.d("MainActivity showPurchase")
		binding.toolbar.menu.findItem(R.id.menu_vip).isVisible = false
		binding.toolbar.menu.findItem(R.id.menu_purchase).isVisible = true
		binding.navView.menu.findItem(R.id.nav_purchase).isVisible = true
	}

	override fun hidePurchase(showVIP: Boolean) {
		Timber.d("MainActivity hidePurchase")
		binding.toolbar.menu.findItem(R.id.menu_vip).isVisible = showVIP
		binding.toolbar.menu.findItem(R.id.menu_purchase).isVisible = false
		binding.navView.menu.findItem(R.id.nav_purchase).isVisible = false
	}

	fun updateLocale(locale: Locale) = localeDelegate.setLocale(this, locale)

	fun onPurchaseSuccess() {
		Toast.makeText(this, R.string.purchase_success, Toast.LENGTH_SHORT).show()
		AdsManager.instance.deliverContent(this)
		switchToMainScreen()
		(supportFragmentManager.findFragmentByTag(ServersFragment.STATIC_TAG) as ServersFragment?)?.hideBanner()
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		val newFragment: Any = when (item.itemId) {
			R.id.nav_servers -> ServersFragment()
			R.id.nav_allowed_apps -> AllowedAppsFragment()
			R.id.nav_settings -> PreferencesFragment()
			R.id.nav_privacy -> getDisplayFragment(DisplayFragment.DisplayType.PRIVACY_POLICY)
			R.id.nav_terms -> getDisplayFragment(DisplayFragment.DisplayType.TERMS)
			R.id.nav_share -> Utils.shareApp(this)
			R.id.nav_rate -> Utils.openPlayStoreAppPage(this)
			R.id.nav_purchase -> SubscriptionFragment()
			R.id.nav_notifications -> NotificationsFragment()
			else -> MainFragment()
		}
		binding.drawerLayout.closeDrawer(GravityCompat.START, true)

		if (newFragment is BaseFragmentInterface)
			changeScreen(newFragment, true)

		return true
	}

	private fun getDisplayFragment(type: String): DisplayFragment<MainActivity> {
		val fragment = DisplayFragment<MainActivity>()
		val args = Bundle()
		args.putString(DisplayFragment.TYPE, type)
		fragment.arguments = args
		return fragment
	}

	private fun backFromAllowedAppsFragment() {
		if (currentFragment is AllowedAppsFragment)
			switchToMainScreen(promptDisconnect = (currentFragment as AllowedAppsFragment).hasConfigChanged())
		else
			switchToMainScreen()
	}

	private fun checkAppUpdate() {
		appUpdateManager.appUpdateInfo.addOnSuccessListener {
			if (it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
			    && it.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
			) {
				appUpdateManager.startUpdateFlowForResult(
					it,
					appUpdateResult,
					AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
				)
			}
		}
	}

	fun isNotificationsPermissionGranted() =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
			this, Manifest.permission.POST_NOTIFICATIONS
		) == PackageManager.PERMISSION_GRANTED

	inner class NotificationReceiver : BroadcastReceiver() {

		override fun onReceive(context: Context?, intent: Intent?) {
			Timber.d("Notification broadcast received")
			if (intent?.action == MESSAGING_NOTIFICATION_ACTION) {
				updateNotificationsCount()

				if (currentFragment is NotificationsFragment)
					(currentFragment as NotificationsFragment).updateNotifications()
			}
		}
	}
}