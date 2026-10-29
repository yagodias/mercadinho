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

/**
 * Registra os mercados do grupo como "cercas" no Android. Quem vigia a
 * localização é o próprio sistema (Google Play Services), mesmo com o app fechado.
 */
object GeofenceManager {

    private const val TAG = "GeofenceManager"

    /** Limite do Android por app */
    private const val MAX_GEOFENCES = 100

    /** Tempo dentro do raio antes de avisar. Evita disparar ao passar de carro na frente. */
    const val LOITERING_MS = 2 * 60 * 1000

    fun hasPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
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
    fun sync(context: Context, stores: List<Store>) {
        val appContext = context.applicationContext
        Prefs(appContext).cacheStores(stores)

        val client = LocationServices.getGeofencingClient(appContext)
        val pi = pendingIntent(appContext)

        client.removeGeofences(pi).addOnCompleteListener {
            if (stores.isEmpty()) return@addOnCompleteListener
            if (!hasPermissions(appContext)) {
                Log.i(TAG, "Sem permissão de localização em segundo plano; avisos desativados")
                return@addOnCompleteListener
            }

            val fences = stores.take(MAX_GEOFENCES).map { s ->
                Geofence.Builder()
                    .setRequestId(s.id)
                    .setCircularRegion(s.lat, s.lng, s.radius.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                    .setLoiteringDelay(LOITERING_MS)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                // Se a pessoa já estiver dentro de um mercado, também avisa
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofences(fences)
                .build()

            client.addGeofences(request, pi)
                .addOnSuccessListener { Log.i(TAG, "${fences.size} mercado(s) monitorado(s)") }
                .addOnFailureListener { e ->
                    val code = (e as? ApiException)?.statusCode
                    val reason = code?.let { GeofenceStatusCodes.getStatusCodeString(it) } ?: e.message
                    // GEOFENCE_NOT_AVAILABLE costuma significar localização do celular desligada
                    Log.w(TAG, "Falha ao registrar mercados: $reason")
                }
        }
    }

    /** Recadastra a partir da cópia local (usado após reiniciar o celular). */
    fun resyncFromCache(context: Context) {
        sync(context, Prefs(context).cachedStores())
    }
}
