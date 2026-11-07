package com.example.glasslauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class Weather(
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val city: String?,
    val fahrenheit: Boolean,
    val fetchedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("temp", temp).put("code", code).put("isDay", isDay)
        .put("high", high).put("low", low).put("city", city ?: "")
        .put("f", fahrenheit).put("at", fetchedAt)
        .toString()

    companion object {
        fun fromJson(s: String?): Weather? = runCatching {
            val o = JSONObject(s!!)
            Weather(
                o.getDouble("temp"), o.getInt("code"), o.getBoolean("isDay"),
                o.getDouble("high"), o.getDouble("low"),
                o.optString("city").ifBlank { null }, o.optBoolean("f"), o.getLong("at"),
            )
        }.getOrNull()
    }
}

/** WMO weather code → emoji. */
fun weatherEmoji(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "🌤️" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67, in 80..82 -> "🌧️"
    in 71..77, 85, 86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

/** WMO weather code → short description (English, Turkish). */
fun weatherText(code: Int): Pair<String, String> = when (code) {
    0 -> "Clear" to "Açık"
    1 -> "Mostly clear" to "Çoğunlukla açık"
    2 -> "Partly cloudy" to "Parçalı bulutlu"
    3 -> "Cloudy" to "Bulutlu"
    45, 48 -> "Fog" to "Sis"
    in 51..57 -> "Drizzle" to "Çisenti"
    in 61..67 -> "Rain" to "Yağmur"
    in 71..77 -> "Snow" to "Kar"
    in 80..82 -> "Showers" to "Sağanak"
    85, 86 -> "Snow showers" to "Kar sağanağı"
    in 95..99 -> "Thunderstorm" to "Gök gürültülü fırtına"
    else -> "Weather" to "Hava"
}

/**
 * Free weather from Open-Meteo (open-meteo.com, no API key; data under CC BY 4.0)
 * for the device's approximate location.
 */
object WeatherSource {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun location(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            val loc = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        lm.getCurrentLocation(p, signal, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
            if (loc != null) return loc
        }
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    suspend fun fetch(context: Context, fahrenheit: Boolean): Weather? {
        val loc = location(context) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val lat = String.format(Locale.US, "%.3f", loc.latitude)
                val lon = String.format(Locale.US, "%.3f", loc.longitude)
                val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day" +
                        "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto$unit"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                val body = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                val o = JSONObject(body)
                val cur = o.getJSONObject("current")
                val daily = o.getJSONObject("daily")
                @Suppress("DEPRECATION")
                val city = runCatching {
                    Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                        ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                }.getOrNull()
                Weather(
                    temp = cur.getDouble("temperature_2m"),
                    code = cur.getInt("weather_code"),
                    isDay = cur.optInt("is_day", 1) == 1,
                    high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                    low = daily.getJSONArray("temperature_2m_min").getDouble(0),
                    city = city,
                    fahrenheit = fahrenheit,
                    fetchedAt = System.currentTimeMillis(),
                )
            }.getOrNull()
        }
    }
}
