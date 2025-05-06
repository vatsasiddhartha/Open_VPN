package com.dzboot.ovpn

import android.widget.Toast
import com.dzboot.ovpn.activities.MainActivity
import com.dzboot.ovpn.base.BaseApplication
import com.dzboot.ovpn.constants.Constants
import com.dzboot.ovpn.helpers.AdsManager


class Application : BaseApplication<MainActivity>(MainActivity::class.java, false, false) {

    override fun onCreate() {
        super.onCreate()

        if (Constants.isNotSet()) {
            Toast.makeText(this, "App not configured", Toast.LENGTH_SHORT).show()
            throw RuntimeException("Config file is not set. Please follow tutorial before running app")
        }

        AdsManager.init(this)
    }
}