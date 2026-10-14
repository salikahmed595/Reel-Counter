package com.reelcounter.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConfirmedTransitionEntity::class,
        TrackingSessionEntity::class,
        EstimateBucketEntity::class,
        GoalThresholdDeliveryEntity::class,
        ObservationWindowEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun goalDeliveryDao(): GoalDeliveryDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reel_counter.db").build()
    }
}
