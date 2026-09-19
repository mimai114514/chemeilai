package io.github.mimai114514.chemeilai.data.tongda

import io.github.mimai114514.chemeilai.data.model.BusEta
import io.github.mimai114514.chemeilai.data.model.City
import io.github.mimai114514.chemeilai.data.model.CityLine
import io.github.mimai114514.chemeilai.data.model.LineArrival
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.data.model.Nearby
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.data.model.NearestStation
import io.github.mimai114514.chemeilai.data.model.Realtime
import io.github.mimai114514.chemeilai.data.model.RouteStation
import io.github.mimai114514.chemeilai.data.model.SearchLine
import io.github.mimai114514.chemeilai.data.model.SearchResult
import io.github.mimai114514.chemeilai.data.model.Station
import io.github.mimai114514.chemeilai.data.model.StationDetail
import io.github.mimai114514.chemeilai.data.model.StationLineGroup
import io.github.mimai114514.chemeilai.data.model.applyFavorites
import io.github.mimai114514.chemeilai.data.model.toStationLineGroups
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Calendar

/**
 * 梧州（通卡/yourbus 平台）数据源。参数命名不统一：
 * 站点与线路接口用 companyNo / scontent，实时接口用 company / roadId，且 localtion 拼写如此。
 */
class TongdaSource(private val api: TongdaApi) {

    private val siteCache = mutableMapOf<String, List<TongdaSite>>()
    private val lineCache = mutableMapOf<String, List<TongdaLineInfo>>()
    private val roadSiteCache = mutableMapOf<String, List<TongdaRoadSite>>()
    private val roadStateCache = mutableMapOf<String, List<TongdaLineInfo>>()

    fun companyFor(cityId: String?): String? = cityId?.let { COMPANIES[it] }

    suspend fun nearby(
        companyNo: String,
        city: City,
        lat: Double,
        lng: Double,
        favoriteLineNames: Set<String>,
        limit: Int = 8,
    ): Nearby {
        val (bLat, bLng) = ChinaCoordinates.wgs84ToBd09(lat, lng)
        val sites = loadSites(companyNo)
        val roads = loadRoadSites(companyNo, bLat, bLng)
        // 同名站台（可能相距几十米）共用同一批线路，实时数据在下游按各站坐标分别计算
        val byStation = roads.groupBy { it.stationname.orEmpty() }
        val stops = sites
            .mapNotNull { site ->
                val id = site.siteid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = site.stationname?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val siteLat = site.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val siteLng = site.lng?.toDoubleOrNull() ?: return@mapNotNull null
                NearbyStop(
                    sId = id,
                    name = name,
                    distanceMeters = metersBetween(bLat, bLng, siteLat, siteLng).toInt(),
                    lat = siteLat,
                    lng = siteLng,
                    lines = byStation[name].orEmpty()
                        .mapNotNull { it.toLineDirection() }
                        .toStationLineGroups()
                        .applyFavorites(favoriteLineNames),
                )
            }
            .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
            .take(limit)
        val enriched = enrichStops(companyNo, stops)
        return Nearby(city, enriched)
    }

    suspend fun stationDetail(
        companyNo: String,
        stationId: String,
        fallbackName: String?,
        lat: Double?,
        lng: Double?,
        favoriteLineNames: Set<String>,
    ): StationDetail {
        val local = if (lat != null && lng != null) ChinaCoordinates.wgs84ToBd09(lat, lng) else null
        val bLat = local?.first
        val bLng = local?.second
        val site = loadSites(companyNo).firstOrNull { it.siteid == stationId }
        val siteLat = site?.lat?.toDoubleOrNull()
        val siteLng = site?.lng?.toDoubleOrNull()
        val name = site?.stationname ?: fallbackName ?: stationId
        val useLat = siteLat ?: bLat
        val useLng = siteLng ?: bLng
        val directions = if (useLat != null && useLng != null) {
            val raw = loadRoadSites(companyNo, useLat, useLng)
                .filter { it.stationname == name }
                .mapNotNull { it.toLineDirection() }
            // 同名站台（马路两侧）共用一个站点编号，getLocalRoadSite 会把双向都返回，
            // 用站序接口里各方向的站台坐标标出本站台方向，换向时仍可查看对向
            if (siteLat != null && siteLng != null) {
                markPlatformDirections(companyNo, raw, siteLat, siteLng)
            } else {
                raw
            }
        } else {
            emptyList()
        }
        val distance = if (bLat != null && bLng != null && siteLat != null && siteLng != null) {
            metersBetween(bLat, bLng, siteLat, siteLng).toInt()
        } else {
            null
        }
        return StationDetail(
            sId = stationId,
            name = name,
            distanceMeters = distance,
            lines = enrichGroups(
                companyNo = companyNo,
                lat = useLat ?: 0.0,
                lng = useLng ?: 0.0,
                groups = directions.toStationLineGroups().applyFavorites(favoriteLineNames),
            ),
        )
    }

    private suspend fun enrichStops(companyNo: String, stops: List<NearbyStop>): List<NearbyStop> =
        coroutineScope {
            val semaphore = Semaphore(ENRICH_CONCURRENCY)
            stops.map { stop ->
                async {
                    val lat = stop.lat
                    val lng = stop.lng
                    if (lat == null || lng == null || stop.lines.isEmpty()) {
                        stop
                    } else {
                        stop.copy(lines = enrichGroups(companyNo, lat, lng, stop.lines, semaphore))
                    }
                }
            }.awaitAll()
        }

    private suspend fun enrichGroups(
        companyNo: String,
        lat: Double,
        lng: Double,
        groups: List<StationLineGroup>,
        semaphore: Semaphore = Semaphore(ENRICH_CONCURRENCY),
    ): List<StationLineGroup> = coroutineScope {
        groups.map { group ->
            async {
                group.copy(
                    directions = group.directions.map { direction ->
                        semaphore.withPermit { enrichDirection(companyNo, direction, lat, lng) }
                    },
                )
            }
        }.awaitAll()
    }

    /** 用站点坐标查实时：最近的车的到达分钟数，以及距本站还有几站。 */
    private suspend fun enrichDirection(
        companyNo: String,
        direction: LineDirection,
        lat: Double,
        lng: Double,
    ): LineDirection {
        if (lat == 0.0 && lng == 0.0) return direction
        val info = runCatching {
            api.getBusInfo(busInfoParams(direction.lineId, companyNo, lat, lng)).data
        }.getOrNull() ?: return direction
        val bus = busList(info, direction.direction).firstOrNull() ?: return direction
        val minutes = bus.etaMinutes ?: return direction
        val suffix = when {
            bus.stationsAway == null -> null
            bus.stationsAway <= 0 -> "已到本站"
            bus.stationsAway == 1 -> "即将到站"
            else -> "${bus.stationsAway}站"
        }
        return direction.copy(
            etaMinutes = minutes,
            etaText = if (suffix == null) "${minutes}分钟" else "${minutes}分钟 · $suffix",
        )
    }

    private suspend fun roadState(companyNo: String, roadId: String): List<TongdaLineInfo> {
        roadStateCache[roadId]?.let { return it }
        val lines = runCatching {
            api.getRoadState(roadStateParams(roadId, companyNo)).lineinfos
        }.getOrDefault(emptyList())
        if (lines.isNotEmpty()) roadStateCache[roadId] = lines
        return lines
    }

    /** 站序接口里某个方向在该站台上的那条记录（坐标是马路两侧区分方向的依据）。 */
    private suspend fun routeStation(
        companyNo: String,
        roadId: String,
        direction: Int,
        stationName: String?,
    ): TongdaBusStation? {
        if (stationName.isNullOrBlank()) return null
        val lineInfo = roadState(companyNo, roadId)
            .firstOrNull { it.roadstatus?.toIntOrNull() == direction }
            ?: return null
        return lineInfo.busstation?.firstOrNull { it.stationname == stationName }
    }

    /**
     * 标出每条线路里停在这个站台上的方向：同名站台两个方向共用一个站点编号，
     * 只能靠站序里该站的坐标区分（两侧约差 50 米），站点页优先展示这个方向。
     * 拿不到坐标时不做标记，宁可退回按 ETA 挑方向也不要漏线路。
     */
    private suspend fun markPlatformDirections(
        companyNo: String,
        directions: List<LineDirection>,
        stationLat: Double,
        stationLng: Double,
    ): List<LineDirection> = coroutineScope {
        val semaphore = Semaphore(ENRICH_CONCURRENCY)
        directions.groupBy { it.lineId }.values.map { sameLine ->
            async {
                val scored = sameLine.map { direction ->
                    val station = semaphore.withPermit {
                        routeStation(companyNo, direction.lineId, direction.direction, direction.stationName)
                    }
                    val sLat = station?.lat?.toDoubleOrNull()
                    val sLng = station?.lng?.toDoubleOrNull()
                    val distance = if (sLat != null && sLng != null) {
                        metersBetween(stationLat, stationLng, sLat, sLng)
                    } else {
                        null
                    }
                    direction to distance
                }
                val nearest = scored.filter { it.second != null }
                    .minByOrNull { it.second ?: Double.MAX_VALUE }
                    ?: return@async sameLine
                if ((nearest.second ?: Double.MAX_VALUE) > PLATFORM_RADIUS_METERS) return@async sameLine
                sameLine.map { direction ->
                    if (direction.lineId == nearest.first.lineId &&
                        direction.direction == nearest.first.direction
                    ) {
                        direction.copy(platformMatch = true)
                    } else {
                        direction
                    }
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun search(
        companyNo: String,
        keyword: String,
        lat: Double?,
        lng: Double?,
    ): SearchResult {
        val key = keyword.trim()
        if (key.isEmpty()) return SearchResult(emptyList(), emptyList(), emptyList())
        val local = if (lat != null && lng != null) ChinaCoordinates.wgs84ToBd09(lat, lng) else null
        val bLat = local?.first
        val bLng = local?.second
        val stations = loadSites(companyNo)
            .filter { it.stationname?.contains(key, ignoreCase = true) == true }
            .mapNotNull { site ->
                val id = site.siteid ?: return@mapNotNull null
                val siteLat = site.lat?.toDoubleOrNull()
                val siteLng = site.lng?.toDoubleOrNull()
                val distance = if (bLat != null && bLng != null && siteLat != null && siteLng != null) {
                    metersBetween(bLat, bLng, siteLat, siteLng).toInt()
                } else {
                    null
                }
                Station(id, site.stationname.orEmpty(), distance, siteLat, siteLng)
            }
            .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
            .take(10)
        val lines = loadLines(companyNo)
            .filter { it.roadname?.contains(key, ignoreCase = true) == true }
            .mapNotNull { line ->
                val id = line.roadId ?: return@mapNotNull null
                SearchLine(
                    lineId = id,
                    lineNo = normalizeLineNo(line.roadname),
                    displayName = normalizeLineNo(line.roadname),
                    direction = line.roadstatus?.toIntOrNull() ?: 0,
                    startName = line.firstsite,
                    endName = line.lastsite,
                )
            }
            .distinctBy { it.lineId + "-" + it.direction }
            .take(10)
        return SearchResult(stations, lines, emptyList())
    }

    /** 站点与用户的距离（按通卡站点表计算）。 */
    suspend fun stationDistanceMeters(
        companyNo: String,
        stationId: String,
        lat: Double,
        lng: Double,
    ): Int? {
        val site = loadSites(companyNo).firstOrNull { it.siteid == stationId } ?: return null
        val siteLat = site.lat?.toDoubleOrNull() ?: return null
        val siteLng = site.lng?.toDoubleOrNull() ?: return null
        val (bLat, bLng) = ChinaCoordinates.wgs84ToBd09(lat, lng)
        return metersBetween(bLat, bLng, siteLat, siteLng).toInt()
    }

    suspend fun lineDirections(companyNo: String, lineName: String): List<CityLine> {
        val key = normalizeLineNo(lineName)
        return loadLines(companyNo)
            .filter { normalizeLineNo(it.roadname) == key }
            .mapIndexedNotNull { index, line ->
                val id = line.roadId ?: return@mapIndexedNotNull null
                CityLine(
                    lineId = id,
                    lineNo = normalizeLineNo(line.roadname),
                    displayName = normalizeLineNo(line.roadname),
                    direction = line.roadstatus?.toIntOrNull() ?: index,
                    startName = line.firstsite,
                    endName = line.lastsite,
                    firstTime = line.firsttime,
                    lastTime = line.lasttime,
                    price = line.ticketprice,
                )
            }
    }

    suspend fun realtime(
        companyNo: String,
        roadId: String,
        lineNo: String,
        direction: Int,
        lat: Double?,
        lng: Double?,
        stationName: String?,
        targetOrder: Int?,
    ): Realtime {
        val local = if (lat != null && lng != null) ChinaCoordinates.wgs84ToBd09(lat, lng) else null
        val bLat = local?.first
        val bLng = local?.second
        val roads = if (bLat != null && bLng != null) loadRoadSites(companyNo, bLat, bLng) else emptyList()
        val stateResult = runCatching { api.getRoadState(roadStateParams(roadId, companyNo)).lineinfos }
        val lineInfo = stateResult.getOrNull()
            ?.firstOrNull { it.roadstatus?.toIntOrNull() == direction }
            ?: stateResult.getOrNull()?.firstOrNull()
        val stations = lineInfo?.busstation.orEmpty().mapNotNull { it.toRouteStation() }
        // getLocalRoadSite 的 stationno 是站点编号，站序里的 stationno 才是序号，用站名反查
        val targetStation = stationName
            ?.takeIf { it.isNotBlank() }
            ?.let { name -> lineInfo?.busstation?.firstOrNull { it.stationname == name } }
        val resolvedTargetOrder = targetStation?.stationno?.toIntOrNull() ?: targetOrder
        // getBusInfo 的 stationNum 是相对查询点算的，必须用目标站坐标提问，否则「还剩几站」对不上
        val queryLat = targetStation?.lat?.toDoubleOrNull() ?: bLat
        val queryLng = targetStation?.lng?.toDoubleOrNull() ?: bLng
        val buses = if (queryLat != null && queryLng != null) {
            runCatching { api.getBusInfo(busInfoParams(roadId, companyNo, queryLat, queryLng)).data }
                .getOrNull()
                ?.let { info -> busList(info, direction) }
                .orEmpty()
        } else {
            emptyList()
        }
        return Realtime(
            lineDisplayName = lineNo,
            direction = direction,
            stationName = stationName.orEmpty(),
            startName = lineInfo?.firstsite ?: roads.firstOrNull { it.roadid == roadId }?.firstSite,
            endName = lineInfo?.lastsite ?: roads.firstOrNull { it.roadid == roadId }?.lastSite,
            targetOrder = resolvedTargetOrder ?: 0,
            tip = null,
            buses = buses,
            stations = stations,
        )
    }

    suspend fun lineRealtime(
        companyNo: String,
        lineName: String,
        direction: Int,
        lat: Double?,
        lng: Double?,
    ): Realtime? {
        val cityLine = lineDirections(companyNo, lineName)
            .firstOrNull { it.direction == direction }
            ?: lineDirections(companyNo, lineName).firstOrNull()
            ?: return null
        return realtime(
            companyNo = companyNo,
            roadId = cityLine.lineId,
            lineNo = cityLine.displayName,
            direction = cityLine.direction,
            lat = lat,
            lng = lng,
            stationName = null,
            targetOrder = null,
        )
    }

    suspend fun lineNearestStations(
        companyNo: String,
        lineName: String,
        lat: Double,
        lng: Double,
    ): List<NearestStation> {
        val (bLat, bLng) = ChinaCoordinates.wgs84ToBd09(lat, lng)
        val directions = lineDirections(companyNo, lineName)
        return directions.mapNotNull { direction ->
            val stations = runCatching {
                api.getRoadState(roadStateParams(direction.lineId, companyNo)).lineinfos
            }.getOrNull()
                ?.firstOrNull { it.roadstatus?.toIntOrNull() == direction.direction }
                ?.busstation
                .orEmpty()
            val scored = stations.mapNotNull { station ->
                val id = station.siteid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val stationLat = station.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val stationLng = station.lng?.toDoubleOrNull() ?: return@mapNotNull null
                Triple(station, id, metersBetween(bLat, bLng, stationLat, stationLng))
            }
            val nearest = scored.minByOrNull { it.third } ?: return@mapNotNull null
            NearestStation(
                direction = direction.direction,
                lineId = direction.lineId,
                lineNo = direction.lineNo,
                startName = direction.startName,
                endName = direction.endName,
                stationId = nearest.second,
                stationName = nearest.first.stationname.orEmpty(),
                distanceMeters = nearest.third.toInt(),
                lat = nearest.first.lat?.toDoubleOrNull(),
                lng = nearest.first.lng?.toDoubleOrNull(),
            )
        }
    }

    suspend fun arrivalAt(
        companyNo: String,
        nearest: NearestStation,
        lat: Double?,
        lng: Double?,
    ): LineArrival? {
        // 通卡站序接口返回的站台坐标已是 BD-09；用它提问，stationNum 才是相对这个站台的
        val stationLat = nearest.lat
        val stationLng = nearest.lng
        val (bLat, bLng) = when {
            stationLat != null && stationLng != null -> stationLat to stationLng
            lat != null && lng != null -> ChinaCoordinates.wgs84ToBd09(lat, lng)
            else -> return null
        }
        val info = runCatching {
            api.getBusInfo(busInfoParams(nearest.lineId, companyNo, bLat, bLng)).data
        }.getOrNull() ?: return null
        val buses = busList(info, nearest.direction)
        val best = buses.minByOrNull { it.etaMinutes ?: Int.MAX_VALUE }
        return LineArrival(
            stationId = nearest.stationId,
            stationName = nearest.stationName,
            lineNo = nearest.lineNo,
            direction = nearest.direction,
            distanceMeters = nearest.distanceMeters,
            etaMinutes = best?.etaMinutes,
            tip = null,
            buses = buses,
        )
    }

    /**
     * nearlyBusInfo 的 stationNum 是「到查询点还剩几站 + 1」（查询点就是目标站时，
     * 车已到站为 1），不是线路上的绝对站序；绝对站序在 allBusInfo 里（0-based）。
     */
    private fun busList(info: TongdaBusInfo, direction: Int? = null): List<BusEta> {
        val all = info.nearlyBusInfo.orEmpty()
        // nearlyBusInfo 会混入双向的车，按 roadStatus 过滤；方向未知的车保留，对向的车宁可不要
        val scoped = if (direction != null) {
            all.filter { it.roadStatus?.toIntOrNull() == direction }
                .ifEmpty { all.filter { it.roadStatus?.toIntOrNull() == null } }
        } else {
            all
        }
        val positions = info.allBusInfo.orEmpty().associateBy { it.busplate }
        return scoped
            .map { bus ->
                val position = positions[bus.busPlate]
                BusEta(
                    busId = bus.busPlate,
                    order = position?.stationno?.toIntOrNull()?.plus(1),
                    distanceMeters = null,
                    etaMinutes = bus.estimateTime?.toDoubleOrNull()?.let { Math.round(it).toInt() },
                    timeStr = null,
                    state = position?.roadstatus?.toIntOrNull() ?: bus.roadStatus?.toIntOrNull(),
                    stationsAway = bus.stationNum?.toDoubleOrNull()
                        ?.let { Math.round(it).toInt() - 1 },
                    stationName = position?.sitename,
                )
            }
            .sortedBy { it.etaMinutes ?: Int.MAX_VALUE }
    }

    private suspend fun loadSites(companyNo: String): List<TongdaSite> {
        siteCache[companyNo]?.let { return it }
        val sites = runCatching {
            api.getLocalSite(params("{localtion:'0,0',companyNo:'$companyNo'}")).data.orEmpty()
        }.getOrDefault(emptyList())
        if (sites.isNotEmpty()) siteCache[companyNo] = sites
        return sites
    }

    private suspend fun loadLines(companyNo: String): List<TongdaLineInfo> {
        lineCache[companyNo]?.let { return it }
        val all = mutableListOf<TongdaLineInfo>()
        var page = 1
        while (page <= MAX_LINE_PAGES) {
            val data = runCatching {
                api.getRoadInfoSum(
                    params("{scontent:'99999',companyNo:'$companyNo',pageNo:'$page',pageSize:'20'}"),
                )
            }.getOrNull() ?: break
            all += data.lineinfos
            val totalPages = data.totalPages?.toInt() ?: 0
            if (data.lineinfos.isEmpty() || page >= totalPages) break
            page++
        }
        if (all.isNotEmpty()) lineCache[companyNo] = all
        return all
    }

    private suspend fun loadRoadSites(companyNo: String, lat: Double, lng: Double): List<TongdaRoadSite> {
        val key = "$companyNo|${"%.4f".format(java.util.Locale.US, lat)}|${"%.4f".format(java.util.Locale.US, lng)}"
        roadSiteCache[key]?.let { return it }
        val sites = runCatching {
            api.getLocalRoadSite(params("{localtion:'$lng,$lat',companyNo:'$companyNo'}")).data.orEmpty()
        }.getOrDefault(emptyList())
        if (sites.isNotEmpty()) roadSiteCache[key] = sites
        return sites
    }

    private fun params(json: String): String = json

    private fun roadStateParams(roadId: String, companyNo: String): String =
        params("{scontent:'$roadId',companyNo:'$companyNo'}")

    private fun busInfoParams(roadId: String, companyNo: String, lat: Double, lng: Double): String =
        params("{roadId:'$roadId',company:'$companyNo',localtion:'$lng,$lat'}")

    private fun TongdaRoadSite.toLineDirection(): LineDirection? {
        val id = roadid?.takeIf { it.isNotBlank() } ?: return null
        val name = normalizeLineNo(roadname).ifBlank { return null }
        val departures = time?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val next = departures.firstOrNull { it > nowHm() } ?: departures.firstOrNull()
        return LineDirection(
            lineId = id,
            lineNo = name,
            displayName = name,
            direction = roadstatus?.toIntOrNull() ?: 0,
            startName = firstSite,
            endName = lastSite,
            targetOrder = stationno?.toIntOrNull() ?: 0,
            stationName = stationname.orEmpty(),
            nextStationName = "",
            etaText = next?.let { "${it} 发车" },
            etaMinutes = next?.let { minutesUntil(it) },
            desc = null,
            firstTime = firsttime,
            lastTime = lasttime,
            price = ticketprice,
            summaryOverride = "开往 ${lastSite ?: "终点站"}",
        )
    }

    private fun TongdaBusStation.toRouteStation(): RouteStation? {
        val name = stationname?.takeIf { it.isNotBlank() } ?: return null
        val order = stationno?.toIntOrNull() ?: return null
        return RouteStation(
            sId = siteid,
            name = name,
            order = order,
            lat = lat?.toDoubleOrNull(),
            lng = lng?.toDoubleOrNull(),
        )
    }

    private fun normalizeLineNo(raw: String?): String =
        raw?.trim()?.trimEnd('·')?.trim().orEmpty()

    private fun nowHm(): String {
        val calendar = Calendar.getInstance()
        return "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun minutesUntil(hm: String): Int? {
        val parts = hm.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val calendar = Calendar.getInstance()
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val target = hour * 60 + minute
        return if (target >= nowMinutes) target - nowMinutes else target + 24 * 60 - nowMinutes
    }

    private fun metersBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    companion object {
        /** 车来了 H5 没有渠道、但有通卡平台的城市。 */
        private val COMPANIES = mapOf(
            "169" to "190918180642923",
        )
        private const val MAX_LINE_PAGES = 20
        private const val ENRICH_CONCURRENCY = 3

        /** 站台坐标与站序里该方向坐标的最大偏差，超过则认为不是同一个站台。 */
        private const val PLATFORM_RADIUS_METERS = 150.0
    }
}
