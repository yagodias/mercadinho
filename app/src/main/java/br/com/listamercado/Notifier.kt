package br.com.listamercado

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {

    private const val CHANNEL_ID = "aviso_mercado"
    private const val NOTIFICATION_ID = 1001
    private const val MAX_LINES = 6

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aviso de mercado",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa quando você está num mercado e há itens na lista"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showStoreAlert(context: Context, storeName: String, items: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val summary = if (items.size == 1) {
            "Tem 1 item na lista: ${items.first()}"
        } else {
            "Tem ${items.size} itens na lista de compras"
        }

        val style = NotificationCompat.InboxStyle().setBigContentTitle(storeName)
        items.take(MAX_LINES).forEach { style.addLine("• $it") }
        if (items.size > MAX_LINES) style.setSummaryText("e mais ${items.size - MAX_LINES}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cart)
            .setContentTitle(storeName)
            .setContentText(summary)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
