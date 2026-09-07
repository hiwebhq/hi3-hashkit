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
    ],
    version = 3,
    exportSchema = true,
)
abstract class HashkitDatabase : RoomDatabase() {
    abstract fun minerDao(): MinerDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun alertDao(): AlertDao
    abstract fun auditDao(): AuditDao

    companion object {
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
    }
}
