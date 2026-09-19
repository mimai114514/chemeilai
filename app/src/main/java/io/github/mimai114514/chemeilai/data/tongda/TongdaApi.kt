package io.github.mimai114514.chemeilai.data.tongda

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

interface TongdaApi {

    @FormUrlEncoded
    @POST("mobile/member/getLocalSite.koala")
    suspend fun getLocalSite(@Field("params") params: String): TongdaResponse<List<TongdaSite>>

    @FormUrlEncoded
    @POST("mobile/member/getLocalRoadSite.koala")
    suspend fun getLocalRoadSite(@Field("params") params: String): TongdaResponse<List<TongdaRoadSite>>

    @FormUrlEncoded
    @POST("mobile/member/getRoadInfoSum.koala")
    suspend fun getRoadInfoSum(@Field("params") params: String): TongdaLineList

    @FormUrlEncoded
    @POST("mobile/member/getRoadState.koala")
    suspend fun getRoadState(@Field("params") params: String): TongdaLineList

    @FormUrlEncoded
    @POST("mobile/member/getBusInfo.koala")
    suspend fun getBusInfo(@Field("params") params: String): TongdaResponse<TongdaBusInfo>
}

object TongdaApiFactory {

    const val BASE_URL = "https://yourbus.tongda.cc/"
    private const val APP_TAG = "cc.busonline.mobile.i32795"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; ) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/151.0.7922.199 Mobile Safari/537.36"

    fun create(json: Json): TongdaApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor())
            .addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = if (io.github.mimai114514.chemeilai.BuildConfig.DEBUG) {
                        okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                    } else {
                        okhttp3.logging.HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TongdaApi::class.java)
    }

    private class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("Origin", "https://mobile.busonline.cc")
                .header("Referer", "https://mobile.busonline.cc/")
                .header("X-Requested-With", APP_TAG)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .build()
            return chain.proceed(request)
        }
    }
}
