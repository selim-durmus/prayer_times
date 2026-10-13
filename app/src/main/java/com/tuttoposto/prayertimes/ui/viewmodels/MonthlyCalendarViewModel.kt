package com.tuttoposto.prayertimes.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.prayertimes.data.api.AladhanData
import com.tuttoposto.prayertimes.data.api.NetworkModule
import com.tuttoposto.prayertimes.data.repository.PrayerTimesRepository
import com.tuttoposto.prayertimes.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MonthlyCalendarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MonthlyCalendarVM"
    }

    private val api = NetworkModule.aladhanApi
    private val repository = PrayerTimesRepository(application)

    private val _uiState = MutableStateFlow<MonthlyCalendarUiState>(MonthlyCalendarUiState.Loading)
    val uiState: StateFlow<MonthlyCalendarUiState> = _uiState.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private var cachedLocation: Pair<Double, Double>? = null
    private val settingsRepository = SettingsRepository(application)
    // Keyed by Asr school too, so switching Hanafi/Shafi'i never shows the other school's times.
    private val monthCache = mutableMapOf<Pair<YearMonth, Int>, List<DayPrayerTimes>>()

    fun loadMonth(yearMonth: YearMonth = _currentMonth.value, forceRefresh: Boolean = false) {
        _currentMonth.value = yearMonth

        viewModelScope.launch {
            val school = settingsRepository.getSettings().asrSchool.apiValue
            val cached = monthCache[yearMonth to school]
            if (cached != null && !forceRefresh) {
                _uiState.value = MonthlyCalendarUiState.Success(cached)
                return@launch
            }
            val savedDays = repository.getSavedMonth(yearMonth).filter { it.asrSchool == school }
            val saved = savedDays.takeIf { it.size == yearMonth.lengthOfMonth() }?.map { day ->
                fun time(name: String): String = day.prayers.first { it.name == name }.let {
                    Instant.ofEpochMilli(it.startTimeMillis).atZone(ZoneId.of(day.timezoneId))
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                }
                DayPrayerTimes(
                    day.date.dayOfMonth, day.date.format(DateTimeFormatter.ofPattern("d MMM")),
                    day.hijriDate, time("Fajr"), time("Dhuhr"), time("Asr"), time("Maghrib"), time("Isha"),
                    day.date == LocalDate.now(), day.date
                )
            }
            if (saved != null && !forceRefresh) {
                monthCache[yearMonth to school] = saved
                _uiState.value = MonthlyCalendarUiState.Success(saved)
                return@launch
            }
            _uiState.value = MonthlyCalendarUiState.Loading

            val location = getLocation()
            if (location == null) {
                _uiState.value = MonthlyCalendarUiState.Error("Location not available. Open the app first.")
                return@launch
            }

            try {
                val response = api.getCalendar(
                    year = yearMonth.year,
                    month = yearMonth.monthValue,
                    latitude = location.first,
                    longitude = location.second,
                    school = school
                )

                if (response.code != 200) {
                    _uiState.value = MonthlyCalendarUiState.Error("API error: ${response.status}")
                    return@launch
                }

                val days = response.data.map { dayData ->
                    val date = runCatching {
                        LocalDate.of(dayData.date.gregorian.year.toInt(), dayData.date.gregorian.month.number,
                            dayData.date.gregorian.day.toInt())
                    }.getOrNull()
                    DayPrayerTimes(
                        dayOfMonth = dayData.date.gregorian.day.toIntOrNull() ?: 0,
                        dateLabel = "${dayData.date.gregorian.day} ${dayData.date.gregorian.month.en.take(3)}",
                        hijriLabel = dayData.date.hijri?.formatted(),
                        fajr = cleanTime(dayData.timings.fajr),
                        dhuhr = cleanTime(dayData.timings.dhuhr),
                        asr = cleanTime(dayData.timings.asr),
                        maghrib = cleanTime(dayData.timings.maghrib),
                        isha = cleanTime(dayData.timings.isha),
                        isToday = isToday(dayData),
                        date = date
                    )
                }

                monthCache[yearMonth to school] = days
                _uiState.value = MonthlyCalendarUiState.Success(days)
                Log.d(TAG, "Fetched and cached ${days.size} days for $yearMonth")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch calendar", e)
                _uiState.value = (saved ?: cached)?.let { MonthlyCalendarUiState.Success(it) }
                    ?: MonthlyCalendarUiState.Error("Failed to load: ${e.message}")
            }
        }
    }

    fun goToPreviousMonth() {
        loadMonth(_currentMonth.value.minusMonths(1))
    }

    fun goToNextMonth() {
        loadMonth(_currentMonth.value.plusMonths(1))
    }

    fun goToToday() {
        loadMonth(YearMonth.now())
    }

    private fun cleanTime(raw: String): String = raw.split(" ")[0].trim()

    private fun isToday(data: AladhanData): Boolean {
        return try {
            val greg = data.date.gregorian
            val day = greg.day.toIntOrNull() ?: return false
            val month = greg.month.number
            val year = greg.year.toIntOrNull() ?: return false
            val today = LocalDate.now()
            today.dayOfMonth == day && today.monthValue == month && today.year == year
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getLocation(): Pair<Double, Double>? {
        cachedLocation?.let { return it }
        val cache = repository.getCachedPrayerTimes()
        return cache?.let {
            Pair(it.latitude, it.longitude).also { loc -> cachedLocation = loc }
        }
    }
}

sealed class MonthlyCalendarUiState {
    data object Loading : MonthlyCalendarUiState()
    data class Success(val days: List<DayPrayerTimes>) : MonthlyCalendarUiState()
    data class Error(val message: String) : MonthlyCalendarUiState()
}

data class DayPrayerTimes(
    val dayOfMonth: Int,
    val dateLabel: String,
    val hijriLabel: String?,
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val isToday: Boolean,
    val date: LocalDate? = null
)
