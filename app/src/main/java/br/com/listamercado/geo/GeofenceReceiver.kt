package br.com.listamercado.geo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.listamercado.Notifier
import br.com.listamercado.data.Prefs
import br.com.listamercado.data.Repository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Chamado pelo Android quando a pessoa fica alguns minutos dentro do raio de um mercado. */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Erro no geofence: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            return
        }
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_DWELL &&
            transition != Geofence.GEOFENCE_TRANSITION_ENTER
        ) return

        val prefs = Prefs(context)
        val groupId = prefs.groupId ?: return
        val triggeredIds = event.triggeringGeofences.orEmpty().map { it.requestId }.toSet()
        val now = System.currentTimeMillis()

        val store = prefs.cachedStores().firstOrNull { s ->
            s.id in triggeredIds && now - prefs.lastNotifiedAt(s.id) > COOLDOWN_MS
        } ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = Repository().fetchPendingItemNames(groupId)
                if (items.isNotEmpty()) {
                    Notifier.showStoreAlert(context, store.name, items)
                    prefs.markNotified(store.id, now)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível montar o aviso", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"

        /** No máximo um aviso por mercado nesse intervalo */
        const val COOLDOWN_MS = 2 * 60 * 60 * 1000L
    }
}
