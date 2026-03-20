package local.nspanel.sound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class WebhookService : Service() {

    private var soundPool: SoundPool? = null

    private var beepSoundId: Int = 0
    private var doorbellSoundId: Int = 0

    private var beepLoaded: Boolean = false
    private var doorbellLoaded: Boolean = false

    private var server: SimpleHttpServer? = null

    @Volatile
    private var countdownRunning: Boolean = false

    private val handler = Handler(Looper.getMainLooper())

    private val beepLoopRunnable = object : Runnable {
        override fun run() {
            if (!countdownRunning) return

            playBeepOnce()
            handler.postDelayed(this, BEEP_INTERVAL_MS)
        }
    }

    private val autoStopRunnable = Runnable {
        stopCountdown()
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        initSoundPool()

        server = SimpleHttpServer(
            port = SERVER_PORT,
            onCountdownStartRequested = { startCountdown() },
            onCountdownStopRequested = { stopCountdown() },
            onDoorbellPlayRequested = { playDoorbellOnce() },
            isCountdownRunning = { countdownRunning }
        )
        server?.start()
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                if (sampleId == beepSoundId) {
                    beepLoaded = true
                }
                if (sampleId == doorbellSoundId) {
                    doorbellLoaded = true
                }
            }
        }

        beepSoundId = soundPool?.load(this, R.raw.beep, 1) ?: 0
        doorbellSoundId = soundPool?.load(this, R.raw.doorbell, 1) ?: 0
    }

    private fun playBeepOnce() {
        if (!beepLoaded) return

        soundPool?.play(
            beepSoundId,
            1.0f,
            1.0f,
            1,
            0,
            1.0f
        )
    }

    private fun playDoorbellOnce() {
        if (!doorbellLoaded) return

        soundPool?.play(
            doorbellSoundId,
            1.0f,
            1.0f,
            2,
            0,
            1.0f
        )
    }

    private fun startCountdown() {
        val maxDurationMs = getMaxCountdownDurationSeconds() * 1000L

        countdownRunning = true

        handler.removeCallbacks(beepLoopRunnable)
        handler.removeCallbacks(autoStopRunnable)

        handler.post(beepLoopRunnable)
        handler.postDelayed(autoStopRunnable, maxDurationMs)
    }

    private fun stopCountdown() {
        countdownRunning = false
        handler.removeCallbacks(beepLoopRunnable)
        handler.removeCallbacks(autoStopRunnable)
    }

    private fun buildNotification(): Notification {
        val maxDuration = getMaxCountdownDurationSeconds()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NSPanel Sound")
            .setContentText("Webhook server běží na portu $SERVER_PORT | max: ${maxDuration}s")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Webhook Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getPrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getMaxCountdownDurationSeconds(): Int {
        return getPrefs().getInt(KEY_MAX_COUNTDOWN_SECONDS, DEFAULT_MAX_COUNTDOWN_SECONDS)
    }

    override fun onDestroy() {
        stopCountdown()
        server?.stop()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "webhook_service"
        private const val NOTIFICATION_ID = 1001
        private const val SERVER_PORT = 8765
        private const val BEEP_INTERVAL_MS = 1000L

        private const val PREFS_NAME = "nspanel_sound_prefs"
        private const val KEY_MAX_COUNTDOWN_SECONDS = "max_countdown_seconds"
        private const val DEFAULT_MAX_COUNTDOWN_SECONDS = 60
    }
}