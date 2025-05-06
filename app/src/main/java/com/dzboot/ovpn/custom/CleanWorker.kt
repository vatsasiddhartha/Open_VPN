package com.dzboot.ovpn.custom

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import timber.log.Timber


class CleanWorker(private val appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork() = try {
        appContext.cacheDir.deleteRecursively()
        appContext.externalCacheDirs.forEach { it.deleteRecursively() }
        Result.success()
    } catch (exception: Exception) {
        Timber.w(exception, "Error cleaning cache")
        Result.failure()
    }
}