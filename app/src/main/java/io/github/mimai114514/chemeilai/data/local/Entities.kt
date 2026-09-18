package io.github.mimai114514.chemeilai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val cityId: String,
    val cityName: String?,
    val lat: Double,
    val lng: Double,
    val updatedAt: Long,
)

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val sId: String,
    val name: String?,
    val lat: Double?,
    val lng: Double?,
    val distance: Double?,
    val cityId: String?,
    val updatedAt: Long,
)

@Entity(tableName = "line_locators", primaryKeys = ["stationId", "lineNo", "direction"])
data class LineLocatorEntity(
    val stationId: String,
    val lineNo: String,
    val direction: Int,
    val lineId: String,
    val lineName: String?,
    val targetOrder: Int,
    val stationName: String?,
    val nextStationName: String?,
    val cityId: String?,
    val updatedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val type: String,
    val stationId: String?,
    val stationName: String?,
    val lineId: String?,
    val lineNo: String?,
    val lineName: String?,
    val direction: Int?,
    val startName: String?,
    val endName: String?,
    val cityId: String?,
    val createdAt: Long,
)
