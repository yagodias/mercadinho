package br.com.listamercado.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

private val RADIUS_OPTIONS = listOf(100, 150, 250, 400)

/**
 * Tela para marcar um mercado: arraste o mapa até o pino ficar sobre o mercado,
 * dê um nome e escolha o raio do aviso. Usa OpenStreetMap (não precisa de chave de API).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    onCancel: () -> Unit,
    onSave: (name: String, lat: Double, lng: Double, radius: Int) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableIntStateOf(150) }
    var radiusPx by remember { mutableFloatStateOf(0f) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(-14.2, -51.9)) // Brasil, até achar sua localização
        }
    }

    fun updateRadius() {
        radiusPx = mapView.projection.metersToPixels(radius.toFloat())
    }

    // Ciclo de vida do mapa + recalcula o círculo ao mover/zoom
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                updateRadius()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                updateRadius()
                return false
            }
        }
        mapView.addMapListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(listener)
            mapView.onDetach()
        }
    }

    LaunchedEffect(radius) { updateRadius() }
    LaunchedEffect(Unit) { centerOnUser(context, mapView, silent = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo mercado") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                val primary = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    if (radiusPx > 0f) {
                        drawCircle(color = primary.copy(alpha = 0.15f), radius = radiusPx, center = center)
                        drawCircle(
                            color = primary,
                            radius = radiusPx,
                            center = center,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                // A ponta do pino fica exatamente no centro do mapa
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-18).dp)
                        .size(40.dp),
                )
                SmallFloatingActionButton(
                    onClick = { centerOnUser(context, mapView, silent = false) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Minha localização")
                }
            }

            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Arraste o mapa até o pino ficar sobre o mercado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nome do mercado") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Avisar num raio de", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RADIUS_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = radius == option,
                            onClick = { radius = option },
                            label = { Text("$option m") },
                        )
                    }
                }
                Button(
                    onClick = {
                        val center = mapView.mapCenter
                        onSave(name, center.latitude, center.longitude, radius)
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar mercado") }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun centerOnUser(context: Context, mapView: MapView, silent: Boolean) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        if (!silent) {
            Toast.makeText(context, "Permita a localização na aba Lista para usar isso.", Toast.LENGTH_LONG).show()
        }
        return
    }
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location ->
            if (location != null) {
                mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude), 17.5, 800L)
            } else if (!silent) {
                Toast.makeText(context, "Não consegui sua localização agora.", Toast.LENGTH_SHORT).show()
            }
        }
}
