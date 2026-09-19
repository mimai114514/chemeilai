package io.github.mimai114514.chemeilai.data.repository

import io.github.mimai114514.chemeilai.core.ApiException
import io.github.mimai114514.chemeilai.core.CheLaileCrypto
import io.github.mimai114514.chemeilai.data.local.CheMeiLaiDao
import io.github.mimai114514.chemeilai.data.local.CityEntity
import io.github.mimai114514.chemeilai.data.local.FavoriteEntity
import io.github.mimai114514.chemeilai.data.local.LineLocatorEntity
import io.github.mimai114514.chemeilai.data.local.SessionStore
import io.github.mimai114514.chemeilai.data.local.StationEntity
import io.github.mimai114514.chemeilai.data.model.BusEta
import io.github.mimai114514.chemeilai.data.model.City
import io.github.mimai114514.chemeilai.data.model.CityLine
import io.github.mimai114514.chemeilai.data.model.CityOption
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.LineArrival
import io.github.mimai114514.chemeilai.data.model.LineDirection
import io.github.mimai114514.chemeilai.data.model.LineLocator
import io.github.mimai114514.chemeilai.data.model.Nearby
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.data.model.NearestStation
import io.github.mimai114514.chemeilai.data.model.Poi
import io.github.mimai114514.chemeilai.data.model.Realtime
import io.github.mimai114514.chemeilai.data.model.RouteStation
import io.github.mimai114514.chemeilai.data.model.SearchLine
import io.github.mimai114514.chemeilai.data.model.SearchResult
import io.github.mimai114514.chemeilai.data.model.Station
import io.github.mimai114514.chemeilai.data.model.StationDetail
import io.github.mimai114514.chemeilai.data.model.StationLineGroup
import io.github.mimai114514.chemeilai.data.model.applyFavorites
import io.github.mimai114514.chemeilai.data.remote.CheLaileApi
import io.github.mimai114514.chemeilai.data.remote.CheLaileApiFactory
import io.github.mimai114514.chemeilai.data.remote.CityLineListDto
import io.github.mimai114514.chemeilai.data.remote.CityOptionDto
import io.github.mimai114514.chemeilai.data.remote.ClientSearchDto
import io.github.mimai114514.chemeilai.data.remote.Envelope
import io.github.mimai114514.chemeilai.data.remote.LineDetailDto
import io.github.mimai114514.chemeilai.data.remote.LineDto
import io.github.mimai114514.chemeilai.data.remote.LineRouteDto
import io.github.mimai114514.chemeilai.data.remote.NearLinesDto
import io.github.mimai114514.chemeilai.data.remote.NearStationDto
import io.github.mimai114514.chemeilai.data.remote.PoiDto
import io.github.mimai114514.chemeilai.data.remote.RouteStationDto
import io.github.mimai114514.chemeilai.data.remote.SearchLineDto
import io.github.mimai114514.chemeilai.data.remote.StationDetailDto
import io.github.mimai114514.chemeilai.data.remote.StationLineEntryDto
import io.github.mimai114514.chemeilai.data.remote.StnStateDto
import io.github.mimai114514.chemeilai.data.remote.requireData
import io.github.mimai114514.chemeilai.data.tongda.TongdaSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ceil
import kotlin.math.roundToInt

class CheLaileRepository(
    private val api: CheLaileApi,
    private val dao: CheMeiLaiDao,
    private val session: SessionStore,
    private val json: Json,
    private val tongda: TongdaSource,
) {

    private suspend fun tongdaCompany(): String? = tongda.companyFor(currentCity()?.cityId)

    suspend fun lastCity(): City? = dao.latestCity()?.let { City(it.cityId, it.cityName) }

    @Volatile
    private var allCitiesCache: List<CityOption>? = null

    @Volatile
    private var cityLinesCache: List<CityLine>? = null

    private val routeStationsCache = java.util.concurrent.ConcurrentHashMap<String, List<RouteStation>>()

    suspend fun manualCity(): City? = session.manualCity()?.let { (id, name) ->
        City(id, name.ifBlank { null })
    }

    suspend fun currentCity(): City? = manualCity() ?: lastCity()

    suspend fun selectCity(city: City) = session.setManualCity(city.cityId, city.cityName.orEmpty())

    suspend fun clearManualCity() = session.clearManualCity()

    suspend fun startDestination(): String = session.startDestination()

    suspend fun setStartDestination(value: String) = session.setStartDestination(value)

    suspend fun allCities(): List<CityOption> {
        allCitiesCache?.let { return it }
        val params = linkedMapOf(
            "type" to "all",
            "s" to "android",
            "v" to CheLaileApiFactory.CITYLIST_VERSION,
            "src" to "webapp_default",
            "userId" to "",
        )
        val cities = api.allCities(params).data?.allRealtimeCity.orEmpty().mapNotNull { it.toCityOption() }
        if (cities.isNotEmpty()) allCitiesCache = cities
        return cities
    }

    fun observeFavorites(): Flow<List<Favorite>> =
        dao.observeFavorites().map { list -> list.mapNotNull { it.toFavoriteOrNull() } }

    /** 仅关注收藏线路的线路名，供主页/站点页实时同步收藏状态。 */
    fun observeFavoriteLineNames(): Flow<Set<String>> =
        dao.observeFavorites()
            .map { list ->
                list.filter { it.type == FavoriteType.LINE.name }
                    .mapNotNull { it.lineName }
                    .toSet()
            }

    suspend fun favorites(): List<Favorite> = dao.favorites().mapNotNull { it.toFavoriteOrNull() }

    suspend fun isFavorite(id: String): Boolean = dao.isFavorite(id)

    suspend fun addFavorite(favorite: Favorite) = dao.upsertFavorite(favorite.toEntity())

    suspend fun removeFavorite(id: String) = dao.deleteFavorite(id)

    suspend fun search(keyword: String, lat: Double? = null, lng: Double? = null): SearchResult {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            return SearchResult(emptyList(), emptyList(), emptyList())
        }
        tongdaCompany()?.let { company ->
            return tongda.search(company, trimmed, lat, lng)
        }
        val dto: ClientSearchDto = plain(
            call = api::clientSearch,
            biz = mapOf("key" to trimmed, "count" to SEARCH_COUNT),
            cityId = currentCity()?.cityId,
            lat = lat,
            lng = lng,
        )
        return SearchResult(
            stations = dto.stations.mapNotNull { it.toStation() },
            lines = dto.lines.mapNotNull { it.toSearchLine() },
            pois = dto.pois.mapNotNull { it.toPoi() },
        )
    }

    /** cityLineList 全量线路（含方向与首末班），用于线路详情的换向。 */
    suspend fun cityLines(forceRefresh: Boolean = false): List<CityLine> {
        if (!forceRefresh) cityLinesCache?.let { return it }
        val dto: CityLineListDto = plain(
            call = api::cityLineList,
            biz = emptyMap(),
            cityId = currentCity()?.cityId,
            lat = null,
            lng = null,
        )
        val lines = dto.allLines.values.flatten()
            .mapNotNull { it.toCityLine() }
            .distinctBy { it.lineId }
        if (lines.isNotEmpty()) cityLinesCache = lines
        return lines
    }

    /** 同一条线路的所有方向；lineName 为展示名，lineNo 为原始编码（可选，用于兜底匹配）。 */
    suspend fun lineDirections(lineName: String, lineNo: String? = null): List<CityLine> {
        val key = lineName.trim()
        if (key.isEmpty()) return emptyList()
        tongdaCompany()?.let { company ->
            val lines = tongda.lineDirections(company, key)
            if (lines.isNotEmpty()) return lines
        }
        val matches = cityLines().filter {
            it.displayName == key || it.lineNo == key || (lineNo != null && it.lineNo == lineNo)
        }
        // cityLineList 不返回 direction，按顺序编号即可（界面用起终点区分）
        return matches.mapIndexed { index, line -> line.copy(direction = index) }
    }

    suspend fun lineRouteStations(
        lineId: String,
        lat: Double? = null,
        lng: Double? = null,
    ): List<RouteStation> {
        routeStationsCache[lineId]?.let { return it }
        val dto: LineRouteDto = plain(
            call = api::lineRoute,
            biz = mapOf("lineId" to lineId),
            cityId = currentCity()?.cityId,
            lat = lat,
            lng = lng,
        )
        val stations = dto.stations.mapNotNull { it.toRouteStation() }
        if (stations.isNotEmpty()) routeStationsCache[lineId] = stations
        return stations
    }

    /** 该线路每个方向上离 (lat,lng) 最近的站台。 */
    suspend fun lineNearestStations(lineName: String, lat: Double, lng: Double): List<NearestStation> {
        tongdaCompany()?.let { company ->
            val nearest = tongda.lineNearestStations(company, lineName, lat, lng)
            if (nearest.isNotEmpty()) return nearest
        }
        val directions = lineDirections(lineName)
        return directions.mapNotNull { direction ->
            val stations = runCatching { lineRouteStations(direction.lineId, lat, lng) }.getOrNull().orEmpty()
            val scored = stations.mapNotNull { station ->
                val stationId = station.sId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val stationLat = station.lat ?: return@mapNotNull null
                val stationLng = station.lng ?: return@mapNotNull null
                Triple(station, stationId, metersBetween(lat, lng, stationLat, stationLng))
            }
            val nearest = scored.minByOrNull { it.third } ?: return@mapNotNull null
            NearestStation(
                direction = direction.direction,
                lineId = direction.lineId,
                lineNo = direction.lineNo,
                startName = direction.startName,
                endName = direction.endName,
                stationId = nearest.second,
                stationName = nearest.first.name,
                distanceMeters = nearest.third.roundToInt(),
            )
        }
    }

    /** 在指定站台查询该线路的到达信息（lineNo 会用 stationDetail 里的原始编码）。 */
    suspend fun arrivalAt(
        nearest: NearestStation,
        lineName: String,
        lat: Double? = null,
        lng: Double? = null,
    ): LineArrival? {
        tongdaCompany()?.let { company ->
            return tongda.arrivalAt(company, nearest, lat, lng)
        }
        val detail = runCatching { stationDetail(nearest.stationId, lat, lng) }.getOrNull()
        val rawLineNo = detail?.lines
            ?.firstOrNull { it.displayName == lineName }
            ?.directions
            ?.firstOrNull { it.direction == nearest.direction }
            ?.lineNo
            ?: nearest.lineNo
        val realtime = runCatching {
            realtime(nearest.stationId, rawLineNo, nearest.direction, lat, lng)
        }.getOrNull() ?: return null
        return LineArrival(
            stationId = nearest.stationId,
            stationName = realtime.stationName.ifBlank { nearest.stationName },
            lineNo = rawLineNo,
            direction = nearest.direction,
            distanceMeters = nearest.distanceMeters,
            etaMinutes = realtime.buses.firstOrNull()?.etaMinutes,
            tip = realtime.tip,
            buses = realtime.buses,
        )
    }

    /** 无站点上下文的线路实时：用该方向的首站充当接口所需的目标站。 */
    suspend fun lineRealtime(
        lineName: String,
        direction: Int,
        lat: Double? = null,
        lng: Double? = null,
    ): Realtime? {
        tongdaCompany()?.let { company ->
            return tongda.lineRealtime(company, lineName, direction, lat, lng)
        }
        val directions = lineDirections(lineName)
        val target = directions.firstOrNull { it.direction == direction }
            ?: directions.firstOrNull()
            ?: return null
        val stations = runCatching { lineRouteStations(target.lineId, lat, lng) }.getOrNull().orEmpty()
        val stationId = stations.firstOrNull { !it.sId.isNullOrBlank() }?.sId ?: return null
        val detail = runCatching { stationDetail(stationId, lat, lng) }.getOrNull()
        val rawLineNo = detail?.lines
            ?.firstOrNull { it.displayName == lineName }
            ?.directions
            ?.firstOrNull { it.direction == target.direction }
            ?.lineNo
            ?: target.lineNo
        return runCatching {
            realtime(stationId, rawLineNo, target.direction, lat, lng)
        }.getOrNull()
    }

    /** 站点与用户的距离（仅当本地缓存过该站点坐标时可用）。 */
    suspend fun stationDistanceMeters(stationId: String, lat: Double, lng: Double): Int? {
        tongdaCompany()?.let { company ->
            return tongda.stationDistanceMeters(company, stationId, lat, lng)
        }
        val station = dao.station(stationId) ?: return null
        val stationLat = station.lat ?: return null
        val stationLng = station.lng ?: return null
        return metersBetween(lat, lng, stationLat, stationLng).roundToInt()
    }

    suspend fun resolveCity(lat: Double, lng: Double): City {
        val cached = dao.latestCity()
        if (cached != null && isFresh(cached.updatedAt) && metersBetween(cached.lat, cached.lng, lat, lng) < CITY_RADIUS_METERS) {
            return City(cached.cityId, cached.cityName)
        }
        val params = linkedMapOf(
            "type" to "gpsRealtimeCity",
            "lat" to lat.toString(),
            "lng" to lng.toString(),
            "gpstype" to CheLaileApiFactory.GPSTYPE,
            "s" to "android",
            "v" to CheLaileApiFactory.CITYLIST_VERSION,
            "src" to "webapp_default",
            "userId" to "",
        )
        val response = api.cityList(params)
        if (response.status != "OK") {
            throw ApiException("无法根据坐标确定城市")
        }
        val city = response.data?.gpsRealtimeCity
            ?: throw ApiException("无法根据坐标确定城市")
        val cityId = city.cityId?.takeIf { it.isNotBlank() }
            ?: throw ApiException("城市信息缺少 cityId")
        dao.upsertCity(
            CityEntity(
                cityId = cityId,
                cityName = city.cityName ?: city.name,
                lat = lat,
                lng = lng,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return City(cityId, city.cityName ?: city.name)
    }

    suspend fun nearby(lat: Double, lng: Double): Nearby {
        val city = resolveCity(lat, lng)
        tongda.companyFor(city.cityId)?.let { company ->
            return tongda.nearby(company, city, lat, lng, favoriteLineNames(city.cityId))
        }
        val dto = encrypted<NearLinesDto>(
            call = api::encryptedNearLines,
            biz = listOf("cityState" to "2"),
            cityId = city.cityId,
            lat = lat,
            lng = lng,
        )
        val favorites = favoriteLineNames(city.cityId)
        val stops = dto.nearLines.map { stop ->
            NearbyStop(
                sId = stop.sId.orEmpty(),
                name = stop.sn.orEmpty(),
                distanceMeters = stop.distance?.roundToInt(),
                lat = stop.lat,
                lng = stop.lng,
                lines = stop.lines.mapNotNull { it.toLineDirection(stop.sn) }.toGroups(favorites),
            )
        }
        // nearLines 只返回部分线路，用站点详情补全每个站的完整线路表
        val enriched = coroutineScope {
            stops.map { stop -> async { enrichStop(stop, city.cityId) } }.awaitAll()
        }
        cacheStations(city.cityId, enriched)
        return Nearby(city, enriched)
    }

    private suspend fun enrichStop(stop: NearbyStop, cityId: String): NearbyStop {
        if (stop.sId.isBlank()) return stop
        val detail = runCatching {
            stationDetail(stop.sId, stop.lat, stop.lng, cityId)
        }.getOrNull() ?: return stop
        if (detail.lines.isEmpty()) return stop
        return stop.copy(lines = detail.lines)
    }

    suspend fun stationDetail(
        stationId: String,
        lat: Double? = null,
        lng: Double? = null,
        cityId: String? = null,
    ): StationDetail {
        tongdaCompany()?.let { company ->
            return tongda.stationDetail(
                companyNo = company,
                stationId = stationId,
                fallbackName = dao.station(stationId)?.name,
                lat = lat,
                lng = lng,
                favoriteLineNames = favoriteLineNames(currentCity()?.cityId),
            )
        }
        val geo = resolveGeo(stationId, lat, lng)
        val city = cityId ?: currentCity()?.cityId
        val dto = encrypted<StationDetailDto>(
            call = api::encryptedStationDetail,
            biz = listOf("stationId" to stationId, "destSId" to "-1"),
            cityId = city,
            lat = geo.first,
            lng = geo.second,
        )
        val resolvedStationId = dto.sId ?: stationId
        val stationName = dto.sn ?: dao.station(stationId)?.name ?: stationId
        val directions = dto.lines.mapNotNull { it.toLineDirection(stationName) }
        if (directions.isNotEmpty()) {
            val now = System.currentTimeMillis()
            dao.upsertLocators(
                directions.map { line ->
                    LineLocatorEntity(
                        stationId = resolvedStationId,
                        lineNo = line.lineNo,
                        direction = line.direction,
                        lineId = line.lineId,
                        lineName = line.displayName,
                        targetOrder = line.targetOrder,
                        stationName = line.stationName,
                        nextStationName = line.nextStationName,
                        cityId = city,
                        updatedAt = now,
                    )
                },
            )
        }
        return StationDetail(
            sId = resolvedStationId,
            name = stationName,
            distanceMeters = dto.distance?.roundToInt(),
            lines = directions.toGroups(favoriteLineNames(city)),
        )
    }

    suspend fun realtime(
        stationId: String,
        lineNo: String,
        direction: Int,
        lat: Double? = null,
        lng: Double? = null,
        cityId: String? = null,
    ): Realtime {
        tongdaCompany()?.let { company ->
            val detail = tongda.stationDetail(company, stationId, null, lat, lng, emptySet())
            val directions = detail.lines.firstOrNull { it.displayName == lineNo }?.directions.orEmpty()
            val line = directions.firstOrNull { it.direction == direction }
                ?: throw ApiException("找不到该线路在本站的定位信息")
            return tongda.realtime(
                companyNo = company,
                roadId = line.lineId,
                lineNo = lineNo,
                direction = direction,
                lat = lat,
                lng = lng,
                stationName = line.stationName,
                targetOrder = line.targetOrder,
            ).copy(otherDirection = directions.firstOrNull { it.direction != direction }?.direction)
        }
        val locator = locator(stationId, lineNo, direction)
            ?: run {
                stationDetail(stationId, lat, lng, cityId)
                locator(stationId, lineNo, direction)
            }
            ?: throw ApiException("找不到该线路在本站的定位信息")
        val geo = resolveGeo(stationId, lat, lng)
        val dto = encrypted<LineDetailDto>(
            call = api::encryptedLineDetail,
            biz = listOf(
                "lineId" to locator.lineId,
                "lineName" to (locator.lineName ?: locator.lineNo),
                "direction" to locator.direction,
                "stationName" to locator.stationName,
                "nextStationName" to locator.nextStationName,
                "lineNo" to locator.lineNo,
                "targetOrder" to locator.targetOrder,
            ),
            cityId = cityId ?: currentCity()?.cityId,
            lat = geo.first,
            lng = geo.second,
        )
        val line = dto.line
        return Realtime(
            lineDisplayName = displayLineName(line?.lineNo, line?.name).ifBlank { locator.lineNo },
            direction = locator.direction,
            stationName = locator.stationName,
            startName = line?.startSn,
            endName = line?.endSn ?: line?.destinationName,
            targetOrder = dto.targetOrder?.roundToInt() ?: locator.targetOrder,
            tip = dto.tip?.desc ?: line?.desc ?: line?.shortDesc,
            buses = dto.buses.map { bus ->
                BusEta(
                    busId = bus.busId,
                    order = (bus.specialOrder ?: bus.order)?.roundToInt(),
                    distanceMeters = bus.distanceToSc?.takeIf { it >= 0 }?.roundToInt(),
                    etaMinutes = bus.travelTime?.takeIf { it > 0 }?.let { ceil(it / 60.0).toInt() },
                    timeStr = bus.timeStr,
                    state = bus.state?.roundToInt(),
                )
            },
            stations = dto.stations.mapNotNull { station ->
                val name = station.sn ?: return@mapNotNull null
                RouteStation(
                    sId = station.sId,
                    name = name,
                    order = station.order?.roundToInt() ?: return@mapNotNull null,
                    lat = station.lat,
                    lng = station.lng,
                )
            },
            otherDirection = otherDirection(stationId, lineNo, direction),
        )
    }

    private suspend fun favoriteLineNames(cityId: String?): Set<String> =
        dao.favorites()
            .filter {
                it.type == FavoriteType.LINE.name &&
                    (cityId == null || it.cityId == null || it.cityId == cityId)
            }
            .mapNotNull { it.lineName }
            .toSet()

    private suspend fun locator(stationId: String, lineNo: String, direction: Int): LineLocator? =
        dao.locator(stationId, lineNo, direction)?.let { entity -> entity.toLocator() }

    /** 同一条线路在本站的反方向，用于实时页换向。 */
    private suspend fun otherDirection(stationId: String, lineNo: String, direction: Int): Int? =
        dao.locators(stationId, lineNo)
            .firstOrNull { it.direction != direction }
            ?.direction

    private fun LineLocatorEntity.toLocator(): LineLocator =
        LineLocator(
            stationId = stationId,
            lineNo = lineNo,
            lineId = lineId,
            lineName = lineName,
            direction = direction,
            targetOrder = targetOrder,
            stationName = stationName.orEmpty(),
            nextStationName = nextStationName.orEmpty(),
        )

    private suspend fun cacheStations(cityId: String, stops: List<NearbyStop>) {
        if (stops.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertStations(
            stops.map { stop ->
                StationEntity(
                    sId = stop.sId,
                    name = stop.name,
                    lat = stop.lat,
                    lng = stop.lng,
                    distance = stop.distanceMeters?.toDouble(),
                    cityId = cityId,
                    updatedAt = now,
                )
            },
        )
    }

    private suspend fun resolveGeo(stationId: String, lat: Double?, lng: Double?): Pair<Double?, Double?> {
        if (lat != null && lng != null) return lat to lng
        dao.station(stationId)?.let { station ->
            if (station.lat != null && station.lng != null) return station.lat to station.lng
        }
        dao.latestCity()?.let { city -> return city.lat to city.lng }
        return null to null
    }

    private fun StationLineEntryDto.toLineDirection(fallbackStation: String?): LineDirection? {
        val line = line ?: return null
        val lineId = line.lineId ?: return null
        return LineDirection(
            lineId = lineId,
            lineNo = line.lineNo ?: line.name.orEmpty(),
            displayName = displayLineName(line.lineNo, line.name),
            direction = line.direction?.roundToInt() ?: 0,
            startName = line.startSn,
            endName = line.endSn,
            targetOrder = targetStation?.order?.roundToInt() ?: 0,
            stationName = targetStation?.sn ?: fallbackStation.orEmpty(),
            nextStationName = nextStation?.sn.orEmpty(),
            etaText = etaText(stnStates),
            etaMinutes = etaMinutes(stnStates),
            desc = line.desc ?: line.shortDesc,
            firstTime = line.firstTime,
            lastTime = line.lastTime,
            price = line.price,
        )
    }

    private fun List<LineDirection>.toGroups(favoriteLines: Set<String>): List<StationLineGroup> =
        groupBy { it.displayName }
            .map { (name, directions) ->
                StationLineGroup(
                    key = name,
                    displayName = name,
                    directions = directions.sortedBy { it.direction },
                )
            }
            .applyFavorites(favoriteLines)

    private fun FavoriteEntity.toFavoriteOrNull(): Favorite? {
        val parsed = runCatching { FavoriteType.valueOf(type) }.getOrNull() ?: return null
        return Favorite(
            id = id,
            type = parsed,
            stationId = stationId,
            stationName = stationName,
            lineId = lineId,
            lineNo = lineNo,
            lineName = lineName,
            direction = direction,
            startName = startName,
            endName = endName,
            cityId = cityId,
        )
    }

    private fun Favorite.toEntity(): FavoriteEntity = FavoriteEntity(
        id = id,
        type = type.name,
        stationId = stationId,
        stationName = stationName,
        lineId = lineId,
        lineNo = lineNo,
        lineName = lineName,
        direction = direction,
        startName = startName,
        endName = endName,
        cityId = cityId,
        createdAt = System.currentTimeMillis(),
    )

    private fun LineDto.toCityLine(): CityLine? {
        val id = lineId?.takeIf { it.isNotBlank() } ?: return null
        val rawNo = lineNo?.takeIf { it.isNotBlank() } ?: lineName
        val rawName = name?.takeIf { it.isNotBlank() } ?: lineName
        val label = displayLineName(rawNo, rawName).ifBlank { rawName.orEmpty() }
        if (label.isBlank()) return null
        return CityLine(
            lineId = id,
            lineNo = rawNo ?: label,
            displayName = label,
            direction = direction?.roundToInt() ?: 0,
            startName = startSn ?: startStopName,
            endName = endSn ?: endStopName,
            firstTime = firstTime,
            lastTime = lastTime,
            price = price,
        )
    }

    private fun CityOptionDto.toCityOption(): CityOption? {
        val id = cityId?.takeIf { it.isNotBlank() } ?: return null
        val label = cityName?.takeIf { it.isNotBlank() } ?: return null
        return CityOption(
            cityId = id,
            name = label,
            pinyin = pinyin.orEmpty(),
            hot = (hot ?: 0.0) > 0.0,
        )
    }

    private fun NearStationDto.toStation(): Station? {
        val id = sId ?: return null
        return Station(
            sId = id,
            name = sn.orEmpty(),
            distanceMeters = distance?.roundToInt(),
            lat = lat,
            lng = lng,
        )
    }

    private fun SearchLineDto.toSearchLine(): SearchLine? {
        val id = lineId ?: return null
        return SearchLine(
            lineId = id,
            lineNo = lineNo ?: name.orEmpty(),
            displayName = displayLineName(lineNo, name).ifBlank { name.orEmpty() },
            direction = direction?.roundToInt() ?: 0,
            startName = startSn,
            endName = endSn,
        )
    }

    private fun PoiDto.toPoi(): Poi? {
        val label = sn ?: sn1 ?: return null
        return Poi(
            name = label,
            address = address ?: sn1Address ?: adname,
            tag = sn1Tag,
            lat = lat,
            lng = lng,
        )
    }

    private fun RouteStationDto.toRouteStation(): RouteStation? {
        val name = sn ?: return null
        val order = order?.roundToInt() ?: return null
        return RouteStation(sId = sId, name = name, order = order, lat = lat, lng = lng)
    }

    private fun etaText(states: List<StnStateDto>): String? {
        val state = states.firstOrNull() ?: return null
        val value = state.value
        if (value != null && value >= 0) return "${value.roundToInt()}分钟"
        val travel = state.travelTime
        if (travel != null && travel > 0) return "${ceil(travel / 60.0).toInt()}分钟"
        return state.timeStr
    }

    private fun etaMinutes(states: List<StnStateDto>): Int? {
        val state = states.firstOrNull() ?: return null
        state.value?.takeIf { it >= 0 }?.let { return it.roundToInt() }
        state.travelTime?.takeIf { it > 0 }?.let { return ceil(it / 60.0).toInt() }
        return null
    }

    private fun displayLineName(lineNo: String?, name: String?): String {
        val no = lineNo?.trim().orEmpty()
        val label = name?.trim().orEmpty()
        if (no.isEmpty()) return label
        if (label.isEmpty()) return no
        val looksInternalCode = no.first().lowercaseChar() == 'r' ||
            (no.length >= 6 && no.all { it.isDigit() })
        return if (looksInternalCode) label else no
    }

    private suspend fun sharedParams(
        cityId: String?,
        lat: Double?,
        lng: Double?,
    ): LinkedHashMap<String, String> {
        val userId = session.userId()
        val params = LinkedHashMap<String, String>()
        cityId?.takeIf { it.isNotBlank() }?.let { params["cityId"] = it }
        params["s"] = "h5"
        params["v"] = CheLaileApiFactory.API_VERSION
        params["vc"] = "1"
        params["src"] = CheLaileApiFactory.SRC
        params["userId"] = userId
        params["h5Id"] = userId
        params["sign"] = "1"
        if (lat != null && lng != null) {
            val latText = lat.toString()
            val lngText = lng.toString()
            params["lat"] = latText
            params["lng"] = lngText
            params["geo_lat"] = latText
            params["geo_lng"] = lngText
            params["gpstype"] = CheLaileApiFactory.GPSTYPE
        }
        return params
    }

    private suspend inline fun <reified T> plain(
        crossinline call: suspend (Map<String, String>) -> Envelope,
        biz: Map<String, String>,
        cityId: String?,
        lat: Double?,
        lng: Double?,
    ): T {
        val params = LinkedHashMap<String, String>().apply { putAll(biz) }
        params.putAll(sharedParams(cityId, lat, lng))
        return json.decodeFromJsonElement(call(params).requireData())
    }

    private suspend inline fun <reified T> encrypted(
        crossinline call: suspend (Map<String, String>) -> Envelope,
        biz: List<Pair<String, Any?>>,
        cityId: String?,
        lat: Double?,
        lng: Double?,
    ): T {
        val params = LinkedHashMap<String, String>()
        biz.forEach { (key, value) -> if (value != null) params[key] = value.toString() }
        params["cryptoSign"] = CheLaileCrypto.cryptoSign(biz)
        params.putAll(sharedParams(cityId, lat, lng))
        val envelope = call(params)
        val data = envelope.requireData().jsonObject
        val encryptedText = data["encryptResult"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException("接口未返回加密数据")
        val plainText = CheLaileCrypto.decryptAesEcbBase64(encryptedText)
        return json.decodeFromString(plainText)
    }

    private fun isFresh(updatedAt: Long): Boolean =
        System.currentTimeMillis() - updatedAt < CITY_CACHE_WINDOW_MS

    private fun metersBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private companion object {
        const val CITY_RADIUS_METERS = 15_000.0
        const val CITY_CACHE_WINDOW_MS = 6 * 60 * 60 * 1000L
        const val SEARCH_COUNT = "12"
    }
}
