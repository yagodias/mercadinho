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
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Aviso de mercado",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Avisa quando você está num mercado e há itens na lista"
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Permissão concedida, app não bloqueado e canal "Aviso de mercado" não silenciado. */
    fun canNotify(context: Context): Boolean {
        val permission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        val channelOn =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
                channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
            } else {
                true
            }
        return permission && manager.areNotificationsEnabled() && channelOn
    }

    /**
     * @param test true para o botão "Testar aviso": mostra a notificação mesmo com a lista vazia.
     */
    @SuppressLint("MissingPermission")
    fun showStoreAlert(
        context: Context,
        storeName: String,
        items: List<String>,
        test: Boolean = false,
    ) {
        if (!canNotify(context)) return
        if (items.isEmpty() && !test) return

        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val title = if (test) "Teste · $storeName" else storeName
        val summary =
            when (items.size) {
                0 -> "A lista está vazia. No mercado, o aviso só aparece quando há itens pendentes."
                1 -> "Tem 1 item na lista: ${items.first()}"
                else -> "Tem ${items.size} itens na lista de compras"
            }

        val style =
            if (items.isEmpty()) {
                NotificationCompat.BigTextStyle().bigText(summary)
            } else {
                NotificationCompat.InboxStyle().setBigContentTitle(title).also { inbox ->
                    items.take(MAX_LINES).forEach { inbox.addLine("• $it") }
                    if (items.size > MAX_LINES) inbox.setSummaryText("e mais ${items.size - MAX_LINES}")
                }
            }

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_cart)
                .setContentTitle(title)
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
