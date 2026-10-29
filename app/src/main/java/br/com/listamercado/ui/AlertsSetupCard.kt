package br.com.listamercado.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

data class AlertStatus(
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val locationOn: Boolean,
) {
    val allOk: Boolean
        get() = fineLocation && backgroundLocation && notifications && batteryUnrestricted && locationOn
}

fun readAlertStatus(context: Context): AlertStatus {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val power = context.getSystemService(PowerManager::class.java)
    val location = context.getSystemService(LocationManager::class.java)
    return AlertStatus(
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(Manifest.permission.POST_NOTIFICATIONS),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        locationOn = LocationManagerCompat.isLocationEnabled(location),
    )
}

/**
 * Cartão que aparece no topo da lista enquanto falta alguma permissão para
 * o aviso de mercado funcionar com o app fechado. Some quando está tudo certo.
 */
@Composable
fun AlertsSetupCard(onStatusChanged: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    // Relê o estado sempre que a pessoa volta das telas de configuração
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val status = remember(refresh) { readAlertStatus(context) }
    LaunchedEffect(status) { onStatusChanged() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    if (status.allOk) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ative o aviso de mercado", style = MaterialTheme.typography.titleMedium)
            Text(
                "Para avisar quando alguém chegar num mercado, mesmo com o app fechado:",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!status.locationOn) {
                SetupStep("Ligar a localização do celular", "Ligar") {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            if (!status.fineLocation) {
                SetupStep("Permitir acesso à localização", "Permitir") {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            }
            if (!status.backgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SetupStep(
                    text = "Localização o tempo todo: escolha \"Permitir o tempo todo\"",
                    action = "Ajustar",
                    enabled = status.fineLocation,
                ) {
                    permissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            if (!status.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupStep("Permitir notificações", "Permitir") {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (!status.batteryUnrestricted) {
                SetupStep("Liberar da economia de bateria (senão o Android pode cortar o aviso)", "Liberar") {
                    requestIgnoreBatteryOptimizations(context)
                }
            }

            TextButton(onClick = { openAppSettings(context) }) {
                Text("Nada acontece? Abrir configurações do app")
            }
        }
    }
}

@Composable
private fun SetupStep(text: String, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}
