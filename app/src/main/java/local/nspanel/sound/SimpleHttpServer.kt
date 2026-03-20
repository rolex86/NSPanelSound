package local.nspanel.sound

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SimpleHttpServer(
    private val port: Int,
    private val onCountdownStartRequested: () -> Unit,
    private val onCountdownStopRequested: () -> Unit,
    private val onDoorbellPlayRequested: () -> Unit,
    private val isCountdownRunning: () -> Boolean
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (running.get()) return

        running.set(true)

        executor.execute {
            try {
                serverSocket = ServerSocket(port)

                while (running.get()) {
                    val client = serverSocket?.accept() ?: break
                    executor.execute {
                        handleClient(client)
                    }
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        running.set(false)

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 3000

            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val output = client.getOutputStream()

            val requestLine = reader.readLine() ?: run {
                client.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(output, 400, "Bad Request")
                client.close()
                return
            }

            val method = parts[0]
            val path = parts[1]

            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
            }

            when {
                method.equals("GET", ignoreCase = true) && path == "/health" -> {
                    writeResponse(output, 200, "OK")
                }

                method.equals("GET", ignoreCase = true) && path == "/status" -> {
                    val body = if (isCountdownRunning()) {
                        """{"countdownRunning":true}"""
                    } else {
                        """{"countdownRunning":false}"""
                    }
                    writeResponse(output, 200, body, "application/json; charset=utf-8")
                }

                method.equals("POST", ignoreCase = true) && path == "/countdown/start" -> {
                    onCountdownStartRequested()
                    writeResponse(output, 200, "COUNTDOWN_STARTED")
                }

                method.equals("POST", ignoreCase = true) && path == "/countdown/stop" -> {
                    onCountdownStopRequested()
                    writeResponse(output, 200, "COUNTDOWN_STOPPED")
                }

                method.equals("POST", ignoreCase = true) && path == "/doorbell/play" -> {
                    onDoorbellPlayRequested()
                    writeResponse(output, 200, "DOORBELL_PLAYED")
                }

                else -> {
                    writeResponse(output, 404, "Not Found")
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeResponse(
        output: OutputStream,
        statusCode: Int,
        body: String,
        contentType: String = "text/plain; charset=utf-8"
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "OK"
        }

        val bodyBytes = body.toByteArray(Charsets.UTF_8)

        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)

        output.write(headers)
        output.write(bodyBytes)
        output.flush()
    }
}