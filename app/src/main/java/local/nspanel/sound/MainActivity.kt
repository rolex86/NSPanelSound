package local.nspanel.sound

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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

        val prefs = getSharedPreferences("nspanel_sound_prefs", MODE_PRIVATE)
        val currentMaxSeconds = prefs.getInt("max_countdown_seconds", 60)

        val ipAddress = getLocalIpAddress()
        val baseUrl = if (ipAddress != null) {
            "http://$ipAddress:8765"
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
            hint = "60"
        }

        val saveButton = Button(this).apply {
            text = "Uložit"
            setOnClickListener {
                val enteredValue = maxInput.text.toString().trim().toIntOrNull()

                if (enteredValue == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Zadej číslo.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val safeValue = enteredValue.coerceIn(5, 600)

                prefs.edit()
                    .putInt("max_countdown_seconds", safeValue)
                    .apply()

                maxInput.setText(safeValue.toString())

                Toast.makeText(
                    this@MainActivity,
                    "Uloženo: ${safeValue}s",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val hintText = TextView(this).apply {
            text = "Rozsah: 5 až 600 sekund. Výchozí hodnota je 60 s."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        root.addView(title)
        root.addView(serverInfo)
        root.addView(maxLabel)
        root.addView(maxInput)
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