package com.iyftv.app.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Where the viewer left off in one title. One row per title. */
@Entity(tableName = "watch_history")
data class WatchRecord(
    @PrimaryKey val videoKey: String,
    val title: String,
    val imageUrl: String?,
    val episodeKey: String,
    val episodeName: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    /** Near the end counts as finished, so resume restarts instead. */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        const val FINISHED_MARGIN_MS = 60_000L
    }
}
