package io.github.mimai114514.chemeilai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheMeiLaiDao {

    @Query("SELECT * FROM cities ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestCity(): CityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCity(city: CityEntity)

    @Query("SELECT * FROM stations WHERE cityId = :cityId ORDER BY distance ASC")
    suspend fun stations(cityId: String): List<StationEntity>

    @Query("SELECT * FROM stations WHERE sId = :sId LIMIT 1")
    suspend fun station(sId: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStations(stations: List<StationEntity>)

    @Query("DELETE FROM stations WHERE cityId = :cityId")
    suspend fun clearStations(cityId: String)

    @Query("SELECT * FROM line_locators WHERE stationId = :stationId AND lineNo = :lineNo AND direction = :direction LIMIT 1")
    suspend fun locator(stationId: String, lineNo: String, direction: Int): LineLocatorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocators(locators: List<LineLocatorEntity>)

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    suspend fun favorites(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavorite(id: String)
}
