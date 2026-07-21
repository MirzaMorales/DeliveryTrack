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
import mx.utng.deliverytrack.tv.domain.model.DashboardMetrics

class TvDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TvDashboardScreen()
            }
        }
    }
}

@Composable
fun TvDashboardScreen() {
    var metrics by remember { mutableStateOf(DashboardMetrics(12, 47, 22, 2, 8)) }

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
                text = "● En vivo",
                color = Color(0xFF22C55E),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCard("Pedidos activos", "${metrics.pedidosActivos}", Color(0xFF3B82F6), Modifier.weight(1f))
            MetricCard("Entregados hoy", "${metrics.entregadosHoy}", Color(0xFF22C55E), Modifier.weight(1f))
            MetricCard("Tiempo prom.", "${metrics.tiempoPromedioMin} m", Color(0xFFF59E0B), Modifier.weight(1f))
            MetricCard("Incidencias", "${metrics.incidencias}", Color(0xFFEF4444), Modifier.weight(1f))
            MetricCard("Repartidores en ruta", "${metrics.repartidoresEnRuta}", Color(0xFF8B5CF6), Modifier.weight(1f))
        }
    }
}

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
