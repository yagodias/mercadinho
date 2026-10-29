package br.com.listamercado.geo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** O Android apaga as cercas ao reiniciar ou atualizar o app; aqui elas voltam. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> GeofenceManager.resyncFromCache(context)
        }
    }
}
