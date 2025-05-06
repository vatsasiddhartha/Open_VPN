package com.dzboot.ovpn.base

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.addCallback
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.viewbinding.ViewBinding
import com.dzboot.ovpn.R
import com.dzboot.ovpn.helpers.PrefsHelper
import com.dzboot.ovpn.helpers.ThemeHelper
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.ktx.setCustomKeys
import com.google.firebase.ktx.Firebase
import com.zeugmasolutions.localehelper.LocaleHelper
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegateImpl
import timber.log.Timber


abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {

	protected val localeDelegate = LocaleHelperActivityDelegateImpl()
	protected val binding: T by lazy { initializeBinding() }

	abstract fun initializeBinding(): T
	abstract fun onBackPressAction()


	override fun attachBaseContext(newBase: Context) {
		super.attachBaseContext(localeDelegate.attachBaseContext(newBase))
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (isLauncherActivity()) {
			Timber.d("Installing SplashScreen")
			installSplashScreen()
		}

		ThemeHelper.applyTheme(PrefsHelper.getDisplayMode())
		localeDelegate.onCreate(this)

		//issue with Splash Screen API on some Android TV devices
		try {
			setContentView(binding.root)
		} catch (exception: IllegalStateException) {
			setTheme(R.style.AppTheme_Main)
			setContentView(binding.root)

			Firebase.crashlytics.setCustomKeys {
				key("runningOnTV_key", BaseApplication.runningOnTV)
			}
			Firebase.crashlytics.log("Crashed on setContentView")
			Firebase.crashlytics.recordException(exception)
		}

		onBackPressedDispatcher.addCallback(this) { onBackPressAction() }
	}

	override fun getApplicationContext() = localeDelegate.getApplicationContext(super.getApplicationContext())
	override fun getDelegate() = localeDelegate.getAppCompatDelegate(super.getDelegate())
	fun getDrawableCompat(@DrawableRes resId: Int) = ResourcesCompat.getDrawable(resources, resId, theme)
	fun getColorCompat(@ColorRes resId: Int) = ResourcesCompat.getColor(resources, resId, theme)

	override fun onResume() {
		super.onResume()
		localeDelegate.onResumed(this)
	}

	override fun onPause() {
		super.onPause()
		localeDelegate.onPaused()
	}

	override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
		return LocaleHelper.onAttach(super.createConfigurationContext(overrideConfiguration))
	}

	private fun isLauncherActivity() =
		intent?.categories?.contains(Intent.CATEGORY_LAUNCHER) == true && intent?.action == Intent.ACTION_MAIN
}