package com.pilltack.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: String = "Tablet",
    val strength: String = "",
    val unit: String = "mg",
    val shape: Int = 0,
    val colorLeft: Int,
    val colorRight: Int,
    val colorBg: Int,
    val notes: String = "",
    val scheduleType: String = "DAILY",   // DAILY, WEEKDAYS, DATES, AS_NEEDED
    val weekdays: Int = 0,                // bit0 = Monday ... bit6 = Sunday
    val dates: String = "",               // csv of epoch days
    val times: String = "08:00",          // csv of HH:mm
    val doseAmount: Int = 1,
    val startDate: Long,                  // epoch day
    val durationType: String = "ONGOING", // ONGOING, DAYS, END_DATE, DOSES
    val durationDays: Int = 0,
    val endDate: Long = 0,
    val totalDoses: Int = 0,
    val status: String = "ACTIVE",        // ACTIVE, PAUSED, COMPLETED
    val pillsLeft: Int = -1,
    val refillAt: Int = -1,
    val activeFrom: Long                  // millis; schedule counts from here (set on create/edit/reactivate)
)

@Entity(tableName = "doses", indices = [Index(value = ["medicineId", "scheduledAt"], unique = true)])
data class Dose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val scheduledAt: Long,
    val status: String = "PENDING",       // PENDING, TAKEN, SKIPPED, MISSED
    val actualAt: Long = 0,
    val medName: String,                  // snapshot so history survives edits/deletes
    val medDetail: String = ""
)

@Dao
interface MedicineDao {
    @Insert suspend fun insert(m: Medicine): Long
    @Update suspend fun update(m: Medicine)
    @Delete suspend fun delete(m: Medicine)
    @Query("SELECT * FROM medicines ORDER BY id DESC") fun all(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicines") suspend fun allNow(): List<Medicine>
    @Query("SELECT * FROM medicines WHERE id = :id") suspend fun get(id: Long): Medicine?
}

@Dao
interface DoseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(d: Dose): Long
    @Update suspend fun update(d: Dose)
    @Query("SELECT * FROM doses WHERE id = :id") suspend fun get(id: Long): Dose?
    @Query("SELECT * FROM doses WHERE medicineId = :mid AND scheduledAt = :t") suspend fun find(mid: Long, t: Long): Dose?
    @Query("SELECT COUNT(*) FROM doses WHERE medicineId = :mid AND scheduledAt >= :from") suspend fun countFor(mid: Long, from: Long): Int
    @Query("SELECT COUNT(*) FROM doses WHERE medicineId = :mid AND status = 'PENDING'") suspend fun pendingFor(mid: Long): Int
    @Query("SELECT * FROM doses WHERE status = 'PENDING' AND scheduledAt < :before") suspend fun stalePending(before: Long): List<Dose>
    @Query("SELECT * FROM doses ORDER BY scheduledAt DESC") fun allFlow(): Flow<List<Dose>>
    @Query("SELECT * FROM doses ORDER BY scheduledAt") suspend fun allNow(): List<Dose>
    @Query("DELETE FROM doses") suspend fun clear()
}

@Database(entities = [Medicine::class, Dose::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun meds(): MedicineDao
    abstract fun doses(): DoseDao
}
