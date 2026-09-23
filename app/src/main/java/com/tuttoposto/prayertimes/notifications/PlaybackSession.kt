package com.tuttoposto.prayertimes.notifications

/** Real alarms always take priority over previews; preview stop must never silence a real alarm. */
internal class PlaybackSession {
    var token: String? = null
        private set
    var active: Boolean = false
        private set
    var preview: Boolean = false
        private set

    fun start(isPreview: Boolean, newToken: String = java.util.UUID.randomUUID().toString()): Boolean {
        if (active && !preview && isPreview) return false
        active = true
        preview = isPreview
        token = newToken
        return true
    }

    fun stopPreview(): Boolean = active && preview
    fun canStop(expectedToken: String?): Boolean = active && expectedToken != null && token == expectedToken

    fun clear() { active = false; preview = false; token = null }
}
