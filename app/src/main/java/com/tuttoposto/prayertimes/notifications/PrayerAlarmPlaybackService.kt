package com.tuttoposto.prayertimes.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tuttoposto.prayertimes.R
import com.tuttoposto.prayertimes.ui.MainActivity
import com.tuttoposto.prayertimes.ui.FajrAlarmActivity

/**
 * Foreground service for [NotificationStyle.ALARMY]: plays alarm-stream audio until the user
 * swipes the notification away, [PrayerAlarmDismissReceiver], timeout, or [requestStop].
 */
class PrayerAlarmPlaybackService : Service() {

    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var isForeground = false
    private val session = PlaybackSession()
    private var showFajrScreen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SESSION) {
            if (session.canStop(intent.getStringExtra(FajrAlarmActivity.EXTRA_TOKEN))) shutdown()
            else if (!session.active) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP_PREVIEW) {
            if (session.stopPreview()) shutdown()
            else if (!session.active) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        val uriString = intent?.getStringExtra(EXTRA_SOUND_URI) ?: run {
            Log.w(TAG, "Missing sound URI, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        if (!session.start(preview)) return START_NOT_STICKY
        showFajrScreen = intent.getBooleanExtra(EXTRA_FAJR_SCREEN, false)
        FajrAlarmScreenState.publish(if (showFajrScreen) FajrAlarmScreenSession(
            requireNotNull(session.token), intent.getLongExtra(EXTRA_SUNRISE, 0), preview
        ) else null)

        cancelTimeout()
        releaseWakeLock()

        val fgNotification = buildForegroundNotification(title, text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    PLAYBACK_NOTIFICATION_ID,
                    fgNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(PLAYBACK_NOTIFICATION_ID, fgNotification)
            }
            isForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            FajrAlarmScreenState.publish(null)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        val uri = Uri.parse(uriString)
        run {
            try {
                ringtone?.stop()
            } catch (_: Exception) {
            }
            ringtone = null
            try {
                ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
                if (ringtone == null) {
                    Log.e(TAG, "Ringtone is null for $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play ringtone", e)
            }
        }

        timeoutRunnable = Runnable { shutdown() }
        mainHandler.postDelayed(timeoutRunnable!!, if (preview) NotificationHelper.PREVIEW_DURATION_MS else MAX_PLAYBACK_MS)
        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val token = requireNotNull(session.token)
        val openApp = if (showFajrScreen) PendingIntent.getActivity(
            this, 5002,
            Intent(this, FajrAlarmActivity::class.java).apply {
                data = Uri.parse("prayertimes://fajr-alarm/$token")
                putExtra(FajrAlarmActivity.EXTRA_TOKEN, token)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            // Scoped to the private alarm activity and handed only to Android's notification UI.
            ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            ).toBundle()
        ) else PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopFajr = if (showFajrScreen) PendingIntent.getBroadcast(this, 5003,
            Intent(this, PrayerAlarmDismissReceiver::class.java).apply {
                data = Uri.parse("prayertimes://stop-fajr/$token")
                putExtra(FajrAlarmActivity.EXTRA_TOKEN, token)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else null

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALARM_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp)
            .apply {
                if (showFajrScreen) {
                    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    addAction(0, "Stop", stopFajr)
                    if (getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
                        setFullScreenIntent(openApp, true)
                    }
                }
            }
            .setDeleteIntent(
                stopFajr ?: PendingIntent.getBroadcast(
                    this,
                    if (session.preview) 4 else 3,
                    Intent(this, PrayerAlarmDismissReceiver::class.java).apply {
                        putExtra(PrayerAlarmDismissReceiver.EXTRA_PREVIEW, session.preview)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrayerTimes:AlarmPlayback").apply {
            setReferenceCounted(false)
            acquire(MAX_PLAYBACK_MS)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun shutdown() {
        FajrAlarmScreenState.publish(null)
        session.clear()
        cancelTimeout()
        releaseWakeLock()
        run {
            try {
                ringtone?.stop()
            } catch (_: Exception) {
            }
            ringtone = null
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        FajrAlarmScreenState.publish(null)
        session.clear()
        cancelTimeout()
        releaseWakeLock()
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PrayerAlarmPlayback"
        const val ACTION_STOP = "com.tuttoposto.prayertimes.alarm_playback.STOP"
        private const val ACTION_STOP_SESSION = "com.tuttoposto.prayertimes.alarm_playback.STOP_SESSION"
        private const val EXTRA_FAJR_SCREEN = "fajr_screen"
        private const val EXTRA_SUNRISE = "sunrise_millis"
        private const val ACTION_STOP_PREVIEW = "com.tuttoposto.prayertimes.alarm_playback.STOP_PREVIEW"
        private const val EXTRA_PREVIEW = "preview"
        private const val EXTRA_SOUND_URI = "sound_uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        const val PLAYBACK_NOTIFICATION_ID = 5001
        private const val MAX_PLAYBACK_MS = 20 * 60 * 1000L

        fun startPlayback(context: Context, soundUri: Uri, title: String, text: String, isPreview: Boolean = false,
                          showFajrScreen: Boolean = false, sunriseMillis: Long = 0) {
            val app = context.applicationContext
            if (!NotificationHelper.hasNotificationPermission(app)) {
                Log.w(TAG, "No notification permission, skip alarm playback")
                return
            }
            val intent = Intent(app, PrayerAlarmPlaybackService::class.java).apply {
                putExtra(EXTRA_SOUND_URI, soundUri.toString())
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_PREVIEW, isPreview)
                putExtra(EXTRA_FAJR_SCREEN, showFajrScreen)
                putExtra(EXTRA_SUNRISE, sunriseMillis)
            }
            ContextCompat.startForegroundService(app, intent)
        }

        fun requestStop(context: Context) {
            val app = context.applicationContext
            val i = Intent(app, PrayerAlarmPlaybackService::class.java).apply { action = ACTION_STOP }
            app.startService(i)
        }

        fun stopPreview(context: Context) {
            context.startService(Intent(context, PrayerAlarmPlaybackService::class.java).apply { action = ACTION_STOP_PREVIEW })
        }

        fun stopSession(context: Context, token: String) {
            context.startService(Intent(context, PrayerAlarmPlaybackService::class.java).apply {
                action = ACTION_STOP_SESSION
                putExtra(FajrAlarmActivity.EXTRA_TOKEN, token)
            })
        }
    }
}
