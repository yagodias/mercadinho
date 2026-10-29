package br.com.listamercado.geo

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.listamercado.data.Prefs
import br.com.listamercado.data.Store
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface GeofenceStatus {
    data object Unknown : GeofenceStatus

    data object NoStores : GeofenceStatus

    data object NoPermission : GeofenceStatus

    data class Active(
        val count: Int,
    ) : GeofenceStatus

    data class Failed(
        val code: Int?,
        val message: String,
    ) : GeofenceStatus
}

/**
 * Registra os mercados do grupo como "cercas" no Android. Quem vigia a
 * localização é o próprio sistema (Google Play Services), mesmo com o app fechado.
 */
object GeofenceManager {
    private const val TAG = "GeofenceManager"

    /** Limite do Android por app */
    private const val MAX_GEOFENCES = 100

    /** Tempo dentro do raio antes de avisar. Evita disparar ao passar de carro na frente. */
    const val LOITERING_MS = 60 * 1000

    private val _status = MutableStateFlow<GeofenceStatus>(GeofenceStatus.Unknown)

    /** Se o Android aceitou os mercados; mostrado na aba Mercados. */
    val status: StateFlow<GeofenceStatus> = _status.asStateFlow()

    fun hasPermissions(context: Context): Boolean {
        val fine =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val background =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        // Geofencing exige PendingIntent mutável no Android 12+
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or mutable
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** Substitui todas as cercas pelas dos mercados informados e guarda a lista localmente. */
    @SuppressLint("MissingPermission")
    fun sync(
        context: Context,
        stores: List<Store>,
    ) {
        val appContext = context.applicationContext
        Prefs(appContext).cacheStores(stores)

        val client = LocationServices.getGeofencingClient(appContext)
        val pi = pendingIntent(appContext)

        client.removeGeofences(pi).addOnCompleteListener {
            if (stores.isEmpty()) {
                _status.value = GeofenceStatus.NoStores
                return@addOnCompleteListener
            }
            if (!hasPermissions(appContext)) {
                Log.i(TAG, "Sem permissão de localização em segundo plano; avisos desativados")
                _status.value = GeofenceStatus.NoPermission
                return@addOnCompleteListener
            }

            val fences =
                stores.take(MAX_GEOFENCES).map { s ->
                    Geofence
                        .Builder()
                        .setRequestId(s.id)
                        .setCircularRegion(s.lat, s.lng, s.radius.toFloat())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                        .setLoiteringDelay(LOITERING_MS)
                        .build()
                }
            val request =
                GeofencingRequest
                    .Builder()
                    // Se a pessoa já estiver dentro de um mercado, também avisa
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    .addGeofences(fences)
                    .build()

            try {
                client
                    .addGeofences(request, pi)
                    .addOnSuccessListener {
                        Log.i(TAG, "${fences.size} mercado(s) monitorado(s)")
                        _status.value = GeofenceStatus.Active(fences.size)
                    }.addOnFailureListener { e ->
                        val code = (e as? ApiException)?.statusCode
                        Log.w(TAG, "Falha ao registrar mercados: ${code?.let { GeofenceStatusCodes.getStatusCodeString(it) } ?: e.message}")
                        _status.value = GeofenceStatus.Failed(code, describe(code))
                    }
            } catch (e: SecurityException) {
                _status.value = GeofenceStatus.NoPermission
            }
        }
    }

    /** Chamado quando o próprio Android avisa um erro (ex.: desligaram a localização). */
    fun reportError(code: Int) {
        _status.value = GeofenceStatus.Failed(code, describe(code))
    }

    fun describe(code: Int?): String =
        when (code) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                "O Android desligou os avisos: a localização do celular ou a \"Precisão da localização do Google\" está desativada."
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                "Mercados demais: o Android aceita no máximo $MAX_GEOFENCES."
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS ->
                "O Android recusou o cadastro dos avisos. Toque em \"Tentar de novo\"."
            // GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION
            1004 -> "Falta a permissão de localização \"Permitir o tempo todo\"."
            // GEOFENCE_REQUEST_TOO_FREQUENTLY
            1005 -> "O Android pediu para esperar um pouco. Tente de novo em alguns minutos."
            null -> "O Android recusou os avisos por um motivo desconhecido."
            else -> "O Android recusou os avisos (código $code)."
        }

    /** Recadastra a partir da cópia local (usado após reiniciar o celular). */
    fun resyncFromCache(context: Context) {
        sync(context, Prefs(context).cachedStores())
    }
}
