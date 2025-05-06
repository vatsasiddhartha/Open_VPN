package com.dzboot.ovpn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dzboot.ovpn.data.models.Server


@Dao
interface ServersDao {

    @Query("SELECT * FROM servers ORDER BY connectedDevices, ping ASC LIMIT 1")
    suspend fun getLeastLoadedServer(): Server

    @Query("SELECT * FROM servers WHERE isFree ORDER BY connectedDevices, ping ASC LIMIT 1")
    suspend fun getLeastLoadedFreeServer(): Server

    @Query("SELECT * FROM servers ORDER BY ping, connectedDevices ASC LIMIT 1")
    suspend fun getNearestServer(): Server

    @Query("SELECT * FROM servers WHERE isFree ORDER BY ping, connectedDevices ASC LIMIT 1")
    suspend fun getNearestFreeServer(): Server

    @Query("SELECT * FROM servers WHERE id IN (SELECT id FROM servers ORDER BY RANDOM() LIMIT 1)")
    suspend fun getRandomServer(): Server

    @Query("SELECT * FROM servers WHERE isFree AND id IN (SELECT id FROM servers ORDER BY RANDOM() LIMIT 1)")
    suspend fun getRandomFreeServer(): Server

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServer(id: Int): Server?

    @Query("SELECT * FROM servers ORDER BY `order` ASC")
    suspend fun getAll(): List<Server>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(servers: List<Server>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: Server)

    @Query("DELETE FROM servers WHERE 1")
    suspend fun deleteAll()
}