package br.com.listamercado.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.listamercado.geo.GeofenceManager
import br.com.listamercado.geo.GeofenceStatus
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * Mostra se o Android aceitou os mercados para avisar e tem o botão "Testar aviso",
 * que dispara agora a notificação que apareceria no mercado.
 */
@Composable
fun AlertsStatusCard(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status by vm.geofenceStatus.collectAsStateWithLifecycle()

    // Depois que a pessoa responde ao pedido de ligar a localização, tenta de novo
    val settingsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { vm.refreshGeofences() }

    val (icon, text, ok) = describe(status)
    val isLocationOff =
        status is GeofenceStatus.Failed &&
            (status as GeofenceStatus.Failed).code == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (ok) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Avisos de mercado", style = MaterialTheme.typography.titleMedium)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::testAlert) { Text("Testar aviso") }
                when {
                    isLocationOff ->
                        OutlinedButton(onClick = {
                            fixLocationSettings(context, onResolvable = { request ->
                                settingsLauncher.launch(request)
                            }, onAlreadyOk = vm::refreshGeofences)
                        }) { Text("Corrigir") }
                    status is GeofenceStatus.Failed ->
                        OutlinedButton(onClick = vm::refreshGeofences) { Text("Tentar de novo") }
                }
            }
        }
    }
}

private data class StatusView(
    val icon: ImageVector,
    val text: String,
    val ok: Boolean,
)

private fun describe(status: GeofenceStatus): StatusView =
    when (status) {
        GeofenceStatus.Unknown -> StatusView(Icons.Default.Info, "Verificando…", true)
        GeofenceStatus.NoStores ->
            StatusView(
                Icons.Default.Info,
                "Nenhum mercado cadastrado ainda. Importe ou marque um no mapa.",
                true,
            )
        GeofenceStatus.NoPermission ->
            StatusView(
                Icons.Default.Warning,
                "Desligados: falta a localização \"Permitir o tempo todo\". Siga o cartão na aba Lista.",
                false,
            )
        is GeofenceStatus.Active ->
            StatusView(
                Icons.Default.CheckCircle,
                (if (status.count == 1) "Ligados em 1 mercado. " else "Ligados em ${status.count} mercados. ") +
                    "O aviso aparece depois de ${loiteringText()} dentro do mercado " +
                    "(o Android pode demorar alguns minutos a mais para perceber).",
                true,
            )
        is GeofenceStatus.Failed -> StatusView(Icons.Default.Warning, status.message, false)
    }

/** Abre o pedido do Google para ligar a localização (inclui "Precisão da localização do Google"). */
private fun fixLocationSettings(
    context: Context,
    onResolvable: (IntentSenderRequest) -> Unit,
    onAlreadyOk: () -> Unit,
) {
    val request =
        LocationSettingsRequest
            .Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build())
            .build()
    LocationServices
        .getSettingsClient(context)
        .checkLocationSettings(request)
        .addOnSuccessListener { onAlreadyOk() }
        .addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                onResolvable(IntentSenderRequest.Builder(e.resolution).build())
            } else {
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    // Sem tela de localização: nada a fazer
                }
            }
        }
}

private fun loiteringText(): String {
    val seconds = GeofenceManager.LOITERING_MS / 1000
    return when {
        seconds < 60 -> "uns $seconds segundos"
        seconds < 120 -> "cerca de 1 minuto"
        else -> "uns ${seconds / 60} minutos"
    }
}
