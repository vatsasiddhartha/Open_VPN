package com.dzboot.ovpn.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dzboot.ovpn.data.models.Notification
import com.dzboot.ovpn.data.models.Server


@Database(entities = [Server::class, Notification::class], version = 8, exportSchema = false)
abstract class AppDB : RoomDatabase() {

    abstract fun serversDao(): ServersDao
    abstract fun notificationsDao(): NotificationsDao

    companion object {

        private const val DATABASE = "servers"

        // For Singleton instantiation
        @Volatile
        private var instance: AppDB? = null

        fun getInstance(context: Context): AppDB {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDB::class.java, DATABASE)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}