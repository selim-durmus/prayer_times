package com.tuttoposto.prayertimes.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response models for Aladhan Prayer Times API.
 * API Documentation: https://aladhan.com/prayer-times-api
 * 
 * We use the /timings endpoint which returns prayer times for a specific date and location.
 */

@JsonClass(generateAdapter = true)
data class AladhanResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: AladhanData
)

@JsonClass(generateAdapter = true)
data class AladhanData(
    @Json(name = "timings") val timings: AladhanTimings,
    @Json(name = "date") val date: AladhanDate,
    @Json(name = "meta") val meta: AladhanMeta
)

@JsonClass(generateAdapter = true)
data class AladhanTimings(
    @Json(name = "Fajr") val fajr: String,
    @Json(name = "Sunrise") val sunrise: String,
    @Json(name = "Dhuhr") val dhuhr: String,
    @Json(name = "Asr") val asr: String,
    @Json(name = "Sunset") val sunset: String,
    @Json(name = "Maghrib") val maghrib: String,
    @Json(name = "Isha") val isha: String,
    @Json(name = "Imsak") val imsak: String,
    @Json(name = "Midnight") val midnight: String,
    @Json(name = "Firstthird") val firstThird: String? = null,
    @Json(name = "Lastthird") val lastThird: String? = null
)

@JsonClass(generateAdapter = true)
data class AladhanDate(
    @Json(name = "readable") val readable: String,
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "gregorian") val gregorian: AladhanGregorianDate
)

@JsonClass(generateAdapter = true)
data class AladhanGregorianDate(
    @Json(name = "date") val date: String,
    @Json(name = "format") val format: String,
    @Json(name = "day") val day: String,
    @Json(name = "weekday") val weekday: AladhanWeekday,
    @Json(name = "month") val month: AladhanMonth,
    @Json(name = "year") val year: String
)

@JsonClass(generateAdapter = true)
data class AladhanWeekday(
    @Json(name = "en") val en: String
)

@JsonClass(generateAdapter = true)
data class AladhanMonth(
    @Json(name = "number") val number: Int,
    @Json(name = "en") val en: String
)

@JsonClass(generateAdapter = true)
data class AladhanMeta(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "timezone") val timezone: String,
    @Json(name = "method") val method: AladhanMethod
)

@JsonClass(generateAdapter = true)
data class AladhanMethod(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

