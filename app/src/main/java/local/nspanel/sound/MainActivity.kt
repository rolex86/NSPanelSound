package local.nspanel.sound

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = AppConfig.prefs(this)

        val currentMaxSeconds = prefs.getInt(
            AppConfig.KEY_MAX_COUNTDOWN_SECONDS,
            AppConfig.DEFAULT_MAX_COUNTDOWN_SECONDS
        )
        val currentCountdownVolume = prefs.getInt(
            AppConfig.KEY_COUNTDOWN_VOLUME_PERCENT,
            AppConfig.DEFAULT_COUNTDOWN_VOLUME_PERCENT
        )
        val currentDoorbellVolume = prefs.getInt(
            AppConfig.KEY_DOORBELL_VOLUME_PERCENT,
            AppConfig.DEFAULT_DOORBELL_VOLUME_PERCENT
        )

        val ipAddress = getLocalIpAddress()
        val baseUrl = if (ipAddress != null) {
            "http://$ipAddress:${AppConfig.SERVER_PORT}"
        } else {
            "IP nezjištěna"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val title = TextView(this).apply {
            text = "NSPanel Sound"
            textSize = 24f
        }

        val serverInfo = TextView(this).apply {
            text = """
                Server:
                $baseUrl

                Endpoints:
                POST /countdown/start
                POST /countdown/stop
                POST /doorbell/play
                GET  /health
                GET  /status
            """.trimIndent()
            textSize = 16f
            setPadding(0, 30, 0, 30)
        }

        val maxLabel = TextView(this).apply {
            text = "Max délka pípání (sekundy)"
            textSize = 16f
        }

        val maxInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentMaxSeconds.toString())
            hint = AppConfig.DEFAULT_MAX_COUNTDOWN_SECONDS.toString()
        }

        val countdownVolumeLabel = TextView(this).apply {
            text = "Countdown hlasitost (%)"
            textSize = 16f
            setPadding(0, 24, 0, 0)
        }

        val countdownVolumeInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentCountdownVolume.toString())
            hint = AppConfig.DEFAULT_COUNTDOWN_VOLUME_PERCENT.toString()
        }

        val doorbellVolumeLabel = TextView(this).apply {
            text = "Doorbell hlasitost (%)"
            textSize = 16f
            setPadding(0, 24, 0, 0)
        }

        val doorbellVolumeInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentDoorbellVolume.toString())
            hint = AppConfig.DEFAULT_DOORBELL_VOLUME_PERCENT.toString()
        }

        val saveButton = Button(this).apply {
            text = "Uložit"
            setOnClickListener {
                val enteredMaxSeconds = maxInput.text.toString().trim().toIntOrNull()
                val enteredCountdownVolume = countdownVolumeInput.text.toString().trim().toIntOrNull()
                val enteredDoorbellVolume = doorbellVolumeInput.text.toString().trim().toIntOrNull()

                if (enteredMaxSeconds == null || enteredCountdownVolume == null || enteredDoorbellVolume == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Vyplň všechna pole číslem.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val safeMaxSeconds = enteredMaxSeconds.coerceIn(5, 600)
                val safeCountdownVolume = enteredCountdownVolume.coerceIn(0, 100)
                val safeDoorbellVolume = enteredDoorbellVolume.coerceIn(0, 100)

                prefs.edit()
                    .putInt(AppConfig.KEY_MAX_COUNTDOWN_SECONDS, safeMaxSeconds)
                    .putInt(AppConfig.KEY_COUNTDOWN_VOLUME_PERCENT, safeCountdownVolume)
                    .putInt(AppConfig.KEY_DOORBELL_VOLUME_PERCENT, safeDoorbellVolume)
                    .apply()

                maxInput.setText(safeMaxSeconds.toString())
                countdownVolumeInput.setText(safeCountdownVolume.toString())
                doorbellVolumeInput.setText(safeDoorbellVolume.toString())

                Toast.makeText(
                    this@MainActivity,
                    "Uloženo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val hintText = TextView(this).apply {
            text = """
                Max délka: 5 až 600 s
                Countdown hlasitost: 0 až 100 %
                Doorbell hlasitost: 0 až 100 %
            """.trimIndent()
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        root.addView(title)
        root.addView(serverInfo)
        root.addView(maxLabel)
        root.addView(maxInput)
        root.addView(countdownVolumeLabel)
        root.addView(countdownVolumeInput)
        root.addView(doorbellVolumeLabel)
        root.addView(doorbellVolumeInput)
        root.addView(saveButton)
        root.addView(hintText)

        setContentView(root)

        val intent = Intent(this, WebhookService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()

            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses?.toList().orEmpty()

                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress
                        if (!host.isNullOrBlank() && host != "127.0.0.1") {
                            return host
                        }
                    }
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

}
