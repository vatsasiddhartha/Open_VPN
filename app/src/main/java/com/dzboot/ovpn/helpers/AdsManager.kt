package com.dzboot.ovpn.helpers

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.viewbinding.ViewBinding
import androidx.window.layout.WindowMetricsCalculator
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.base.BaseActivity
import com.dzboot.ovpn.base.BaseMainActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.*

import org.lsposed.lsparanoid.Obfuscate
import com.dzboot.ovpn.R
import timber.log.Timber


@Obfuscate
class AdsManager private constructor() : DefaultLifecycleObserver {

	private enum class AdType {
		APP_OPEN,
		SERVERS_BANNER,
		NATIVE,
		MAIN_INTERSTITIAL,
		MORE_TIME_INTERSTITIAL,
		MORE_TIME_REWARD
	}

	//reload ads retry count for exponential backoff
	private val reloadAdsRetries = HashMap<AdType, Int>()

	//flag to prevent showing InterstitialAd with AppOpenAd simultaneously
	private var isShowingAd = false
	private var showAds = false
	private var lastMoreTimeRewardTime: Long = 0
	private var appOpenAdLoadTime: Long = 0
	private var appOpenAd: AppOpenAd? = null
	private var interstitialAd: InterstitialAd? = null
	private var moreTimeInterstitialAd: InterstitialAd? = null
	private var moreTimeRewardedAd: RewardedInterstitialAd? = null
	private var nativeAd: NativeAd? = null
	private var bannerAd: AdView? = null
	private var consentForm: ConsentForm? = null

	private var showInterstitialAdCount = 0

	private fun useLocalAds() = BuildConfig.DEBUG && FORCE_TEST_ADS_WHEN_DEBUG || BuildConfig.USE_LOCAL_ADMOB_IDS

	private fun retryLoadingAd(adType: AdType, vararg args: Any) {
		return
		//        val count = reloadAdsRetries.increment(adType)
		//        Timber.d("Retrying to load ad count=$count")
		//        Handler(Looper.getMainLooper()).postDelayed({
		//            when (adType) {
		//                AdType.APP_OPEN -> fetchAppOpenAd(args[0] as Context)
		//                AdType.SERVERS_BANNER -> loadBannerAd(args[0] as BaseMainActivity<*>, args[1] as FrameLayout)
		//                AdType.NATIVE -> loadNativeAd(args[0] as BaseMainActivity<*>)
		//                AdType.MAIN_INTERSTITIAL -> loadInterstitialAd(args[0] as BaseMainActivity<*>)
		//                AdType.MORE_TIME_INTERSTITIAL -> loadMoreTimeInterstitialAd(args[0] as BaseMainActivity<*>)
		//                AdType.MORE_TIME_REWARD -> loadRewardedInterstitialAd(args[0] as BaseMainActivity<*>)
		//            }
		//        }, RELOAD_AD_PERIOD * count)
	}

	//	fun testMediation(activity: Activity) = MediationTestSuite.launch(activity)

	//region
	private fun fetchAppOpenAd(context: Context) {
		if (isAppOpenAdAvailable()) return

		val loadCallback = object : AppOpenAd.AppOpenAdLoadCallback() {
			override fun onAdLoaded(appOpenAd: AppOpenAd) {
				super.onAdLoaded(appOpenAd)
				//                Timber.d("AppOpenAd loaded successfully")
				this@AdsManager.appOpenAd = appOpenAd
				appOpenAdLoadTime = System.currentTimeMillis()
			}

			override fun onAdFailedToLoad(loadAdError: LoadAdError) {
				super.onAdFailedToLoad(loadAdError)
				//                Timber.w("AppOpenAd failed to load: %s", loadAdError.message)
				retryLoadingAd(AdType.APP_OPEN, context)
			}
		}

		//        Timber.d("Loading AppOpenAd")
		AppOpenAd.load(
			context,
			if (useLocalAds()) BuildConfig.ADMOB_APP_OPEN_KEY else PrefsHelper.getAdmobAppOpenId(),
			getAdRequest(),
			loadCallback
		)
	}

	/**
	 * Utility method to check if ad was loaded more than n hours ago.
	 */
	private fun isAppOpenAdValid() =
		System.currentTimeMillis() - appOpenAdLoadTime < 3600000 * APP_OPEN_AD_VALIDITY_HOURS

	/**
	 * Utility method that checks if ad exists and can be shown.
	 */
	private fun isAppOpenAdAvailable() = appOpenAd != null && isAppOpenAdValid()

	/**
	 * Shows the ad if one isn't already showing.
	 */
	fun showAppOpenAd(activity: BaseActivity<*>) {
		if (DISABLE_ADS)
			return

		if (showAds && !isShowingAd && isAppOpenAdAvailable()) {
			appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
				override fun onAdDismissedFullScreenContent() {
					// Set the reference to null so isAdAvailable() returns false.
					appOpenAd = null
					isShowingAd = false
					fetchAppOpenAd(activity)
				}

				override fun onAdFailedToShowFullScreenContent(adError: AdError) {
					//                    Timber.w("AppOpenAd failed to show ${adError.message}")
				}

				override fun onAdShowedFullScreenContent() {
					isShowingAd = true
				}
			}
			appOpenAd?.show(activity)
		} else {
			//            Timber.d("Can not show ad. Probably ad not available")
			fetchAppOpenAd(activity)
		}
	}

	fun deliverContent(activity: BaseMainActivity<out ViewBinding>) {
		val subscribed = SubscriptionManager.getInstance().isSubscribed
		val canSubscribe = SubscriptionManager.getInstance().isFeatureSupported()
		//        Timber.d("deliverContent subscribed=$subscribed canSubscribe=$canSubscribe")
		if (subscribed) {
			showAds = false
			appOpenAd = null
			interstitialAd = null
			moreTimeInterstitialAd = null
			moreTimeRewardedAd = null
			activity.hideNativeAd()
			activity.hidePurchase(true)
			activity.updateServersList()
		} else {
			showAds = true
			loadAds(activity)
			if (canSubscribe)
				activity.showPurchase()
			else
				activity.hidePurchase(false)
		}
	}

	private fun showResetGDPRConsent(context: Context) =
		UserMessagingPlatform.getConsentInformation(context).consentStatus != ConsentInformation.ConsentStatus.NOT_REQUIRED

	private fun resetGDPRConsent(activity: BaseActivity<out ViewBinding>) {
		UserMessagingPlatform.getConsentInformation(activity).reset()
		getGDPRConsent(activity) { checkAndShowConsentForm(activity) }
	}

	fun getGDPRConsent(activity: BaseActivity<out ViewBinding>, onFormLoadComplete: () -> Unit) {
		if (DISABLE_GDPR_CONSENT) {
			//            Timber.d("GDPR consent disabled")
			onFormLoadComplete()
			return
		}

		val paramsBuilder = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false)
		val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
		if (BuildConfig.DEBUG) {
			val debugSettings = ConsentDebugSettings.Builder(activity)
				.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
				.addTestDeviceHashedId(AdRequest.DEVICE_ID_EMULATOR)
				.addTestDeviceHashedId(TEST_DEVICE_ID_0)
				.addTestDeviceHashedId(TEST_DEVICE_ID_1)
				.build()

			paramsBuilder.setConsentDebugSettings(debugSettings)
		}

		//        Timber.d("Requesting ConsentInfo")
		consentInfo.requestConsentInfoUpdate(
			activity,
			paramsBuilder.build(), {
				//                Timber.d("ConsentInfo available=${consentInfo.isConsentFormAvailable}")
				if (consentInfo.isConsentFormAvailable)
					loadForm(activity, onFormLoadComplete)
				else {
					consentForm = null
					onFormLoadComplete()
				}
			}, {
				consentForm = null
				onFormLoadComplete()
				//                Timber.w("Failed to update ConsentInfo: ${it.message}")
			})
	}

	private fun loadForm(activity: BaseActivity<out ViewBinding>, onFormLoadComplete: () -> Unit) {
		//        Timber.d("Loading GDPR consent form")
		UserMessagingPlatform.loadConsentForm(
			activity, {
				//                Timber.d("Consent form loaded.")
				consentForm = it
				onFormLoadComplete()
			}, {
				// Consent form error. This usually happens if the user is not in the EU.
				//                Timber.e("Error loading consent form: ${it.message}")
				consentForm = null
				onFormLoadComplete()
			})
	}

	fun checkAndShowConsentForm(activity: BaseActivity<out ViewBinding>) {
		if (consentForm == null) {
			//            Timber.d("consentForm is null")
			return
		}

		//        Timber.d("checkAndShowConsentForm consentForm is not null")
		when (UserMessagingPlatform.getConsentInformation(activity).consentStatus) {
			ConsentInformation.ConsentStatus.REQUIRED -> consentForm?.show(activity) { error ->
				if (error == null)  //no error, just recheck consentStatus
					checkAndShowConsentForm(activity)
				else {
					//                    Timber.d("Show consent form error ${error.message}")
					//handle error by reloading the form
					loadForm(activity) { checkAndShowConsentForm(activity) }
				}
			}

			ConsentInformation.ConsentStatus.OBTAINED ->
				if (PrefsHelper.notConsentedToPersonalizedAds())
					DialogHelper.notConsentedToPersonalizedAds(activity) { resetGDPRConsent(activity) }
		}
	}

	private fun loadAds(activity: BaseMainActivity<out ViewBinding>) {
		if (DISABLE_ADS) {
			//            Timber.d("Ads disabled")
			return
		}

		//        Timber.d("Loading Ads")
		loadNativeAd(activity)
		loadInterstitialAd(activity)
		loadMoreTimeInterstitialAd(activity)
		loadRewardedInterstitialAd(activity)
	}

	private fun getAdRequest() = AdRequest.Builder().build()

	private fun loadInterstitialAd(activity: BaseActivity<*>) {
		//        Timber.d("Start loading InterstitialAd")
		InterstitialAd.load(
			activity,
			if (useLocalAds()) BuildConfig.ADMOB_INTERSTITIAL_KEY else PrefsHelper.getAdmobInterId(),
			getAdRequest(),
			object : InterstitialAdLoadCallback() {
				override fun onAdLoaded(ad: InterstitialAd) {
					//                    Timber.d("InterstitialAd loaded successfully")
					ad.fullScreenContentCallback = object : FullScreenContentCallback() {
						override fun onAdFailedToShowFullScreenContent(adError: AdError) {
							super.onAdFailedToShowFullScreenContent(adError)
							//                            Timber.d("Failed to show InterstitialAd ${adError.message}")
							loadInterstitialAd(activity)
						}

						override fun onAdDismissedFullScreenContent() {
							super.onAdDismissedFullScreenContent()
							isShowingAd = false
						}

						override fun onAdShowedFullScreenContent() {
							super.onAdShowedFullScreenContent()
							isShowingAd = true
							loadInterstitialAd(activity)
						}
					}
					interstitialAd = ad
				}

				override fun onAdFailedToLoad(loadAdError: LoadAdError) {
					super.onAdFailedToLoad(loadAdError)
					//                    Timber.w("InterstitialAd failed to load: ${loadAdError.message}")
					retryLoadingAd(AdType.MAIN_INTERSTITIAL, activity)
				}
			})
	}

	private fun loadMoreTimeInterstitialAd(activity: BaseActivity<*>) {
		//        Timber.d("Start loading MoreTimeInterstitialAd")
		InterstitialAd.load(
			activity,
			if (useLocalAds()) BuildConfig.ADMOB_MORE_TIME_INTERSTITIAL_KEY else PrefsHelper.getAdmobRewardedInterId(),
			getAdRequest(),
			object : InterstitialAdLoadCallback() {
				override fun onAdLoaded(ad: InterstitialAd) {
					//                    Timber.d("MoreTimeInterstitialAd loaded successfully")
					ad.fullScreenContentCallback = object : FullScreenContentCallback() {
						override fun onAdFailedToShowFullScreenContent(adError: AdError) {
							super.onAdFailedToShowFullScreenContent(adError)
							//                            Timber.d("Failed to show MoreTimeInterstitialAd ${adError.message}")
							loadMoreTimeInterstitialAd(activity)
						}

						override fun onAdDismissedFullScreenContent() {
							super.onAdDismissedFullScreenContent()
							isShowingAd = false
						}

						override fun onAdShowedFullScreenContent() {
							super.onAdShowedFullScreenContent()
							isShowingAd = true
							loadMoreTimeInterstitialAd(activity)
						}
					}
					moreTimeInterstitialAd = ad
				}

				override fun onAdFailedToLoad(loadAdError: LoadAdError) {
					super.onAdFailedToLoad(loadAdError)
					//                    Timber.w("loadMoreTimeInterstitialAd failed to load: ${loadAdError.message}")
					retryLoadingAd(AdType.MORE_TIME_INTERSTITIAL, activity)
				}
			})
	}

	private fun loadRewardedInterstitialAd(activity: BaseActivity<*>) {
		//        Timber.d("Start loading RewardedInterstitialAd")
		RewardedInterstitialAd.load(
			activity,
			if (useLocalAds()) BuildConfig.ADMOB_REWARDED_INTERSTITIAL_KEY
			else PrefsHelper.getAdmobRewardedInterId(),
			getAdRequest(), object : RewardedInterstitialAdLoadCallback() {

				override fun onAdLoaded(ad: RewardedInterstitialAd) {
					super.onAdLoaded(ad)
					//                    Timber.d("RewardedInterstitialAd loaded successfully")
					moreTimeRewardedAd = ad
				}

				override fun onAdFailedToLoad(loadAdError: LoadAdError) {
					super.onAdFailedToLoad(loadAdError)
					//                    Timber.w("RewardedInterstitialAd failed to load: ${loadAdError.message}")
					retryLoadingAd(AdType.MORE_TIME_REWARD, activity)
				}
			})
	}

	private fun loadNativeAd(activity: BaseMainActivity<out ViewBinding>) {
		if (DISABLE_ADS || !showAds) {
			//            Timber.d("Won't load NativeAd")
			return
		}

		//        Timber.d("Start loading NativeAd")
		AdLoader.Builder(
			activity,
			if (useLocalAds()) BuildConfig.ADMOB_NATIVE_KEY else PrefsHelper.getAdmobNativeId()
		)
			.forNativeAd {
				nativeAd = it
				if (activity.isDestroyed) {
					it.destroy()
					return@forNativeAd
				}

				//                Timber.d("NativeAd loaded successfully")
				if (showAds)
					activity.showNativeAd(it)
			}
			.withAdListener(object : AdListener() {
				override fun onAdFailedToLoad(loadAdError: LoadAdError) {
					super.onAdFailedToLoad(loadAdError)
					//                    Timber.w("NativeAd failed to load: ${loadAdError.message}")
					retryLoadingAd(AdType.NATIVE, activity)
				}
			})
			.withNativeAdOptions(NativeAdOptions.Builder().build())
			.build()
			.loadAd(getAdRequest())
	}

	private fun loadBannerAd(activity: BaseMainActivity<out ViewBinding>, bannerAdLayout: FrameLayout) {
		if (DISABLE_ADS || !showAds) {
			//            Timber.d("Won't load BannerAd")
			return
		}

		bannerAd = AdView(activity)
		bannerAdLayout.removeAllViews()
		bannerAdLayout.addView(bannerAd)
		bannerAd?.adUnitId =
			if (useLocalAds()) BuildConfig.ADMOB_BANNER_KEY else PrefsHelper.getAdmobBannerId()
		bannerAd?.setAdSize(getBannerAdSize(activity, bannerAdLayout))
		bannerAd?.adListener = object : AdListener() {
			override fun onAdFailedToLoad(loadAdError: LoadAdError) {
				super.onAdFailedToLoad(loadAdError)
				//                Timber.w("BannerAd failed to load: ${loadAdError.message}")
				retryLoadingAd(AdType.SERVERS_BANNER, activity, bannerAdLayout)
			}
		}

		//        Timber.d("Loading bannerAd")
		bannerAd?.loadAd(getAdRequest())
	}

	private fun getBannerAdSize(activity: AppCompatActivity, container: FrameLayout): AdSize {
		var adWidthPx = container.width
		if (adWidthPx == 0) {
			adWidthPx = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds.width()
		}

		return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
			activity,
			(adWidthPx / activity.resources.displayMetrics.density).toInt()
		)
	}

	private fun showInterstitialAd(activity: Activity) {
		if (isShowingAd)
			return

		//        Timber.d("showInterstitialAd ad=$interstitialAd")
		if (showInterstitialAdCount++ == INTERSTITIAL_ADS_FREQUENCY) {
			//only show ads once every two calls
			showInterstitialAdCount = 0
			return
		}

		interstitialAd?.show(activity)
	}

	private fun showMoreTimeRewardedAd(activity: BaseActivity<*>, onReward: () -> Unit) {
		if (moreTimeRewardedAd == null) {
			fallbackToInterstitialAd(activity, onReward)
		} else {
			moreTimeRewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
				override fun onAdDismissedFullScreenContent() {
					super.onAdDismissedFullScreenContent()
					isShowingAd = false
					loadRewardedInterstitialAd(activity)
				}

				override fun onAdFailedToShowFullScreenContent(adError: AdError) {
					super.onAdFailedToShowFullScreenContent(adError)
					//                    Timber.d("Failed to show RewardedInterstitialAd")
					fallbackToInterstitialAd(activity, onReward)
					loadRewardedInterstitialAd(activity)
				}

				override fun onAdShowedFullScreenContent() {
					super.onAdShowedFullScreenContent()
					isShowingAd = true
					loadRewardedInterstitialAd(activity)
				}
			}

			DialogHelper.moreTime(activity) {
				moreTimeRewardedAd?.show(activity) {
					moreTimeRewardedAd = null
					rewardMoreTime(onReward)
				}
			}
		}
	}

	private fun fallbackToInterstitialAd(activity: BaseActivity<*>, onReward: () -> Unit) {
		when {
			moreTimeInterstitialAd != null -> {
				moreTimeInterstitialAd?.show(activity)
				rewardMoreTime(onReward)
			}

			lastMoreTimeRewardTime >= System.currentTimeMillis() - 30_000 -> {
				Toast.makeText(activity, R.string.try_again_later, Toast.LENGTH_SHORT).show()
			}

			else -> rewardMoreTime(onReward)
		}
	}

	private fun rewardMoreTime(onReward: () -> Unit) {
		lastMoreTimeRewardTime = System.currentTimeMillis()
		onReward()
	}

	fun destroy() {
		bannerAd?.destroy()
		nativeAd?.destroy()
	}

	companion object {

		private const val DISABLE_ADS = !BuildConfig.SHOW_ADS
		private const val FORCE_TEST_ADS_WHEN_DEBUG = true
		private const val DISABLE_GDPR_CONSENT = !BuildConfig.SHOW_ADS      //disable GDPR Consent at developing stage
		private const val RELOAD_AD_PERIOD = 10000L

		//Google Play may reject app if it is showing interstitial ads too frequently, see link below:
		// https://support.google.com/googleplay/android-developer/answer/9857753?hl=en#zippy=%2Cexamples-of-common-violations
		private const val INTERSTITIAL_ADS_FREQUENCY = 2

		//OPPO test device id
		private const val TEST_DEVICE_ID_0 = "F40D8D0517577C9DFC3EDD5973A08B2B"
		private const val TEST_DEVICE_ID_1 = "1FEEE47D1EFB61D1C0FE58402170446B"

		private const val APP_OPEN_AD_VALIDITY_HOURS = 4

		//Ads test devices
		private val TEST_DEVICES = listOf(TEST_DEVICE_ID_0, TEST_DEVICE_ID_1, AdRequest.DEVICE_ID_EMULATOR)

		@Volatile
		lateinit var instance: AdsManager

		fun init(appContext: Application) {
			//            Timber.d("Initializing ads")
			PrefsHelper.setAdsInitialized(true)

			AdsManager().also {
				MobileAds.initialize(appContext) { status ->
					//                    Timber.d("AdMob initialization : size=${status.adapterStatusMap.size}")
					val configuration = RequestConfiguration.Builder()
						.setTestDeviceIds(TEST_DEVICES)
						.build()
					MobileAds.setRequestConfiguration(configuration)
				}

				instance = it
			}
		}

		private fun HashMap<AdType, Int>.increment(key: AdType) = when (val count = get(key)) {
			null -> 1
			else -> count + 1
		}.also { put(key, it) }

		//convenient overloading for less code
		fun BaseMainActivity<*>.resetGDPRConsent() = instance.resetGDPRConsent(this)
		fun BaseMainActivity<*>.showResetGDPRConsent() = instance.showResetGDPRConsent(this)
		fun BaseMainActivity<*>.showInterstitialAd() = instance.showInterstitialAd(this)
		fun BaseMainActivity<*>.showMoreTimeAd(onReward: () -> Unit) = instance.showMoreTimeRewardedAd(this, onReward)
		fun BaseMainActivity<*>.loadBannerAd(bannerAdLayout: FrameLayout) = instance.loadBannerAd(this, bannerAdLayout)
	}
}