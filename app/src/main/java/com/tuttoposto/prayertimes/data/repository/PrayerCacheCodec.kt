package com.tuttoposto.prayertimes.data.repository

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tuttoposto.prayertimes.data.models.PrayerTime
import com.tuttoposto.prayertimes.data.models.PrayerTimesCache
import java.time.LocalDate
import java.time.ZoneId

@JsonClass(generateAdapter = true)
data class StoredPrayerDay(
    val date: String,
    val timezoneId: String,
    val latitude: Double,
    val longitude: Double,
    val prayers: List<PrayerTime>,
    val hijriDate: String?,
    val fetchedAtMillis: Long = 0
)

/** Only API/legacy source days are persisted; projected fallback dates never are. */
object PrayerCacheCodec {
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<StoredPrayerDay>>(Types.newParameterizedType(List::class.java, StoredPrayerDay::class.java))

    fun encode(days: List<PrayerTimesCache>): String {
        require(days.none { it.isFallback })
        return adapter.toJson(days.map {
            StoredPrayerDay(it.date.toString(), it.timezoneId, it.latitude, it.longitude,
                it.prayers, it.hijriDate, it.fetchedAtMillis)
        })
    }

    fun decode(json: String): List<PrayerTimesCache> = (adapter.fromJson(json) ?: emptyList()).map {
        ZoneId.of(it.timezoneId)
        require(it.prayers.size == 5 && it.prayers.all { prayer -> prayer.endTimeMillis > prayer.startTimeMillis })
        PrayerTimesCache(LocalDate.parse(it.date), it.timezoneId, it.latitude, it.longitude,
            it.prayers, it.hijriDate, fetchedAtMillis = it.fetchedAtMillis)
    }
}
