package mx.utng.deliverytrack.tv.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.utng.deliverytrack.tv.domain.model.KpisDto
import mx.utng.deliverytrack.tv.mqtt.TvWebSocketClient
import mx.utng.deliverytrack.tv.presentation.components.FleetMap

/**
 * Actividad principal del módulo TV que inicializa y muestra el panel logístico de la Smart TV.
 * 
 * Abre la conexión de WebSocket al crearse y la libera al destruirse la actividad.
 */
class TvDashboardActivity : ComponentActivity() {
    private lateinit var webSocketClient: TvWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webSocketClient = TvWebSocketClient()
        webSocketClient.connect()

        setContent {
            MaterialTheme {
                TvDashboardScreen(webSocketClient)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
    }
}

/**
 * Pantalla principal en Compose para el Dashboard de la TV.
 * 
 * Reúne y observa los flujos del WebSocket y pinta la cabecera, indicadores de métricas,
 * listado de pedidos activos y el mapa logístico de Google Maps.
 * 
 * @param client Instancia del cliente de WebSocket del que se consumen los datos en tiempo real.
 */
@Composable
fun TvDashboardScreen(client: TvWebSocketClient) {
    val kpis by client.kpis.collectAsState()
    val isConnected by client.connectionState.collectAsState()
    val pedidos by client.pedidos.collectAsState()
    val repartidores by client.repartidores.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DeliveryTrack — Panel Logístico (Smart TV)",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isConnected) "● En vivo" else "○ Desconectado",
                color = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val activeKpis = kpis ?: KpisDto(0, 0, 0, 0, 0)
            
            MetricCard("Pedidos activos", "${activeKpis.pedidosActivos}", Color(0xFF3B82F6), Modifier.weight(1f))
            MetricCard("Entregados hoy", "${activeKpis.entregadosHoy}", Color(0xFF22C55E), Modifier.weight(1f))
            MetricCard("Tiempo prom.", "${activeKpis.tiempoPromedioMin} m", Color(0xFFF59E0B), Modifier.weight(1f))
            MetricCard("Incidencias", "${activeKpis.incidencias}", Color(0xFFEF4444), Modifier.weight(1f))
            MetricCard("Repartidores en ruta", "${activeKpis.repartidoresEnRuta}", Color(0xFF8B5CF6), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Pedidos Activos y Telemetría",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (pedidos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin entregas activas en este momento",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pedidos.size) { index ->
                                val pedido = pedidos[index]
                                val telemetry = pedido.idRepartidor?.let { repartidores[it] }

                                val statusText = when (pedido.estatus) {
                                    1 -> "Aceptado"
                                    2 -> "Pendiente"
                                    3 -> "En camino"
                                    5 -> "Retrasado"
                                    else -> "Estatus ${pedido.estatus}"
                                }

                                val statusColor = when (pedido.estatus) {
                                    1 -> Color(0xFF3B82F6)
                                    2 -> Color(0xFF94A3B8)
                                    3 -> Color(0xFF22C55E)
                                    5 -> Color(0xFFEF4444)
                                    else -> Color.LightGray
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Pedido #${pedido.idPedido} - ${pedido.nombreCliente}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = pedido.direccion,
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        
                                        if (telemetry != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Repartidor #${pedido.idRepartidor}",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Vel: ${telemetry.velocidad} km/h • Bat: ${telemetry.bateria}%",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FleetMap(
                        repartidores = repartidores,
                        pedidos = pedidos,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Componente de tarjeta reutilizable para mostrar indicadores métricos individuales.
 * 
 * @param title Título descriptivo de la métrica.
 * @param value Valor numérico o texto a destacar.
 * @param accentColor Color característico asignado al valor numérico para contraste visual.
 * @param modifier Modificador para personalizar tamaño, márgenes o peso.
 */
@Composable
fun MetricCard(title: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.LightGray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = accentColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}
