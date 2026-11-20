package org.suraksha.kavach.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.suraksha.core.Level
import org.suraksha.core.ReasonCode
import org.suraksha.core.ScanResult
import org.suraksha.core.Verdict

/** Deliberately has exactly four metadata fields. No message, URL, sender or file name. */
@Entity(tableName = "activity", primaryKeys = ["domain", "timestamp"], indices = [Index("timestamp")])
data class HistoryEntry(val level: String, val domain: String, val timestamp: Long, val reasonCodes: String) {
    fun toResult(): ScanResult {
        val reasons = reasonCodes.split(',').mapNotNull { runCatching { ReasonCode.valueOf(it) }.getOrNull() }.toSet()
        val parsed = runCatching { Level.valueOf(level) }.getOrDefault(Level.SUSPICIOUS)
        return ScanResult(domain, Verdict(parsed, 0.0, reasons), timestamp)
    }
    companion object {
        fun from(result: ScanResult) = HistoryEntry(result.verdict.level.name, result.domain.take(253),
            result.timestamp, result.verdict.reasonCodes.sortedBy { it.name }.joinToString(",") { it.name })
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM activity ORDER BY timestamp DESC LIMIT 500")
    fun observe(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entries: List<HistoryEntry>)

    @Query("DELETE FROM activity WHERE timestamp < :cutoff")
    suspend fun deleteBefore(cutoff: Long)

    @Query("DELETE FROM activity WHERE rowid NOT IN (SELECT rowid FROM activity ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trim()

    @Query("DELETE FROM activity")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM activity")
    suspend fun count(): Int
}

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    companion object {
        const val NAME = "activity.db"
        fun open(context: Context, secrets: SecretStore): HistoryDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = secrets.databaseKey()
            val factory = SupportOpenHelperFactory(passphrase.copyOf())
            passphrase.fill(0)
            return Room.databaseBuilder(context, HistoryDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build().also { it.openHelper.writableDatabase }
        }
    }
}
