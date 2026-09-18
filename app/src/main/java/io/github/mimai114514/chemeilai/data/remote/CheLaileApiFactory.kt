package io.github.mimai114514.chemeilai.data.remote

import io.github.mimai114514.chemeilai.BuildConfig
import io.github.mimai114514.chemeilai.core.CheLaileCrypto
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object CheLaileApiFactory {

    const val HOST = "https://web.chelaile.net.cn"
    const val BASE_URL = "$HOST/"
    const val SRC = "wechat_shaoguan"
    const val API_VERSION = "9.1.2"
    const val CITYLIST_VERSION = "3.80.0"
    const val GPSTYPE = "wgs"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"

    fun create(json: Json): CheLaileApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .addInterceptor(MarkerStrippingInterceptor())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CheLaileApi::class.java)
    }

    private class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request().newBuilder()
                .header("Referer", "$HOST/customer_ch5/?1=1&randomTime=${System.currentTimeMillis()}&src=$SRC")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()
            return chain.proceed(request)
        }
    }

    private class MarkerStrippingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val response = chain.proceed(chain.request())
            val body = response.body ?: return response
            val contentType = body.contentType()
            val raw = body.string()
            val stripped = CheLaileCrypto.stripMarkers(raw)
            return response.newBuilder()
                .body(stripped.toResponseBody(contentType))
                .build()
        }
    }
}
