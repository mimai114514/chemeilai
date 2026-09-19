package io.github.mimai114514.chemeilai.data.model

data class City(
    val cityId: String,
    val cityName: String?,
)

data class CityOption(
    val cityId: String,
    val name: String,
    val pinyin: String,
    val hot: Boolean,
)

data class Station(
    val sId: String,
    val name: String,
    val distanceMeters: Int?,
    val lat: Double?,
    val lng: Double?,
)

data class Nearby(
    val city: City,
    val stops: List<NearbyStop>,
)

data class NearbyStop(
    val sId: String,
    val name: String,
    val distanceMeters: Int?,
    val lat: Double?,
    val lng: Double?,
    val lines: List<StationLineGroup>,
)

/** 同一条线路在某一站的一个方向。lineId 每个方向都不同。 */
data class LineDirection(
    val lineId: String,
    val lineNo: String,
    val displayName: String,
    val direction: Int,
    val startName: String?,
    val endName: String?,
    val targetOrder: Int,
    val stationName: String,
    val nextStationName: String,
    val etaText: String?,
    val etaMinutes: Int?,
    val desc: String? = null,
    val firstTime: String? = null,
    val lastTime: String? = null,
    val price: String? = null,
    val summaryOverride: String? = null,
)

/** 同一线路在某个站/查询下的所有方向，按线路聚合后展示。 */
data class StationLineGroup(
    val key: String,
    val displayName: String,
    val directions: List<LineDirection>,
    val isFavorite: Boolean = false,
) {
    fun defaultDirection(): LineDirection? =
        directions.minByOrNull { it.etaMinutes ?: Int.MAX_VALUE } ?: directions.firstOrNull()
}

/** 按展示名聚合为线路分组（保持首次出现顺序）。 */
fun List<LineDirection>.toStationLineGroups(): List<StationLineGroup> =
    groupBy { it.displayName }
        .map { (name, directions) ->
            StationLineGroup(
                key = name,
                displayName = name,
                directions = directions.sortedBy { it.direction },
            )
        }

/** 按收藏的线路名重算收藏标记，并把已收藏线路排到前面。 */
fun List<StationLineGroup>.applyFavorites(favoriteLineNames: Set<String>): List<StationLineGroup> =
    map { group -> group.copy(isFavorite = group.displayName in favoriteLineNames) }
        .sortedWith(compareByDescending { it.isFavorite })

data class StationDetail(
    val sId: String,
    val name: String,
    val distanceMeters: Int?,
    val lines: List<StationLineGroup>,
)

data class LineLocator(
    val stationId: String,
    val lineNo: String,
    val lineId: String,
    val lineName: String?,
    val direction: Int,
    val targetOrder: Int,
    val stationName: String,
    val nextStationName: String,
)

data class Realtime(
    val lineDisplayName: String,
    val direction: Int,
    val stationName: String,
    val startName: String?,
    val endName: String?,
    val targetOrder: Int,
    val tip: String?,
    val buses: List<BusEta>,
    val stations: List<RouteStation>,
)

data class BusEta(
    val busId: String?,
    val order: Int?,
    val distanceMeters: Int?,
    val etaMinutes: Int?,
    val timeStr: String?,
    val state: Int?,
    /** 距离目标站还剩几站（0 = 已到本站，1 = 即将到站）；仅通卡数据源提供。 */
    val stationsAway: Int? = null,
    /** 车辆当前所在站点名；仅通卡数据源提供。 */
    val stationName: String? = null,
)

data class RouteStation(
    val sId: String?,
    val name: String,
    val order: Int,
    val lat: Double?,
    val lng: Double?,
)

data class SearchResult(
    val stations: List<Station>,
    val lines: List<SearchLine>,
    val pois: List<Poi>,
) {
    val isEmpty: Boolean get() = stations.isEmpty() && lines.isEmpty() && pois.isEmpty()
}

data class SearchLine(
    val lineId: String,
    val lineNo: String,
    val displayName: String,
    val direction: Int,
    val startName: String?,
    val endName: String?,
)

data class Poi(
    val name: String,
    val address: String?,
    val tag: String?,
    val lat: Double?,
    val lng: Double?,
)

/** cityLineList 中的一条线路（已含方向与首末班等信息）。 */
data class CityLine(
    val lineId: String,
    val lineNo: String,
    val displayName: String,
    val direction: Int,
    val startName: String?,
    val endName: String?,
    val firstTime: String?,
    val lastTime: String?,
    val price: String?,
)

/** 某条线路在某个方向上离用户最近的站台。 */
data class NearestStation(
    val direction: Int,
    val lineId: String,
    val lineNo: String,
    val startName: String?,
    val endName: String?,
    val stationId: String,
    val stationName: String,
    val distanceMeters: Int,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val directionLabel: String? get() = listOfNotNull(startName, endName).joinToString(" → ").ifBlank { null }
}

/** 某条线路离用户最近站台的到达情况，用于收藏页展示。 */
data class LineArrival(
    val stationId: String,
    val stationName: String,
    val lineNo: String,
    val direction: Int,
    val distanceMeters: Int,
    val etaMinutes: Int?,
    val tip: String?,
    val buses: List<BusEta>,
)

data class LineDetail(
    val lineId: String,
    val displayName: String,
    val direction: Int,
    val startName: String?,
    val endName: String?,
    val firstTime: String?,
    val lastTime: String?,
    val price: String?,
    val stations: List<RouteStation>,
)

enum class FavoriteType { STATION, LINE }

data class Favorite(
    val id: String,
    val type: FavoriteType,
    val stationId: String? = null,
    val stationName: String? = null,
    val lineId: String? = null,
    val lineNo: String? = null,
    val lineName: String? = null,
    val direction: Int? = null,
    val startName: String? = null,
    val endName: String? = null,
    val cityId: String? = null,
) {
    companion object {
        fun stationKey(stationId: String): String = "station|$stationId"

        fun lineKey(cityId: String?, lineName: String): String = "line|${cityId.orEmpty()}|$lineName"
    }
}
