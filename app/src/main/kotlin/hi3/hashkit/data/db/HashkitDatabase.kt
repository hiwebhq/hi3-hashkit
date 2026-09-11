package hi3.hashkit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MinerEntity::class,
        MinerAddressEntity::class,
        TelemetrySampleEntity::class,
        RawResponseEntity::class,
        AlertEventEntity::class,
        AlertStateEntity::class,
        AuditEventEntity::class,
        ScheduleEntity::class,
        TelemetryHourlyEntity::class,
        FarmEntity::class,
        MaintenanceNoteEntity::class,
        RuleEntity::class,
        SavedPoolEntity::class,
        LogLineEntity::class,
        TuneSweepEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class HashkitDatabase : RoomDatabase() {
    abstract fun minerDao(): MinerDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun alertDao(): AlertDao
    abstract fun auditDao(): AuditDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun hourlyDao(): HourlyDao
    abstract fun farmDao(): FarmDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun ruleDao(): RuleDao
    abstract fun savedPoolDao(): SavedPoolDao
    abstract fun logDao(): LogDao
    abstract fun tuneSweepDao(): TuneSweepDao

    companion object {
        /** v15 -> v16: tune_sweeps table for the persistent tuning optimizer (additive). */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tune_sweeps` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minerId` INTEGER NOT NULL, `sweepStartEpochMs` INTEGER NOT NULL, " +
                        "`atEpochMs` INTEGER NOT NULL, `frequencyMhz` INTEGER NOT NULL, " +
                        "`voltageMv` INTEGER NOT NULL, `hashrateGhs` REAL, `powerW` REAL, " +
                        "`efficiencyJTh` REAL, `chipTempC` REAL, `overTemp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tune_sweeps_minerId_atEpochMs` " +
                        "ON `tune_sweeps` (`minerId`, `atEpochMs`)"
                )
            }
        }

        /** v1 -> v2: additive alert/audit tables; existing telemetry history untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alert_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minerId` INTEGER NOT NULL, `minerName` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `message` TEXT NOT NULL, " +
                        "`raisedAtEpochMs` INTEGER NOT NULL, `resolvedAtEpochMs` INTEGER, " +
                        "`acknowledged` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alert_events_minerId_raisedAtEpochMs` " +
                        "ON `alert_events` (`minerId`, `raisedAtEpochMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alert_events_acknowledged` " +
                        "ON `alert_events` (`acknowledged`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alert_states` (" +
                        "`minerId` INTEGER PRIMARY KEY NOT NULL, " +
                        "`consecutiveFailures` INTEGER NOT NULL, `wasOffline` INTEGER NOT NULL, " +
                        "`previousUptimeS` INTEGER, `previousPoolUrl` TEXT, " +
                        "`previousBestDifficulty` REAL, `activeTypesCsv` TEXT NOT NULL, " +
                        "`lastNotifiedJson` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minerId` INTEGER NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                        "`action` TEXT NOT NULL, `previousJson` TEXT NOT NULL, " +
                        "`appliedJson` TEXT NOT NULL, `outcome` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_audit_events_minerId_atEpochMs` " +
                        "ON `audit_events` (`minerId`, `atEpochMs`)"
                )
            }
        }

        /** v2 -> v3: miner-reported network difficulty column (Canaan support). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `telemetry_samples` ADD COLUMN `networkDifficulty` REAL")
            }
        }

        /** v5 -> v6: hourly downsampled telemetry (additive). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `telemetry_hourly` (" +
                        "`minerId` INTEGER NOT NULL, `hourStartEpochMs` INTEGER NOT NULL, " +
                        "`samples` INTEGER NOT NULL, `onlineSamples` INTEGER NOT NULL, " +
                        "`avgHashrateGhs` REAL, `minHashrateGhs` REAL, `maxHashrateGhs` REAL, " +
                        "`avgPowerW` REAL, `avgChipTempC` REAL, `maxChipTempC` REAL, " +
                        "`maxVrTempC` REAL, `energyWh` REAL, " +
                        "PRIMARY KEY(`minerId`, `hourStartEpochMs`))"
                )
            }
        }

        /** v4 -> v5: per-miner alert override columns (additive). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `alertHashBelowPct` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `alertChipTempC` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `alertVrTempC` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `alertRejectPct` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `alertsMuted` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 -> v7: farms/sites table + miners.farmId (additive; existing miners stay unassigned). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `farms` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, " +
                        "`subnetsCsv` TEXT NOT NULL, `notes` TEXT, " +
                        "`createdAtEpochMs` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `farmId` INTEGER DEFAULT NULL")
            }
        }

        /** v7 -> v8: per-farm foreground refresh interval (additive; defaults to 15s). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `farms` ADD COLUMN `refreshIntervalMs` INTEGER NOT NULL DEFAULT 15000")
            }
        }

        /** v8 -> v9: per-miner smart-plug safety-cutoff columns (additive). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `plugType` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `plugHost` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `plugOnUrl` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `plugOffUrl` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `plugCutoffTempC` REAL DEFAULT NULL")
            }
        }

        /** v9 -> v10: per-miner encrypted credential for authenticated controls (additive). */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `miners` ADD COLUMN `credentialEnc` TEXT DEFAULT NULL")
            }
        }

        /** v10 -> v11: per-chain health JSON on telemetry samples (additive). */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `telemetry_samples` ADD COLUMN `perChainJson` TEXT DEFAULT NULL")
            }
        }

        /** v14 -> v15: log_lines capture buffer (additive). */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_lines` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minerId` INTEGER NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                        "`severity` TEXT NOT NULL, `category` TEXT NOT NULL, `text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_log_lines_minerId_atEpochMs` " +
                        "ON `log_lines` (`minerId`, `atEpochMs`)"
                )
            }
        }

        /** v13 -> v14: saved_pools address book (additive). */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_pools` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`port` INTEGER NOT NULL, `worker` TEXT NOT NULL)"
                )
            }
        }

        /** v12 -> v13: rules table (additive). */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                        "`conditionType` TEXT NOT NULL, `threshold` REAL, " +
                        "`actionType` TEXT NOT NULL, `targetGroup` TEXT, " +
                        "`minIntervalMinutes` INTEGER NOT NULL, " +
                        "`lastFiredAtEpochMs` INTEGER, `lastResult` TEXT)"
                )
            }
        }

        /** v11 -> v12: maintenance_notes table (additive). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `maintenance_notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minerId` INTEGER NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, `photoPath` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_notes_minerId_atEpochMs` " +
                        "ON `maintenance_notes` (`minerId`, `atEpochMs`)"
                )
            }
        }

        /** v3 -> v4: schedules table (additive). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                        "`actionType` TEXT NOT NULL, `paramsJson` TEXT NOT NULL, " +
                        "`targetMinerIdsCsv` TEXT NOT NULL, `targetGroup` TEXT, " +
                        "`timeMinutesOfDay` INTEGER NOT NULL, `daysOfWeekCsv` TEXT NOT NULL, " +
                        "`minIntervalMinutes` INTEGER NOT NULL, " +
                        "`lastRunAtEpochMs` INTEGER, `lastResult` TEXT)"
                )
            }
        }
    }
}
