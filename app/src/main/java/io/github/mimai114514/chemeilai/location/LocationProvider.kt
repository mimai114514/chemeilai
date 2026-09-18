package io.github.mimai114514.chemeilai.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Success(val location: Location) : LocationResult

    data object PermissionMissing : LocationResult

    data object ServicesDisabled : LocationResult

    data object Unavailable : LocationResult
}

class LocationProvider(private val context: Context) {

    private val manager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean =
        hasFinePermission() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching { manager.isLocationEnabled }.getOrDefault(false)
        }
        return PROVIDERS.any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return candidateProviders()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * 获取当前位置。厂商/手表上 GPS 冷启动可能较慢，因此：
     * - 直接用足够新的 lastKnown 秒回
     * - 否则并发请求多个 provider，取第一个返回的结果
     * - 超时后回退到任意 lastKnown
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(timeoutMillis: Long = 25_000): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionMissing
        if (!isLocationEnabled()) return LocationResult.ServicesDisabled

        lastKnown()?.let { known ->
            if (System.currentTimeMillis() - known.time < FRESH_WINDOW_MS) {
                return LocationResult.Success(known)
            }
        }

        val providers = candidateProviders()
        val first = if (providers.isEmpty()) null else awaitFirstFix(providers, timeoutMillis)
        val fallback = first ?: lastKnown()
        return if (fallback != null) LocationResult.Success(fallback) else LocationResult.Unavailable
    }

    private fun candidateProviders(): List<String> {
        val fine = hasFinePermission()
        val preferred = buildList {
            if (fine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return preferred.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFirstFix(
        providers: List<String>,
        timeoutMillis: Long,
    ): Location? = withTimeoutOrNull(timeoutMillis) {
        coroutineScope {
            val results = Channel<Location>(Channel.CONFLATED)
            val jobs = providers.map { provider ->
                launch(Dispatchers.Main) {
                    val location = requestSingleFix(provider)
                    if (location != null) results.send(location)
                }
            }
            val first = results.receive()
            jobs.forEach { it.cancel() }
            first
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val submitted = runCatching {
                    manager.getCurrentLocation(
                        provider,
                        null,
                        ContextCompat.getMainExecutor(context),
                    ) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }.isSuccess
                if (!submitted && continuation.isActive) continuation.resume(null)
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                val started = runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.isSuccess
                if (started) {
                    continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                } else if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private companion object {
        val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        const val FRESH_WINDOW_MS = 120_000L
    }
}
