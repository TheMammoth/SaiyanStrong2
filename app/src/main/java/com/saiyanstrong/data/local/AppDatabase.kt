package com.saiyanstrong.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saiyanstrong.data.local.dao.BarPathMetricsDao
import com.saiyanstrong.data.local.dao.BodyWeightDao
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.dao.ExerciseLogDao
import com.saiyanstrong.data.local.dao.SessionDao
import com.saiyanstrong.data.local.dao.SetLogDao
import com.saiyanstrong.data.local.dao.TemplateDao
import com.saiyanstrong.data.local.entity.BarPathMetricsEntity
import com.saiyanstrong.data.local.entity.BodyWeightEntity
import com.saiyanstrong.data.local.entity.ExerciseEntity
import com.saiyanstrong.data.local.entity.ExerciseLogEntity
import com.saiyanstrong.data.local.entity.SessionEntity
import com.saiyanstrong.data.local.entity.SetLogEntity
import com.saiyanstrong.data.local.entity.TemplateEntity
import com.saiyanstrong.data.local.entity.TemplateExerciseEntity

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Clear exercises so ExerciseSeeder re-inserts updated names on next app launch
        database.execSQL("DELETE FROM exercises")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `templates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`created_ms` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `template_exercises` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`template_id` INTEGER NOT NULL, " +
                "`exercise_id` INTEGER NOT NULL, " +
                "`order_index` INTEGER NOT NULL, " +
                "FOREIGN KEY(`template_id`) REFERENCES `templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercises_template_id` ON `template_exercises` (`template_id`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_template_exercises_exercise_id` ON `template_exercises` (`exercise_id`)")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_weight_logs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date_ms` INTEGER NOT NULL, " +
                "`weight_kg` REAL NOT NULL)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exercises ADD COLUMN rest_timer_sec INTEGER")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE templates ADD COLUMN is_from_coach INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `bar_path_metrics` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`set_log_id` INTEGER NOT NULL, " +
                "`peak_velocity_ms` REAL NOT NULL, " +
                "`mean_concentric_velocity_ms` REAL NOT NULL, " +
                "`peak_power_watts` REAL NOT NULL, " +
                "`mean_power_watts` REAL NOT NULL, " +
                "`range_of_motion_cm` REAL NOT NULL, " +
                "`bar_path_deviation_cm` REAL NOT NULL, " +
                "`velocity_zone` TEXT NOT NULL, " +
                "FOREIGN KEY(`set_log_id`) REFERENCES `set_logs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bar_path_metrics_set_log_id` ON `bar_path_metrics` (`set_log_id`)")
    }
}

// Widens bar_path_metrics from "one row per set" to "one row per exercise, optionally
// linked to a set" so a standalone (non-workout) bar path analysis has somewhere to live.
// SQLite can't alter a column's NOT NULL-ness or drop a unique index in place, so this is
// a full table-recreate migration: build the new shape, backfill exercise_id/created_at_ms
// for existing (always set-linked) rows via a join, swap the table, recreate indices.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE `bar_path_metrics_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`set_log_id` INTEGER, " +
                "`exercise_id` INTEGER NOT NULL, " +
                "`created_at_ms` INTEGER NOT NULL, " +
                "`peak_velocity_ms` REAL NOT NULL, " +
                "`mean_concentric_velocity_ms` REAL NOT NULL, " +
                "`peak_power_watts` REAL NOT NULL, " +
                "`mean_power_watts` REAL NOT NULL, " +
                "`range_of_motion_cm` REAL NOT NULL, " +
                "`bar_path_deviation_cm` REAL NOT NULL, " +
                "`velocity_zone` TEXT NOT NULL, " +
                "FOREIGN KEY(`set_log_id`) REFERENCES `set_logs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        database.execSQL(
            "INSERT INTO `bar_path_metrics_new` " +
                "(id, set_log_id, exercise_id, created_at_ms, peak_velocity_ms, mean_concentric_velocity_ms, " +
                "peak_power_watts, mean_power_watts, range_of_motion_cm, bar_path_deviation_cm, velocity_zone) " +
                "SELECT bpm.id, bpm.set_log_id, " +
                "(SELECT el.exercise_id FROM set_logs sl JOIN exercise_logs el ON el.id = sl.exercise_log_id WHERE sl.id = bpm.set_log_id), " +
                "(SELECT sl.timestamp_ms FROM set_logs sl WHERE sl.id = bpm.set_log_id), " +
                "bpm.peak_velocity_ms, bpm.mean_concentric_velocity_ms, bpm.peak_power_watts, bpm.mean_power_watts, " +
                "bpm.range_of_motion_cm, bpm.bar_path_deviation_cm, bpm.velocity_zone " +
                "FROM bar_path_metrics bpm"
        )
        database.execSQL("DROP TABLE `bar_path_metrics`")
        database.execSQL("ALTER TABLE `bar_path_metrics_new` RENAME TO `bar_path_metrics`")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_bar_path_metrics_set_log_id` ON `bar_path_metrics` (`set_log_id`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_bar_path_metrics_exercise_id` ON `bar_path_metrics` (`exercise_id`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_bar_path_metrics_created_at_ms` ON `bar_path_metrics` (`created_at_ms`)")
    }
}

@Database(
    entities = [
        ExerciseEntity::class,
        SessionEntity::class,
        ExerciseLogEntity::class,
        SetLogEntity::class,
        TemplateEntity::class,
        TemplateExerciseEntity::class,
        BodyWeightEntity::class,
        BarPathMetricsEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun setLogDao(): SetLogDao
    abstract fun templateDao(): TemplateDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun barPathMetricsDao(): BarPathMetricsDao
}
