package io.github.mimai114514.chemeilai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CityEntity::class,
        StationEntity::class,
        LineLocatorEntity::class,
        FavoriteEntity::class,
        RoadStateEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class CheMeiLaiDatabase : RoomDatabase() {

    abstract fun dao(): CheMeiLaiDao

    companion object {
        /** 只加一张表，保留收藏与城市缓存。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `road_states` (" +
                        "`roadId` TEXT NOT NULL, " +
                        "`lineInfos` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`roadId`))",
                )
            }
        }

        @Volatile
        private var instance: CheMeiLaiDatabase? = null

        fun get(context: Context): CheMeiLaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CheMeiLaiDatabase::class.java,
                    "chemeilai.db",
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
