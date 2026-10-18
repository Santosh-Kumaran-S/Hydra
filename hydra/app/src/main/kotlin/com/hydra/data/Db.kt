package com.hydra.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1") fun observe(): Flow<UserProfileEntity?>
    @Query("SELECT * FROM user_profile WHERE id = 1") suspend fun get(): UserProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: UserProfileEntity)
}

@Dao interface WaterDao {
    @Insert suspend fun insert(e: WaterIntakeEntity): Long
    @Update suspend fun update(e: WaterIntakeEntity)
    @Delete suspend fun delete(e: WaterIntakeEntity)
    @Query("SELECT * FROM water_intake ORDER BY atEpochMs DESC") fun observeAll(): Flow<List<WaterIntakeEntity>>
    @Query("SELECT * FROM water_intake ORDER BY atEpochMs DESC") suspend fun getAll(): List<WaterIntakeEntity>
    @Query("SELECT COUNT(*) FROM water_intake WHERE amountMl = :amt AND atEpochMs BETWEEN :from AND :to")
    suspend fun countNear(amt: Int, from: Long, to: Long): Int
}

@Dao interface ReminderDao {
    @Insert suspend fun insert(e: ReminderEventEntity): Long
    @Query("SELECT status FROM reminder_event WHERE status IN ('CONFIRMED','SNOOZED','DISMISSED','NOTIFIED')")
    fun observeActedStatuses(): Flow<List<String>>
    @Query("SELECT * FROM reminder_event") suspend fun all(): List<ReminderEventEntity>
    @Query("UPDATE reminder_event SET status=:status, snoozeCount=:snoozes, notifiedAtMs=COALESCE(:notifiedAt, notifiedAtMs), confirmedAmountMl=COALESCE(:amount, confirmedAmountMl) WHERE id=:id")
    suspend fun updateState(id: Long, status: String, snoozes: Int, notifiedAt: Long?, amount: Int?)
    @Query("UPDATE reminder_event SET status='SUPERSEDED' WHERE id=:id AND status='SCHEDULED'") suspend fun supersede(id: Long)
    @Query("UPDATE reminder_event SET status='DISMISSED' WHERE id=:id AND status='NOTIFIED'") suspend fun markDismissed(id: Long)
}

@Dao interface SensorDao {
    @Insert suspend fun insertHr(e: HeartRateReadingEntity): Long
    @Query("SELECT * FROM heart_rate_reading ORDER BY measuredAtMs DESC LIMIT 1") suspend fun latestHr(): HeartRateReadingEntity?
    @Query("SELECT * FROM heart_rate_reading ORDER BY measuredAtMs DESC LIMIT 1") fun observeLatestHr(): Flow<HeartRateReadingEntity?>
    @Query("SELECT * FROM heart_rate_reading") suspend fun allHr(): List<HeartRateReadingEntity>
    @Query("SELECT * FROM environment_reading") suspend fun allEnv(): List<EnvironmentReadingEntity>
    @Insert suspend fun insertEnv(e: EnvironmentReadingEntity): Long
    @Query("SELECT * FROM environment_reading ORDER BY observedAtMs DESC LIMIT 1") suspend fun latestEnv(): EnvironmentReadingEntity?
    @Query("SELECT * FROM environment_reading ORDER BY observedAtMs DESC LIMIT 1") fun observeLatestEnv(): Flow<EnvironmentReadingEntity?>
}

@Database(
    entities = [UserProfileEntity::class, WaterIntakeEntity::class, HeartRateReadingEntity::class,
        EnvironmentReadingEntity::class, ReminderEventEntity::class],
    version = 1, exportSchema = false,
)
abstract class HydraDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun waterDao(): WaterDao
    abstract fun reminderDao(): ReminderDao
    abstract fun sensorDao(): SensorDao
    companion object {
        fun build(ctx: Context) = Room.databaseBuilder(ctx, HydraDatabase::class.java, "hydra.db").build()
    }
}

enum class AddResult { ADDED, DUPLICATE, INVALID }

class HydraRepository(private val db: HydraDatabase) {
    val profile = db.profileDao().observe()
    val intakes = db.waterDao().observeAll()

    suspend fun saveProfile(p: UserProfileEntity) =
        db.profileDao().upsert(p.copy(updatedAt = System.currentTimeMillis()))

    suspend fun addWater(amountMl: Int, method: String, atMs: Long = System.currentTimeMillis()): AddResult {
        if (amountMl !in 1..2000) return AddResult.INVALID
        // Accidental double-tap protection: same amount within 60 s.
        if (db.waterDao().countNear(amountMl, atMs - 60_000, atMs + 60_000) > 0) return AddResult.DUPLICATE
        db.waterDao().insert(WaterIntakeEntity(amountMl = amountMl, atEpochMs = atMs, method = method))
        return AddResult.ADDED
    }

    suspend fun editAmount(e: WaterIntakeEntity, amountMl: Int): Boolean {
        if (amountMl !in 1..2000) return false
        db.waterDao().update(e.copy(amountMl = amountMl, updatedAt = System.currentTimeMillis())); return true
    }
    suspend fun delete(e: WaterIntakeEntity) = db.waterDao().delete(e)
    val latestHrFlow = db.sensorDao().observeLatestHr()
    val latestEnvFlow = db.sensorDao().observeLatestEnv()
    suspend fun latestHr() = db.sensorDao().latestHr()
    suspend fun latestEnv() = db.sensorDao().latestEnv()
    suspend fun saveHr(e: HeartRateReadingEntity) = db.sensorDao().insertHr(e)
    suspend fun saveEnv(e: EnvironmentReadingEntity) = db.sensorDao().insertEnv(e)
    val reminderStatuses = db.reminderDao().observeActedStatuses()

    /** Everything stored about the user, as JSON, for their own backup. Nothing leaves the phone unless they save/share the file. */
    suspend fun exportJson(): String {
        val root = org.json.JSONObject()
        root.put("app", "Hydra"); root.put("exportedAt", java.time.Instant.now().toString())
        profileOnce()?.let { p ->
            root.put("profile", org.json.JSONObject().put("name", p.name).put("heightCm", p.heightCm ?: org.json.JSONObject.NULL)
                .put("weightKg", p.weightKg ?: org.json.JSONObject.NULL).put("wakeMin", p.wakeMin).put("sleepMin", p.sleepMin)
                .put("quietEnabled", p.quietEnabled).put("dailyTargetMl", p.targetMl ?: org.json.JSONObject.NULL)
                .put("clinicianMinGapMin", p.clinicianMinGapMin ?: org.json.JSONObject.NULL)
                .put("baselineIntervalMin", p.baselineIntervalMin).put("zoneId", p.zoneId))
        }
        fun <T> arr(items: List<T>, f: (T) -> org.json.JSONObject) = org.json.JSONArray().also { a -> items.forEach { a.put(f(it)) } }
        fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
        root.put("waterIntake", arr(intakesOnce()) { org.json.JSONObject().put("amountMl", it.amountMl).put("at", iso(it.atEpochMs)).put("method", it.method) })
        root.put("heartRate", arr(db.sensorDao().allHr()) { org.json.JSONObject().put("bpm", it.bpm).put("measuredAt", iso(it.measuredAtMs)).put("source", it.sourceDevice) })
        root.put("environment", arr(db.sensorDao().allEnv()) {
            org.json.JSONObject().put("tempC", it.temperatureC).put("humidityPct", it.humidityPct).put("observedAt", iso(it.observedAtMs)).put("source", it.source).put("kind", it.kind) })
        root.put("reminders", arr(db.reminderDao().all()) {
            org.json.JSONObject().put("scheduledAt", iso(it.scheduledAtMs)).put("status", it.status).put("snoozeCount", it.snoozeCount)
                .put("confirmedAmountMl", it.confirmedAmountMl ?: org.json.JSONObject.NULL).put("reason", it.reason).put("algorithmVersion", it.algorithmVersion) })
        return root.toString(2)
    }
    suspend fun profileOnce() = db.profileDao().get()
    suspend fun intakesOnce() = db.waterDao().getAll()
    suspend fun newEvent(at: Long, reason: String, version: String) = db.reminderDao().insert(
        ReminderEventEntity(scheduledAtMs = at, status = "SCHEDULED", reason = reason, algorithmVersion = version))
    suspend fun supersede(id: Long) = db.reminderDao().supersede(id)
    suspend fun markDismissed(id: Long) = db.reminderDao().markDismissed(id)
    suspend fun setEventState(id: Long, status: String, notifiedAt: Long? = null, snoozes: Int = 0, amount: Int? = null) =
        db.reminderDao().updateState(id, status, snoozes, notifiedAt, amount)
    suspend fun deleteAllData() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
}
