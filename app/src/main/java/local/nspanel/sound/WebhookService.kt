package local.nspanel.sound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
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

    @Volatile
    private var serverStatusText: String = "Starting server..."

    private val handler = Handler(Looper.getMainLooper())
    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in AppConfig.NOTIFICATION_RELEVANT_KEYS) {
            refreshNotification()
        }
    }

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
        getPrefs().registerOnSharedPreferenceChangeListener(prefsChangeListener)

        server = SimpleHttpServer(
            port = AppConfig.SERVER_PORT,
            onServerStarted = {
                serverStatusText = "Server running"
                refreshNotification()
            },
            onServerError = { details ->
                serverStatusText = "Server error: $details"
                refreshNotification()
            },
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

        val volume = getCountdownVolume()

        soundPool?.play(
            beepSoundId,
            volume,
            volume,
            1,
            0,
            1.0f
        )
    }

    private fun playDoorbellOnce(): Boolean {
        if (!doorbellLoaded) return false

        val volume = getDoorbellVolume()

        val streamId = soundPool?.play(
            doorbellSoundId,
            volume,
            volume,
            2,
            0,
            1.0f
        ) ?: 0

        return streamId != 0
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
        val countdownVolume = getCountdownVolumePercent()
        val doorbellVolume = getDoorbellVolumePercent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NSPanel Sound")
            .setContentText(
                "$serverStatusText | port ${AppConfig.SERVER_PORT} | max: ${maxDuration}s | c: ${countdownVolume}% | d: ${doorbellVolume}%"
            )
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        handler.post {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification())
        }
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

    private fun getPrefs() = AppConfig.prefs(this)

    private fun getMaxCountdownDurationSeconds(): Int {
        return getPrefs().getInt(
            AppConfig.KEY_MAX_COUNTDOWN_SECONDS,
            AppConfig.DEFAULT_MAX_COUNTDOWN_SECONDS
        )
    }

    private fun getCountdownVolumePercent(): Int {
        return getPrefs().getInt(
            AppConfig.KEY_COUNTDOWN_VOLUME_PERCENT,
            AppConfig.DEFAULT_COUNTDOWN_VOLUME_PERCENT
        )
            .coerceIn(0, 100)
    }

    private fun getDoorbellVolumePercent(): Int {
        return getPrefs().getInt(
            AppConfig.KEY_DOORBELL_VOLUME_PERCENT,
            AppConfig.DEFAULT_DOORBELL_VOLUME_PERCENT
        )
            .coerceIn(0, 100)
    }

    private fun getCountdownVolume(): Float {
        return getCountdownVolumePercent() / 100f
    }

    private fun getDoorbellVolume(): Float {
        return getDoorbellVolumePercent() / 100f
    }

    override fun onDestroy() {
        stopCountdown()
        getPrefs().unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
        server?.stop()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "webhook_service"
        private const val NOTIFICATION_ID = 1001
        private const val BEEP_INTERVAL_MS = 1000L
    }
}
