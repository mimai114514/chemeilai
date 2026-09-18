package io.github.mimai114514.chemeilai.data.model

data class City(
    val cityId: String,
    val cityName: String?,
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
    val lines: List<NearbyLine>,
)

data class NearbyLine(
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
)

data class StationDetail(
    val sId: String,
    val name: String,
    val distanceMeters: Int?,
    val lines: List<StationLine>,
)

data class StationLine(
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
    val desc: String?,
    val firstTime: String?,
    val lastTime: String?,
    val price: String?,
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

data class LineDetail(
    val lineId: String,
    val displayName: String,
    val startName: String?,
    val endName: String?,
    val firstTime: String?,
    val lastTime: String?,
    val price: String?,
    val stations: List<RouteStation>,
)

enum class FavoriteType { ROUTE, STATION, LINE }

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
        fun routeId(stationId: String, lineNo: String, direction: Int): String =
            "route|$stationId|$lineNo|$direction"

        fun stationKey(stationId: String): String = "station|$stationId"

        fun lineKey(lineId: String, direction: Int): String = "line|$lineId|$direction"
    }
}
