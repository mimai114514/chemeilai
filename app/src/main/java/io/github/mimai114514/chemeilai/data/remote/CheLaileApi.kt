package io.github.mimai114514.chemeilai.data.remote

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.QueryMap

interface CheLaileApi {

    @GET("cdatasource/citylist")
    suspend fun cityList(
        @QueryMap params: Map<String, String>,
    ): CityListResponse

    @GET("cdatasource/citylist")
    suspend fun allCities(
        @QueryMap params: Map<String, String>,
    ): AllCitiesResponse

    @GET("api/bus/stop!nearPhysicalStns.action")
    suspend fun nearPhysicalStations(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @GET("api/bus/stop!encryptedNearlines.action")
    suspend fun encryptedNearLines(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @GET("api/bus/stop!encryptedStnDetail.action")
    suspend fun encryptedStationDetail(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @GET("api/bus/line!encryptedLineDetail.action")
    suspend fun encryptedLineDetail(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @GET("api/bus/line!lineRoute.action")
    suspend fun lineRoute(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @GET("api/bus/cityLineList")
    suspend fun cityLineList(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @Headers("Accept: text/plain,*/*")
    @GET("api/basesearch/client/clientSearch.action")
    suspend fun clientSearch(
        @QueryMap params: Map<String, String>,
    ): Envelope

    @Headers("Accept: text/plain,*/*")
    @GET("api/basesearch/client/clientSearchList.action")
    suspend fun clientSearchList(
        @QueryMap params: Map<String, String>,
    ): Envelope
}
