package io.github.mimai114514.chemeilai.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class NearStationsDto(
    val nearStations: List<NearStationDto> = emptyList(),
)

@Serializable
data class NearStationDto(
    val sId: String? = null,
    val sn: String? = null,
    val distance: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val sType: Double? = null,
)

@Serializable
data class NearLinesDto(
    val nearLines: List<NearStopDto> = emptyList(),
)

@Serializable
data class NearStopDto(
    val sId: String? = null,
    val sn: String? = null,
    val distance: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val lines: List<StationLineEntryDto> = emptyList(),
)

@Serializable
data class StationDetailDto(
    val sId: String? = null,
    val sn: String? = null,
    val distance: Double? = null,
    val lines: List<StationLineEntryDto> = emptyList(),
)

@Serializable
data class StationLineEntryDto(
    val line: LineDto? = null,
    val targetStation: StationRefDto? = null,
    val nextStation: StationRefDto? = null,
    val stnStates: List<StnStateDto> = emptyList(),
)

@Serializable
data class LineDto(
    val lineId: String? = null,
    val name: String? = null,
    val lineNo: String? = null,
    val direction: Double? = null,
    val startSn: String? = null,
    val endSn: String? = null,
    val destinationName: String? = null,
    val desc: String? = null,
    val shortDesc: String? = null,
    val firstTime: String? = null,
    val lastTime: String? = null,
    val price: String? = null,
    val state: Double? = null,
    val stationsNum: Double? = null,
)

@Serializable
data class StationRefDto(
    val sId: String? = null,
    val sn: String? = null,
    val order: Double? = null,
    val distanceToSp: Double? = null,
)

@Serializable
data class StnStateDto(
    val value: Double? = null,
    val travelTime: Double? = null,
    val state: Double? = null,
    val timeStr: String? = null,
)

@Serializable
data class LineDetailDto(
    val line: LineDto? = null,
    val targetOrder: Double? = null,
    val specialTargetOrder: Double? = null,
    val tip: TipDto? = null,
    val buses: List<BusDto> = emptyList(),
    val stations: List<RouteStationDto> = emptyList(),
)

@Serializable
data class TipDto(
    val desc: String? = null,
)

@Serializable
data class BusDto(
    val busId: String? = null,
    val order: Double? = null,
    val specialOrder: Double? = null,
    val distanceToSc: Double? = null,
    val travelTime: Double? = null,
    val state: Double? = null,
    val timeStr: String? = null,
)

@Serializable
data class RouteStationDto(
    val sId: String? = null,
    val sn: String? = null,
    val order: Double? = null,
    val specialOrder: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val distanceToSp: Double? = null,
)

@Serializable
data class CityListResponse(
    val status: String? = null,
    val data: CityListData? = null,
)

@Serializable
data class CityListData(
    val gpsRealtimeCity: RealtimeCityDto? = null,
)

@Serializable
data class RealtimeCityDto(
    val cityId: String? = null,
    val cityName: String? = null,
    val name: String? = null,
)

@Serializable
data class ClientSearchDto(
    val stations: List<NearStationDto> = emptyList(),
    val lines: List<SearchLineDto> = emptyList(),
    val pois: List<PoiDto> = emptyList(),
    val gpstype: String? = null,
    val stationCount: Double? = null,
    val lineCount: Double? = null,
    val poiCount: Double? = null,
    val ordertype: String? = null,
)

@Serializable
data class PoiDto(
    val sn: String? = null,
    val sn1: String? = null,
    val address: String? = null,
    val adname: String? = null,
    val sn1Address: String? = null,
    val sn1Tag: String? = null,
    val sn1Type: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class SearchLineDto(
    val lineId: String? = null,
    val name: String? = null,
    val lineNo: String? = null,
    val direction: Double? = null,
    val startSn: String? = null,
    val endSn: String? = null,
)

@Serializable
data class LineRouteDto(
    val line: LineDto? = null,
    val stations: List<RouteStationDto> = emptyList(),
)

@Serializable
data class CityLineListDto(
    val allLines: Map<String, List<LineDto>> = emptyMap(),
)
