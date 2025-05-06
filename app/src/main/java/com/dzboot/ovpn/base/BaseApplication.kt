package com.dzboot.ovpn.base

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.core.content.getSystemService
import androidx.multidex.MultiDex
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.custom.CleanWorker
import com.dzboot.ovpn.helpers.NotificationsHelper
import com.google.firebase.messaging.FirebaseMessaging
import com.zeugmasolutions.localehelper.LocaleHelper
import com.zeugmasolutions.localehelper.LocaleHelperApplicationDelegate
import fr.bipi.tressence.file.FileLoggerTree
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit


abstract class BaseApplication<T : BaseActivity<*>>(
    private val mainActivityClass: Class<T>,
    private val enableStrictMode: Boolean,
    private val logToFile: Boolean
) : Application() {

    companion object {
        lateinit var instance: Application
        var runningOnTV: Boolean = false
    }


    //region LocaleHelper
    private val localeAppDelegate = LocaleHelperApplicationDelegate()

    override fun attachBaseContext(baseContext: Context) {
        super.attachBaseContext(localeAppDelegate.attachBaseContext(baseContext))
        MultiDex.install(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localeAppDelegate.onConfigurationChanged(this)
    }

    override fun getApplicationContext(): Context = LocaleHelper.onAttach(super.getApplicationContext())
    //endregion

    override fun onCreate() {
        super.onCreate()

        instance = this
        runningOnTV = getSystemService<UiModeManager>()?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        //clean app cache every two days
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "clean_cache",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CleanWorker>(2, TimeUnit.DAYS).build()
            )

        if (BuildConfig.DEBUG) {
            if (enableStrictMode)
                enableStrictMode()

            Timber.plant(Timber.DebugTree())
            FirebaseMessaging.getInstance().subscribeToTopic("debug")
        } else {
            FirebaseMessaging.getInstance().subscribeToTopic("all")
        }

        if (logToFile)
            plantLogToFile()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            NotificationsHelper.createNotificationChannels(this)

        NotificationsHelper.showPersistentNotification(this, mainActivityClass)
    }

    private fun plantLogToFile() {
        val logDir = File(getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdir()
        }
        val t = FileLoggerTree.Builder()
            .withDir(logDir)
            .withFileName("zvpn.log")
            .withSizeLimit(1000000)
            .withFileLimit(1)
            .withMinPriority(Log.DEBUG)
            .appendToFile(true)
            .build()
        Timber.plant(t)
    }

    private fun enableStrictMode() {
        val policy = StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyLog()
            //            .penaltyDeath()
            .build()
        StrictMode.setVmPolicy(policy)
    }
}