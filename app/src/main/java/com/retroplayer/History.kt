package com.retroplayer

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One counted play of a song. */
@Entity(tableName = "plays")
data class Play(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long,
)

@Dao
interface PlayDao {
    @Insert
    suspend fun insert(play: Play)

    /** Song IDs played since [since], most played first. */
    @Query(
        "SELECT songId FROM plays WHERE playedAt >= :since " +
            "GROUP BY songId ORDER BY COUNT(*) DESC, MAX(playedAt) DESC"
    )
    fun mostPlayedSince(since: Long): Flow<List<Long>>

    /** Distinct song IDs, most recently played first. */
    @Query("SELECT songId FROM plays GROUP BY songId ORDER BY MAX(playedAt) DESC")
    fun recentlyPlayed(): Flow<List<Long>>
}

@Database(entities = [Play::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playDao(): PlayDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "history.db")
                .build()
                .also { instance = it }
        }
    }
}
