package com.dzboot.ovpn.helpers

import com.dzboot.ovpn.data.models.Server


fun ArrayList<Server>.findIndex(serverId: Int): Int {
    for (i in indices) {
        if (get(i).id == serverId)
            return i
    }
    return 0
}