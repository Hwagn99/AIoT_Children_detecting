package com.childguard.app.data.remote

import com.childguard.app.data.model.StatusDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Nhận trạng thái realtime (1 Hz) từ backend qua WebSocket. */
class StatusSocket(private val client: OkHttpClient, private val json: Json) {

    fun connect(baseUrl: String, token: String): Flow<StatusDto> = callbackFlow {
        val wsUrl = baseUrl.trimEnd('/').replaceFirst("http", "ws") + "/ws?token=$token"
        val ws = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString<StatusDto>(text) }
                        .onSuccess { trySend(it) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            },
        )
        awaitClose { ws.cancel() }
    }
}
