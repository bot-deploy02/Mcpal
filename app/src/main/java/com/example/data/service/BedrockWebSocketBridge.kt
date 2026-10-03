package com.example.data.service

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID

class BedrockWebSocketBridge {

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _activeClientAddress = MutableStateFlow<String?>(null)
    val activeClientAddress: StateFlow<String?> = _activeClientAddress.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeOutputStream: OutputStream? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(port: Int = 19132) {
        if (_isServerRunning.value) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _isServerRunning.value = true
                logEvent("Bridge server listening on port $port. In Minecraft chat, run: /connect 127.0.0.1:$port")

                while (isActive && serverSocket?.isClosed == false) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                logEvent("Server error: ${e.message}")
            } finally {
                _isServerRunning.value = false
                _isConnected.value = false
                _activeClientAddress.value = null
            }
        }
    }

    fun stop() {
        try {
            activeSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("BedrockBridge", "Error closing sockets", e)
        }
        serverJob?.cancel()
        _isServerRunning.value = false
        _isConnected.value = false
        _activeClientAddress.value = null
        logEvent("Bridge server stopped.")
    }

    fun sendMinecraftCommand(command: String) {
        val trimmed = if (command.startsWith("/")) command.substring(1) else command
        val reqId = UUID.randomUUID().toString()
        val json = JSONObject().apply {
            put("header", JSONObject().apply {
                put("version", 1)
                put("requestId", reqId)
                put("messagePurpose", "commandRequest")
                put("messageType", "commandRequest")
            })
            put("body", JSONObject().apply {
                put("version", 1)
                put("commandLine", trimmed)
                put("origin", JSONObject().apply {
                    put("type", "player")
                })
            })
        }
        sendWebSocketText(json.toString())
        logEvent("Sent command to Minecraft: /$trimmed")
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                activeSocket = socket
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                activeOutputStream = output

                if (performHandshake(input, output)) {
                    _isConnected.value = true
                    _activeClientAddress.value = socket.inetAddress.hostAddress
                    logEvent("Minecraft Bedrock connected from ${socket.inetAddress.hostAddress}")

                    // Subscribe to core events
                    subscribeToEvent("PlayerTravelled")
                    subscribeToEvent("BlockBroken")
                    subscribeToEvent("ItemCrafted")
                    subscribeToEvent("MobKilled")
                    subscribeToEvent("PlayerMessage")

                    // Announce connection in Minecraft
                    sendMinecraftCommand("title @a title §a[MineMaster AI]")
                    sendMinecraftCommand("title @a subtitle §eTactical Copilot Active")

                    // Read frames
                    readFrames(input)
                }
            } catch (e: Exception) {
                logEvent("Client disconnected: ${e.message}")
            } finally {
                _isConnected.value = false
                _activeClientAddress.value = null
                activeOutputStream = null
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun subscribeToEvent(eventName: String) {
        val json = JSONObject().apply {
            put("header", JSONObject().apply {
                put("version", 1)
                put("requestId", UUID.randomUUID().toString())
                put("messagePurpose", "subscribe")
                put("messageType", "commandRequest")
            })
            put("body", JSONObject().apply {
                put("eventName", eventName)
            })
        }
        sendWebSocketText(json.toString())
    }

    private fun performHandshake(input: InputStream, output: OutputStream): Boolean {
        val buffer = ByteArray(4096)
        val read = input.read(buffer)
        if (read <= 0) return false
        val request = String(buffer, 0, read)

        val keyPrefix = "Sec-WebSocket-Key: "
        val keyIndex = request.indexOf(keyPrefix)
        if (keyIndex == -1) return false

        val keyEnd = request.indexOf("\r\n", keyIndex)
        val clientKey = request.substring(keyIndex + keyPrefix.length, keyEnd).trim()

        val acceptKey = generateAcceptKey(clientKey)
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
        return true
    }

    private fun generateAcceptKey(key: String): String {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest((key + magic).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun readFrames(input: InputStream) {
        val header = ByteArray(2)
        while (input.read(header) == 2) {
            val opcode = header[0].toInt() and 0x0F
            val isMasked = (header[1].toInt() and 0x80) != 0
            var payloadLen = (header[1].toInt() and 0x7F).toLong()

            if (opcode == 8) { // Close frame
                break
            }

            if (payloadLen == 126L) {
                val ext = ByteArray(2)
                input.read(ext)
                payloadLen = ((ext[0].toInt() and 0xFF) shl 8 or (ext[1].toInt() and 0xFF)).toLong()
            } else if (payloadLen == 127L) {
                val ext = ByteArray(8)
                input.read(ext)
                payloadLen = 0L
                for (b in ext) {
                    payloadLen = (payloadLen shl 8) or (b.toLong() and 0xFF)
                }
            }

            val mask = ByteArray(4)
            if (isMasked) {
                input.read(mask)
            }

            val payload = ByteArray(payloadLen.toInt())
            var bytesRead = 0
            while (bytesRead < payload.size) {
                val r = input.read(payload, bytesRead, payload.size - bytesRead)
                if (r == -1) break
                bytesRead += r
            }

            if (isMasked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }

            val text = String(payload, Charsets.UTF_8)
            parseMinecraftEvent(text)
        }
    }

    private fun parseMinecraftEvent(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val header = root.optJSONObject("header")
            val purpose = header?.optString("messagePurpose")
            val body = root.optJSONObject("body")

            if (purpose == "event") {
                val eventName = header.optString("eventName")
                logEvent("MC Event: $eventName")
            } else if (purpose == "commandResponse") {
                val statusMsg = body?.optString("statusMessage")
                if (!statusMsg.isNullOrBlank()) {
                    logEvent("MC Response: $statusMsg")
                }
            }
        } catch (e: Exception) {
            // Non-json or debug ping
        }
    }

    @Synchronized
    private fun sendWebSocketText(text: String) {
        try {
            val output = activeOutputStream ?: return
            val bytes = text.toByteArray(Charsets.UTF_8)
            val length = bytes.size

            output.write(0x81) // FIN + text opcode
            if (length <= 125) {
                output.write(length)
            } else if (length <= 65535) {
                output.write(126)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            } else {
                output.write(127)
                for (i in 7 downTo 0) {
                    output.write(((length.toLong() shr (i * 8)) and 0xFF).toInt())
                }
            }
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            Log.e("BedrockBridge", "Failed to write WebSocket frame", e)
        }
    }

    private fun logEvent(msg: String) {
        val current = _eventLogs.value.toMutableList()
        current.add(0, msg)
        if (current.size > 50) current.removeAt(current.size - 1)
        _eventLogs.value = current
    }
}
