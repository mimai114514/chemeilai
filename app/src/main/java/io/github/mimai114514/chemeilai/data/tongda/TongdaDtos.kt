package io.github.mimai114514.chemeilai.data.tongda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TongdaResponse<T>(
    val resultCode: String? = null,
    val resultMsg: String? = null,
    val errCode: String? = null,
    val errCodeDes: String? = null,
    val data: T? = null,
) {
    val isSuccess: Boolean get() = resultCode == "SUCCESS"
}

@Serializable
data class TongdaSite(
    val stationno: String? = null,
    val stationname: String? = null,
    val lng: String? = null,
    val lat: String? = null,
    val siteid: String? = null,
    val siteTime: String? = null,
    val siteMileage: String? = null,
)

@Serializable
data class TongdaRoadSite(
    val firsttime: String? = null,
    val ticketprice: String? = null,
    val lng: String? = null,
    val stationno: String? = null,
    val roadid: String? = null,
    val stationname: String? = null,
    val busDate: String? = null,
    val lasttime: String? = null,
    val firstSite: String? = null,
    val roadstatus: String? = null,
    val lat: String? = null,
    val lastSite: String? = null,
    val roadname: String? = null,
    val time: String? = null,
)

@Serializable
data class TongdaLineList(
    val pageSize: Double? = null,
    val totalCount: Double? = null,
    val pageNo: Double? = null,
    val totalPages: Double? = null,
    val businfos: JsonElement? = null,
    val lineinfos: List<TongdaLineInfo> = emptyList(),
)

@Serializable
data class TongdaLineInfo(
    @SerialName("roadid") val roadId: String? = null,
    val roadname: String? = null,
    val roadstatus: String? = null,
    val firstsite: String? = null,
    val lastsite: String? = null,
    val firsttime: String? = null,
    val lasttime: String? = null,
    val ticketprice: String? = null,
    val autooperation: String? = null,
    val stationname: String? = null,
    val busstation: List<TongdaBusStation>? = null,
)

@Serializable
data class TongdaBusStation(
    val stationno: String? = null,
    val stationname: String? = null,
    val lng: String? = null,
    val lat: String? = null,
    val siteid: String? = null,
    val siteTime: String? = null,
    val siteMileage: String? = null,
)

@Serializable
data class TongdaBusInfo(
    val nearlySiteNo: String? = null,
    val nearlyBusInfo: List<TongdaNearlyBus>? = null,
    val allBusInfo: List<TongdaBus>? = null,
)

@Serializable
data class TongdaNearlyBus(
    val busPlate: String? = null,
    val estimateTime: String? = null,
    val speed: String? = null,
    val roadStatus: String? = null,
    val stationNum: String? = null,
)

@Serializable
data class TongdaBus(
    val busplate: String? = null,
    val stationno: String? = null,
    val sitename: String? = null,
    val speed: String? = null,
    val lng: String? = null,
    val lat: String? = null,
    val roadstatus: String? = null,
    val online: String? = null,
    val azimuth: String? = null,
    val updatetime: String? = null,
    val siteid: String? = null,
)
