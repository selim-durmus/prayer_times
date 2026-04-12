package com.tuttoposto.prayertimes.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Aladhan Prayer Times API interface.
 * Documentation: https://aladhan.com/prayer-times-api
 * 
 * We use the /timings endpoint to get prayer times for a specific day
 * based on latitude/longitude coordinates.
 */
interface AladhanApi {
    
    /**
     * Get prayer times for today at a specific location.
     * 
     * @param timestamp Unix timestamp (seconds) for the date
     * @param latitude Device latitude
     * @param longitude Device longitude
     * @param method Calculation method (default: 2 = Islamic Society of North America)
     * @param school Asr juristic method (0 = Shafi/standard, 1 = Hanafi)
     * 
     * Available methods:
     * 0 - Shia Ithna-Ashari
     * 1 - University of Islamic Sciences, Karachi
     * 2 - Islamic Society of North America (ISNA)
     * 3 - Muslim World League
     * 4 - Umm Al-Qura University, Makkah
     * 5 - Egyptian General Authority of Survey
     * 7 - Institute of Geophysics, University of Tehran
     * 8 - Gulf Region
     * 9 - Kuwait
     * 10 - Qatar
     * 11 - Majlis Ugama Islam Singapura
     * 12 - Union Organization Islamic de France
     * 13 - Diyanet İşleri Başkanlığı, Turkey
     * 14 - Spiritual Administration of Muslims of Russia
     */
    @GET("timings")
    suspend fun getTimings(
        @Query("date_or_timestamp") timestamp: Long,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 2,
        @Query("school") school: Int = 1
    ): AladhanResponse
    
    @GET("calendar/{year}/{month}")
    suspend fun getCalendar(
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 2,
        @Query("school") school: Int = 1
    ): AladhanCalendarResponse
    
    companion object {
        const val BASE_URL = "https://api.aladhan.com/v1/"
    }
}

/**
 * Response wrapper from Aladhan API
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

/**
 * Prayer timings from API.
 * Times are in HH:mm format (24-hour) in the local timezone.
 * Some timings include timezone offset like "05:30 (PKT)" - we strip that.
 */
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
data class AladhanCalendarResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: List<AladhanData>
)

@JsonClass(generateAdapter = true)
data class AladhanDate(
    @Json(name = "readable") val readable: String,
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "gregorian") val gregorian: AladhanGregorian,
    @Json(name = "hijri") val hijri: AladhanHijri? = null
)

@JsonClass(generateAdapter = true)
data class AladhanHijri(
    @Json(name = "day") val day: String,
    @Json(name = "month") val month: AladhanHijriMonth,
    @Json(name = "year") val year: String
) {
    fun formatted(): String = "$day ${month.en} $year"
}

@JsonClass(generateAdapter = true)
data class AladhanHijriMonth(
    @Json(name = "number") val number: Int,
    @Json(name = "en") val en: String
)

@JsonClass(generateAdapter = true)
data class AladhanGregorian(
    @Json(name = "date") val date: String,
    @Json(name = "day") val day: String,
    @Json(name = "month") val month: AladhanMonth,
    @Json(name = "year") val year: String
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

