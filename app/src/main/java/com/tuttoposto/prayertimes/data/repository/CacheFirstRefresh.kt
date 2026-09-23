package com.tuttoposto.prayertimes.data.repository

import kotlinx.coroutines.CancellationException

/** Scheduling finishes before refresh can block on GPS or HTTP; partial success is scheduled too. */
object CacheFirstRefresh {
    suspend fun run(
        schedule: suspend () -> Unit,
        needsRefresh: suspend () -> Boolean,
        refresh: suspend () -> Result<Unit>
    ): Result<Unit> {
        schedule()
        if (!needsRefresh()) return Result.success(Unit)
        val result = try {
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        schedule()
        return result
    }
}
