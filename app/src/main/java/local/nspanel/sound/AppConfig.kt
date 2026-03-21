package local.nspanel.sound

import android.content.Context
import android.content.SharedPreferences

object AppConfig {
    const val SERVER_PORT = 8765

    const val PREFS_NAME = "nspanel_sound_prefs"
    const val KEY_MAX_COUNTDOWN_SECONDS = "max_countdown_seconds"
    const val KEY_COUNTDOWN_VOLUME_PERCENT = "countdown_volume_percent"
    const val KEY_DOORBELL_VOLUME_PERCENT = "doorbell_volume_percent"

    const val DEFAULT_MAX_COUNTDOWN_SECONDS = 70
    const val DEFAULT_COUNTDOWN_VOLUME_PERCENT = 90
    const val DEFAULT_DOORBELL_VOLUME_PERCENT = 60

    val NOTIFICATION_RELEVANT_KEYS = setOf(
        KEY_MAX_COUNTDOWN_SECONDS,
        KEY_COUNTDOWN_VOLUME_PERCENT,
        KEY_DOORBELL_VOLUME_PERCENT
    )

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
