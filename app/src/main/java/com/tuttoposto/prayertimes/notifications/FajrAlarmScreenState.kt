package com.tuttoposto.prayertimes.notifications

import com.tuttoposto.prayertimes.data.models.NotificationStyle
import com.tuttoposto.prayertimes.data.models.Prayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FajrAlarmPresentation {
    fun usesAlarmScreen(prayer: Prayer?, separateWakeUp: Boolean, style: NotificationStyle): Boolean =
        prayer == Prayer.FAJR && separateWakeUp && style == NotificationStyle.ALARMY
}

data class FajrAlarmScreenSession(val token: String, val sunriseMillis: Long, val preview: Boolean)

/** Service-owned live state. A stale notification can never resurrect a stopped alarm. */
object FajrAlarmScreenState {
    private val mutableSession = MutableStateFlow<FajrAlarmScreenSession?>(null)
    val session = mutableSession.asStateFlow()
    internal fun publish(value: FajrAlarmScreenSession?) { mutableSession.value = value }
}
