package com.tuttoposto.prayertimes.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for Aladhan Prayer Times API.
 * 
 * API Documentation: https://aladhan.com/prayer-times-api
 * 
 * We use the /timings/{timestamp} endpoint which returns prayer times
 * for a specific Unix timestamp at a given location.
 */
interface AladhanApiService {
    
    /**
     * Get prayer times for a specific date and location.
     * 
     * @param timestamp Unix timestamp (seconds since epoch) for the date
     * @param latitude Latitude of the location
     * @param longitude Longitude of the location
     * @param method Calculation method ID. Using method 2 (ISNA - Islamic Society of North America)
     *               as a reasonable default. Other common methods:
     *               - 1: University of Islamic Sciences, Karachi
     *               - 2: ISNA (Islamic Society of North America)
     *               - 3: Muslim World League
     *               - 4: Umm Al-Qura University, Makkah
     *               - 5: Egyptian General Authority of Survey
     * @param timezonestring The timezone string (e.g., "America/New_York", "Europe/Istanbul")
     * 
     * @return AladhanResponse containing prayer times and metadata
     */
    @GET("timings/{timestamp}")
    suspend fun getPrayerTimes(
        @Path("timestamp") timestamp: Long,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("method") method: Int = 2, // ISNA method as default
        @Query("timezonestring") timezonestring: String
    ): AladhanResponse
    
    companion object {
        const val BASE_URL = "https://api.aladhan.com/v1/"
    }
}

