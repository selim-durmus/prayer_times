package com.tuttoposto.prayertimes.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuttoposto.prayertimes.notifications.FajrAlarmScreenState
import com.tuttoposto.prayertimes.notifications.PrayerAlarmPlaybackService
import com.tuttoposto.prayertimes.ui.theme.PrayerTimesTheme
import kotlinx.coroutines.delay
import java.util.Date

/** Only the ringing alarm is exposed over the keyguard; never dismisses/unlocks the keyguard. */
class FajrAlarmActivity : ComponentActivity() {
    private var alarmToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmToken = intent.getStringExtra(EXTRA_TOKEN)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            val session by FajrAlarmScreenState.session.collectAsState()
            val current = session?.takeIf { it.token == alarmToken }
            LaunchedEffect(current) { if (current == null) finish() }
            PrayerTimesTheme(useAmoled = true) {
                if (current != null) {
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(current.token) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
                    val time = DateFormat.getTimeFormat(this).format(Date(now))
                    val remaining = ((current.sunriseMillis - now + 59_999) / 60_000).coerceAtLeast(0)
                    Column(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (current.preview) "Fajr wake-up · Test" else "Fajr wake-up",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Text(time, color = MaterialTheme.colorScheme.onBackground, fontSize = 64.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Text(if (current.preview) "Test alarm · Stops after 8 seconds"
                            else if (current.sunriseMillis <= now) "Sunrise time reached"
                            else "$remaining minutes until sunrise",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(56.dp))
                        Button(onClick = {
                            PrayerAlarmPlaybackService.stopSession(this@FajrAlarmActivity, current.token)
                            finish()
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                            Text("Stop", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alarmToken = intent.getStringExtra(EXTRA_TOKEN)
    }

    companion object { const val EXTRA_TOKEN = "fajr_alarm_token" }
}
