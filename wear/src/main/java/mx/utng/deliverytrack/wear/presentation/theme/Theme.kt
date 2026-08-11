package mx.utng.deliverytrack.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Tema principal de diseño para la aplicación DeliveryTrack WearOS.
 *
 * Configura la paleta de colores, tipografías y formas de Compose Material 3
 * para adaptarlos al hardware de relojes inteligentes (WearOS).
 *
 * @param content El árbol de componentes Composable que se renderizará bajo esta temática.
 */
@Composable
fun DeliveryTrackTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}
