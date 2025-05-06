package com.dzboot.ovpn.helpers

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dzboot.ovpn.R
import com.dzboot.ovpn.activities.ShortcutToServiceActivity
import com.dzboot.ovpn.base.BaseApplication
import com.dzboot.ovpn.data.models.Server
import com.flags.CountryUtils
import de.blinkt.openvpn.core.OpenVPNService


object ShortcutsHelper {

    fun add(context: Context, server: Server) {
        val intent = Intent(context, ShortcutToServiceActivity::class.java).also {
            it.action = OpenVPNService.START_VPN
            it.putExtra(OpenVPNService.SELECTED_SERVER_ID_EXTRA, server.id)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "server_${server.id}")
            .setShortLabel(context.getString(R.string.connect_to, server.countryCode.uppercase()))
            .setLongLabel(
                context.getString(
                    R.string.connect_to,
                    CountryUtils.getLocalizedNameFromCountryCode(PrefsHelper.getLanguage(), server.countryCode)
                )
            )
            .setIcon(IconCompat.createWithResource(context, server.getFlagResId(context, R.drawable.ic_auto)))
            .setIntent(intent)

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut.build())
    }

    fun remove(context: Context, id: String) = ShortcutManagerCompat.removeDynamicShortcuts(context, arrayListOf(id))

    fun isAdded(context: Context, serverId: Int): Boolean {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC).forEach {
            if (it.id == "server_$serverId")
                return true
        }
        return false
    }

    //remove deleted servers from shortcuts
    fun removeNonExistent(context: Context, servers: List<Server>) {
        val ids = servers.map { "server_${it.id}" }
        val toRemoveIds = arrayListOf<String>()
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC).forEach {
            if (!ids.contains(it.id))
                toRemoveIds.add(it.id)
        }
        toRemoveIds.remove("server_-1")
        ShortcutManagerCompat.removeDynamicShortcuts(context, toRemoveIds)
    }

    fun removeAllShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(BaseApplication.instance)
    }
}