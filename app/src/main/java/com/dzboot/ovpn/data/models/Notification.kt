package com.dzboot.ovpn.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "notifications")
class Notification {

    @PrimaryKey(autoGenerate = true)
    var id = 0L

    var title: String? = null
    var body: String? = null
    var receiveTime = 0L
    var read: Boolean = false

}