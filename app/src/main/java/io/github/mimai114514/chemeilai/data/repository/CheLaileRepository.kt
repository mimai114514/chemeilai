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
import io.github.mimai114514.chemeilai.data.model.CityOption
import io.github.mimai114514.chemeilai.data.model.Favorite
import io.github.mimai114514.chemeilai.data.model.FavoriteType
import io.github.mimai114514.chemeilai.data.model.LineDetail
import io.github.mimai114514.chemeilai.data.model.LineLocator
import io.github.mimai114514.chemeilai.data.model.Nearby
import io.github.mimai114514.chemeilai.data.model.NearbyLine
import io.github.mimai114514.chemeilai.data.model.NearbyStop
import io.github.mimai114514.chemeilai.data.model.Poi
import io.github.mimai114514.chemeilai.data.model.Realtime
import io.github.mimai114514.chemeilai.data.model.RouteStation
import io.github.mimai114514.chemeilai.data.model.SearchLine
import io.github.mimai114514.chemeilai.data.model.SearchResult
import io.github.mimai114514.chemeilai.data.model.Station
import io.github.mimai114514.chemeilai.data.model.StationDetail
import io.github.mimai114514.chemeilai.data.model.StationLine
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.ceil
import kotlin.math.roundToInt

class CheLaileRepository(
    private val api: CheLaileApi,
    private val dao: CheMeiLaiDao,
    private val session: SessionStore,
    private val json: Json,
) {

    suspend fun lastCity(): City? = dao.latestCity()?.let { City(it.cityId, it.cityName) }

    @Volatile
    private var allCitiesCache: List<CityOption>? = null

    suspend fun manualCity(): City? = session.manualCity()?.let { (id, name) ->
        City(id, name.ifBlank { null })
    }

    suspend fun currentCity(): City? = manualCity() ?: lastCity()

    suspend fun selectCity(city: City) = session.setManualCity(city.cityId, city.cityName.orEmpty())

    suspend fun clearManualCity() = session.clearManualCity()

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
        dao.observeFavorites().map { list -> list.map { it.toFavorite() } }

    suspend fun favorites(): List<Favorite> = dao.favorites().map { it.toFavorite() }

    suspend fun isFavorite(id: String): Boolean = dao.isFavorite(id)

    suspend fun addFavorite(favorite: Favorite) = dao.upsertFavorite(favorite.toEntity())

    suspend fun removeFavorite(id: String) = dao.deleteFavorite(id)

    suspend fun search(keyword: String, lat: Double? = null, lng: Double? = null): SearchResult {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            return SearchResult(emptyList(), emptyList(), emptyList())
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

    suspend fun lineRoute(
        lineId: String,
        displayName: String,
        direction: Int,
        startName: String? = null,
        endName: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ): LineDetail {
        val cityId = currentCity()?.cityId
        val dto: LineRouteDto = plain(
            call = api::lineRoute,
            biz = mapOf("lineId" to lineId),
            cityId = cityId,
            lat = lat,
            lng = lng,
        )
        val meta = runCatching { lineMeta(lineId, cityId, lat, lng) }.getOrNull()
        return LineDetail(
            lineId = lineId,
            displayName = displayName.ifBlank { displayLineName(meta?.lineNo, meta?.name) },
            startName = startName ?: meta?.startSn,
            endName = endName ?: meta?.endSn,
            firstTime = meta?.firstTime,
            lastTime = meta?.lastTime,
            price = meta?.price,
            stations = dto.stations.mapNotNull { it.toRouteStation() },
        )
    }

    private suspend fun lineMeta(
        lineId: String,
        cityId: String?,
        lat: Double?,
        lng: Double?,
    ): LineDto? {
        val dto: CityLineListDto = plain(api::cityLineList, emptyMap(), cityId, lat, lng)
        return dto.allLines.values.flatten().firstOrNull { it.lineId == lineId }
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
        val dto = encrypted<NearLinesDto>(
            call = api::encryptedNearLines,
            biz = listOf("cityState" to "2"),
            cityId = city.cityId,
            lat = lat,
            lng = lng,
        )
        val stops = dto.nearLines.map { stop ->
            NearbyStop(
                sId = stop.sId.orEmpty(),
                name = stop.sn.orEmpty(),
                distanceMeters = stop.distance?.roundToInt(),
                lat = stop.lat,
                lng = stop.lng,
                lines = stop.lines.mapNotNull { it.toNearbyLine(stop.sn) },
            )
        }
        cacheStations(city.cityId, stops)
        return Nearby(city, stops)
    }

    suspend fun stationDetail(
        stationId: String,
        lat: Double? = null,
        lng: Double? = null,
        cityId: String? = null,
    ): StationDetail {
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
        val lines = dto.lines.mapNotNull { entry -> entry.toStationLine(stationName) }
        if (lines.isNotEmpty()) {
            val now = System.currentTimeMillis()
            dao.upsertLocators(
                lines.map { line ->
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
            lines = lines,
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
        )
    }

    private suspend fun locator(stationId: String, lineNo: String, direction: Int): LineLocator? =
        dao.locator(stationId, lineNo, direction)?.let { entity ->
            LineLocator(
                stationId = entity.stationId,
                lineNo = entity.lineNo,
                lineId = entity.lineId,
                lineName = entity.lineName,
                direction = entity.direction,
                targetOrder = entity.targetOrder,
                stationName = entity.stationName.orEmpty(),
                nextStationName = entity.nextStationName.orEmpty(),
            )
        }

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

    private fun StationLineEntryDto.toNearbyLine(fallbackStation: String?): NearbyLine? {
        val line = line ?: return null
        val lineId = line.lineId ?: return null
        return NearbyLine(
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
        )
    }

    private fun StationLineEntryDto.toStationLine(fallbackStation: String?): StationLine? {
        val line = line ?: return null
        val lineId = line.lineId ?: return null
        return StationLine(
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
            desc = line.desc ?: line.shortDesc,
            firstTime = line.firstTime,
            lastTime = line.lastTime,
            price = line.price,
        )
    }

    private fun FavoriteEntity.toFavorite(): Favorite = Favorite(
        id = id,
        type = runCatching { FavoriteType.valueOf(type) }.getOrDefault(FavoriteType.ROUTE),
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
