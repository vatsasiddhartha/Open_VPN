package com.dzboot.ovpn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dzboot.ovpn.data.models.Notification


@Dao
interface NotificationsDao {

    @Query("SELECT * FROM notifications ORDER BY receiveTime DESC")
    suspend fun getAll(): List<Notification>

    @Query("SELECT COUNT(id) FROM notifications WHERE read=0")
    suspend fun getUnreadNotificationsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification): Long

    @Query("UPDATE notifications SET read=1 WHERE 1")
    suspend fun setAllRead()

    @Query("DELETE FROM notifications WHERE id=:id")
    suspend fun deleteNotification(id: Long)

}