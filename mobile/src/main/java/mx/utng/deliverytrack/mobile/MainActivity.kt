package mx.utng.deliverytrack.mobile

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MobileMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "DeliveryTrack Mobile Hub",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Use this console to simulate real-time logistical alerts sent from backend / admin to the Wear OS client.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Button 1: Simulate New Order
                    Button(
                        onClick = { simulateAlert("nuevo") },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF388E3C),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Simulate New Order Assigned")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button 2: Simulate Cancellation
                    Button(
                        onClick = { simulateAlert("cancelado") },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Simulate Active Order Canceled")
                    }
                }
            }
        }
    }

    private fun simulateAlert(type: String) {
        Log.d(TAG, "Simulating alert type: $type")
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected watch nodes found to send alert")
                    Toast.makeText(this, "No connected Wear OS devices found!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/pedido/alerta", type.toByteArray(Charsets.UTF_8))
                        .addOnSuccessListener {
                            Log.d(TAG, "Alert successfully sent to node ${node.displayName}")
                            Toast.makeText(this, "Alert '${type}' sent successfully!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send alert to node ${node.displayName}", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch connected nodes", e)
                Toast.makeText(this, "Failed to find Wear OS devices", Toast.LENGTH_SHORT).show()
            }
    }
}
