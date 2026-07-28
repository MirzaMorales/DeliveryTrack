package mx.utng.deliverytrack.tv.mqtt

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.utng.deliverytrack.shared.config.ServerConfig
import mx.utng.deliverytrack.tv.domain.model.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TvWebSocketClient(
    private val serverUrl: String = ServerConfig.WS_URL
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var reconnectJob: Job? = null
    private var currentBackoffMs = 1000L

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _kpis = MutableStateFlow<KpisDto?>(null)
    val kpis: StateFlow<KpisDto?> = _kpis.asStateFlow()

    private val _pedidos = MutableStateFlow<List<PedidoDto>>(emptyList())
    val pedidos: StateFlow<List<PedidoDto>> = _pedidos.asStateFlow()

    private val _repartidores = MutableStateFlow<Map<Int, RepartidorUbicacionDto>>(emptyMap())
    val repartidores: StateFlow<Map<Int, RepartidorUbicacionDto>> = _repartidores.asStateFlow()

    fun connect() {
        if (webSocket != null) return
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("TvWebSocketClient", "WebSocket Connected!")
                _connectionState.value = true
                currentBackoffMs = 1000L // Reset backoff on success
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TvWebSocketClient", "WebSocket Closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("TvWebSocketClient", "WebSocket Closed: $reason")
                _connectionState.value = false
                this@TvWebSocketClient.webSocket = null
                triggerReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TvWebSocketClient", "WebSocket Failure: ${t.message}", t)
                _connectionState.value = false
                this@TvWebSocketClient.webSocket = null
                triggerReconnect()
            }
        })
    }

    private fun triggerReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d("TvWebSocketClient", "Reconnecting in ${currentBackoffMs}ms...")
            delay(currentBackoffMs)
            currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(30000L)
            connect()
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            when (type) {
                "snapshot" -> {
                    val data = json.getJSONObject("data")
                    
                    // Parse KPIs
                    val kpisJson = data.getJSONObject("kpis")
                    val kpis = KpisDto(
                        pedidosActivos = kpisJson.getInt("pedidosActivos"),
                        entregadosHoy = kpisJson.getInt("entregadosHoy"),
                        tiempoPromedioMin = kpisJson.getInt("tiempoPromedioMin"),
                        incidencias = kpisJson.getInt("incidencias"),
                        repartidoresEnRuta = kpisJson.getInt("repartidoresEnRuta")
                    )

                    // Parse Pedidos
                    val pedidosJson = data.getJSONArray("pedidos")
                    val pedidosList = mutableListOf<PedidoDto>()
                    for (i in 0 until pedidosJson.length()) {
                        val p = pedidosJson.getJSONObject(i)
                        pedidosList.add(parsePedido(p))
                    }

                    // Parse Repartidores
                    val repartidoresJson = data.getJSONArray("repartidores")
                    val repartidoresMap = mutableMapOf<Int, RepartidorUbicacionDto>()
                    for (i in 0 until repartidoresJson.length()) {
                        val r = repartidoresJson.getJSONObject(i)
                        val repId = r.getInt("repartidorId")
                        repartidoresMap[repId] = parseRepartidor(r)
                    }

                    _kpis.value = kpis
                    _pedidos.value = pedidosList
                    _repartidores.value = repartidoresMap
                }
                "pedido_creado" -> {
                    val p = json.getJSONObject("pedido")
                    val newPedido = parsePedido(p)
                    _pedidos.value = _pedidos.value + newPedido
                    
                    // Recalculate KPI for active orders
                    val currentKpis = _kpis.value
                    if (currentKpis != null) {
                        _kpis.value = currentKpis.copy(
                            pedidosActivos = currentKpis.pedidosActivos + 1
                        )
                    }
                }
                "pedido_actualizado" -> {
                    val pedidoId = json.getInt("pedidoId")
                    val nuevoEstatus = json.getInt("nuevoEstatus")

                    // Update local orders list
                    val updatedList = _pedidos.value.map {
                        if (it.idPedido == pedidoId) {
                            it.copy(estatus = nuevoEstatus)
                        } else {
                            it
                        }
                    }
                    
                    // If order status became delivered (6) or cancelled (4), remove it from active list
                    val finalActiveList = updatedList.filter { it.estatus != 4 && it.estatus != 6 }
                    _pedidos.value = finalActiveList

                    // Recalculate KPIs based on status change
                    val currentKpis = _kpis.value
                    if (currentKpis != null) {
                        val activeCount = finalActiveList.count { it.estatus in listOf(1, 2, 3, 5) }
                        val repartidoresEnRuta = finalActiveList.count { it.estatus == 3 }
                        val incidencias = finalActiveList.count { it.estatus == 5 }
                        var deliveredToday = currentKpis.entregadosHoy
                        if (nuevoEstatus == 6) {
                            deliveredToday += 1
                        }

                        _kpis.value = currentKpis.copy(
                            pedidosActivos = activeCount,
                            entregadosHoy = deliveredToday,
                            incidencias = incidencias,
                            repartidoresEnRuta = repartidoresEnRuta
                        )
                    }
                }
                "ubicacion_actualizada" -> {
                    val repId = json.getInt("repartidorId")
                    val lat = json.getDouble("lat")
                    val lng = json.getDouble("lng")
                    val velocidad = json.getDouble("velocidad")
                    val bateria = json.getInt("bateria")

                    val update = RepartidorUbicacionDto(
                        repartidorId = repId,
                        lat = lat,
                        lng = lng,
                        velocidad = velocidad,
                        bateria = bateria
                    )

                    val newMap = _repartidores.value.toMutableMap()
                    newMap[repId] = update
                    _repartidores.value = newMap
                }
            }
        } catch (e: Exception) {
            Log.e("TvWebSocketClient", "Error parsing incoming JSON message: ${e.message}", e)
        }
    }

    private fun parsePedido(obj: JSONObject): PedidoDto {
        return PedidoDto(
            idPedido = obj.getInt("id_pedido"),
            nombreCliente = obj.getString("nombre_cliente"),
            telefono = obj.optString("telefono", ""),
            direccion = obj.getString("direccion"),
            referenciaLugar = obj.optString("referencia_lugar", ""),
            descripcionPedido = obj.optString("descripcion_pedido", ""),
            estatus = obj.getInt("estatus"),
            idRepartidor = if (obj.isNull("id_repartidor")) null else obj.getInt("id_repartidor")
        )
    }

    private fun parseRepartidor(obj: JSONObject): RepartidorUbicacionDto {
        return RepartidorUbicacionDto(
            repartidorId = obj.getInt("repartidorId"),
            lat = obj.getDouble("lat"),
            lng = obj.getDouble("lng"),
            velocidad = obj.optDouble("velocidad", 0.0),
            bateria = obj.optInt("bateria", 100)
        )
    }

    fun disconnect() {
        webSocket?.close(1000, "Goodbye")
        webSocket = null
        _connectionState.value = false
        reconnectJob?.cancel()
    }
}
