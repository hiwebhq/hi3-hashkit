package hi3.hashkit.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import hi3.hashkit.adapters.demo.DemoMinerAdapter
import hi3.hashkit.adapters.espminer.EspMinerAdapter
import hi3.hashkit.data.db.HashkitDatabase
import hi3.hashkit.data.db.MinerDao
import hi3.hashkit.data.db.TelemetryDao
import hi3.hashkit.domain.adapter.MinerAdapter
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Miners are on the local network; keep timeouts short so scans and polls stay snappy.
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HashkitDatabase =
        Room.databaseBuilder(context, HashkitDatabase::class.java, "hashkit.db")
            .addMigrations(
                HashkitDatabase.MIGRATION_1_2,
                HashkitDatabase.MIGRATION_2_3,
                HashkitDatabase.MIGRATION_3_4,
                HashkitDatabase.MIGRATION_4_5,
                HashkitDatabase.MIGRATION_5_6,
                HashkitDatabase.MIGRATION_6_7,
                HashkitDatabase.MIGRATION_7_8,
                HashkitDatabase.MIGRATION_8_9,
                HashkitDatabase.MIGRATION_9_10,
                HashkitDatabase.MIGRATION_10_11,
                HashkitDatabase.MIGRATION_11_12,
                HashkitDatabase.MIGRATION_12_13,
                HashkitDatabase.MIGRATION_13_14,
                HashkitDatabase.MIGRATION_14_15,
            )
            .build()

    @Provides
    fun minerDao(db: HashkitDatabase): MinerDao = db.minerDao()

    @Provides
    fun telemetryDao(db: HashkitDatabase): TelemetryDao = db.telemetryDao()

    @Provides
    fun alertDao(db: HashkitDatabase): hi3.hashkit.data.db.AlertDao = db.alertDao()

    @Provides
    fun auditDao(db: HashkitDatabase): hi3.hashkit.data.db.AuditDao = db.auditDao()

    @Provides
    fun scheduleDao(db: HashkitDatabase): hi3.hashkit.data.db.ScheduleDao = db.scheduleDao()

    @Provides
    fun hourlyDao(db: HashkitDatabase): hi3.hashkit.data.db.HourlyDao = db.hourlyDao()

    @Provides
    fun farmDao(db: HashkitDatabase): hi3.hashkit.data.db.FarmDao = db.farmDao()

    @Provides
    fun maintenanceDao(db: HashkitDatabase): hi3.hashkit.data.db.MaintenanceDao = db.maintenanceDao()

    @Provides
    fun ruleDao(db: HashkitDatabase): hi3.hashkit.data.db.RuleDao = db.ruleDao()

    @Provides
    fun savedPoolDao(db: HashkitDatabase): hi3.hashkit.data.db.SavedPoolDao = db.savedPoolDao()

    @Provides
    fun logDao(db: HashkitDatabase): hi3.hashkit.data.db.LogDao = db.logDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdapterModule {

    @Binds
    @IntoSet
    abstract fun espMiner(adapter: EspMinerAdapter): MinerAdapter

    @Binds
    @IntoSet
    abstract fun canaan(adapter: hi3.hashkit.adapters.canaan.CanaanAdapter): MinerAdapter

    @Binds
    @IntoSet
    abstract fun braiins(adapter: hi3.hashkit.adapters.braiins.BraiinsAdapter): MinerAdapter

    // Generic cgminer (Antminer-class: Bitmain/VNish/LuxOS) — declines Avalon/BOSer,
    // so probe order relative to the specific adapters does not matter.
    @Binds
    @IntoSet
    abstract fun genericCgminer(adapter: hi3.hashkit.adapters.cgminer.GenericCgMinerAdapter): MinerAdapter

    @Binds
    @IntoSet
    abstract fun whatsminer(adapter: hi3.hashkit.adapters.cgminer.WhatsMinerAdapter): MinerAdapter

    @Binds
    @IntoSet
    abstract fun demo(adapter: DemoMinerAdapter): MinerAdapter
}
