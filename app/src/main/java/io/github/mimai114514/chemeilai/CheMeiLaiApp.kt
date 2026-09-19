package io.github.mimai114514.chemeilai

import android.app.Application
import android.content.Context
import io.github.mimai114514.chemeilai.data.local.CheMeiLaiDatabase
import io.github.mimai114514.chemeilai.data.local.LocationStore
import io.github.mimai114514.chemeilai.data.local.SessionStore
import io.github.mimai114514.chemeilai.data.remote.CheLaileApiFactory
import io.github.mimai114514.chemeilai.data.repository.CheLaileRepository
import io.github.mimai114514.chemeilai.data.tongda.TongdaApiFactory
import io.github.mimai114514.chemeilai.data.tongda.TongdaSource
import io.github.mimai114514.chemeilai.location.LocationProvider
import io.github.mimai114514.chemeilai.location.LocationResolver
import kotlinx.serialization.json.Json

class CheMeiLaiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val locationProvider: LocationProvider = LocationProvider(context)

    val locationStore: LocationStore = LocationStore(context)

    val locationResolver: LocationResolver = LocationResolver(locationProvider, locationStore)

    val repository: CheLaileRepository = CheLaileRepository(
        api = CheLaileApiFactory.create(json),
        dao = CheMeiLaiDatabase.get(context).dao(),
        session = SessionStore(context),
        json = json,
        tongda = TongdaSource(TongdaApiFactory.create(json)),
    )
}
