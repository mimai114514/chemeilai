package io.github.mimai114514.chemeilai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CityEntity::class,
        StationEntity::class,
        LineLocatorEntity::class,
        FavoriteEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CheMeiLaiDatabase : RoomDatabase() {

    abstract fun dao(): CheMeiLaiDao

    companion object {
        @Volatile
        private var instance: CheMeiLaiDatabase? = null

        fun get(context: Context): CheMeiLaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CheMeiLaiDatabase::class.java,
                    "chemeilai.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
