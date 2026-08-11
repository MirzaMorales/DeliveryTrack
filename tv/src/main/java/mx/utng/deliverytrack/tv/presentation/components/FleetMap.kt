package mx.utng.deliverytrack.tv.presentation.components

import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import mx.utng.deliverytrack.tv.domain.model.PedidoDto
import mx.utng.deliverytrack.tv.domain.model.RepartidorUbicacionDto

/**
 * Componente que encapsula la vista de Google Maps para mostrar las ubicaciones en tiempo real de los repartidores.
 * 
 * Gestiona el ciclo de vida del mapa, la actualización dinámica de marcadores
 * basados en eventos de telemetría y el encuadre automático de la cámara para incluir a toda la flota activa.
 * 
 * @param repartidores Mapa de repartidores y sus telemetrías/posiciones GPS actuales.
 * @param pedidos Lista de pedidos activos para realizar validación cruzada y colorear los pines.
 * @param modifier Modificador de Compose para tamaño y posicionamiento del mapa.
 */
@Composable
fun FleetMap(
    repartidores: Map<Int, RepartidorUbicacionDto>,
    pedidos: List<PedidoDto>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    val markersMap = remember { mutableMapOf<Int, Marker>() }

    // Correctly manage MapView lifecycle in Compose
    DisposableEffect(mapView) {
        mapView.onCreate(Bundle())
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                googleMap = map
                map.uiSettings.isZoomControlsEnabled = true
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.isMapToolbarEnabled = false
            }
            mapView
        },
        modifier = modifier,
        update = {
            val map = googleMap ?: return@AndroidView
            val builder = LatLngBounds.Builder()
            var hasLocations = false

            // Update or add markers for active and idle couriers
            repartidores.forEach { (repartidorId, loc) ->
                val position = LatLng(loc.lat, loc.lng)
                builder.include(position)
                hasLocations = true

                // Cross-reference active orders to determine marker color
                val activeOrder = pedidos.find { it.idRepartidor == repartidorId }
                val status = activeOrder?.estatus

                val hue = when (status) {
                    1 -> BitmapDescriptorFactory.HUE_BLUE      // Aceptado (Blue)
                    2 -> BitmapDescriptorFactory.HUE_ORANGE    // Pendiente (Orange)
                    3 -> BitmapDescriptorFactory.HUE_GREEN     // En camino (Green)
                    else -> BitmapDescriptorFactory.HUE_AZURE  // Idle / Sin pedido (Azure/Cyan as neutral)
                }

                val title = "Repartidor #$repartidorId (${if (activeOrder != null) "Pedido #${activeOrder.idPedido}" else "Sin pedido / Libre"})"

                val existingMarker = markersMap[repartidorId]
                if (existingMarker != null) {
                    existingMarker.position = position
                    existingMarker.title = title
                    existingMarker.setIcon(BitmapDescriptorFactory.defaultMarker(hue))
                } else {
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(title)
                            .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    )
                    if (marker != null) {
                        markersMap[repartidorId] = marker
                    }
                }
            }

            // Remove markers for disconnected couriers
            val currentIds = repartidores.keys
            val iterator = markersMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in currentIds) {
                    entry.value.remove()
                    iterator.remove()
                }
            }

            // Fit screen bounds to show all markers in view
            if (hasLocations) {
                try {
                    val bounds = builder.build()
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100)
                    map.animateCamera(cameraUpdate)
                } catch (e: IllegalStateException) {
                    // Map view size might not be fully calculated on first load
                }
            }
        }
    )
}
