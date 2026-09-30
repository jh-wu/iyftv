package com.iyftv.app.data.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<WatchRecord>>

    @Query("SELECT * FROM watch_history WHERE videoKey = :videoKey")
    suspend fun get(videoKey: String): WatchRecord?

    @Query("SELECT * FROM watch_history WHERE videoKey = :videoKey")
    fun observe(videoKey: String): Flow<WatchRecord?>

    @Upsert
    suspend fun upsert(record: WatchRecord)

    @Query("DELETE FROM watch_history WHERE videoKey = :videoKey")
    suspend fun delete(videoKey: String)
}
