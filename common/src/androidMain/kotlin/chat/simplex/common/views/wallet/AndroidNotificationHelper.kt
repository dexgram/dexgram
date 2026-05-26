package chat.simplex.common.views.wallet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import chat.simplex.common.platform.androidAppContext
import chat.simplex.res.MR
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android-specific notification display (no Firebase / Google services).
 * Creates notification channels and posts local notifications via
 * [NotificationManager] when [NotificationService] emits wallet events.
 */
object AndroidNotificationHelper {

    private const val CHANNEL_RECEIVE = "wallet_receive"
    private const val CHANNEL_SEND = "wallet_send"
    private const val CHANNEL_SWAP = "wallet_swap"
    private const val CHANNEL_FAILURE = "wallet_failure"

    private var notificationManager: NotificationManager? = null
    private val notifId = AtomicInteger(5000)
    private var smallIconRes: Int = MR.images.qr_icon_new.drawableResId
    private var appContext: Context? = null

    fun initialize(context: Context, iconRes: Int? = null) {
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (iconRes != null) smallIconRes = iconRes

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(CHANNEL_RECEIVE, "Tokens Received", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications when you receive crypto"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 100, 250)
                },
                NotificationChannel(CHANNEL_SEND, "Tokens Sent", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications when you send crypto"
                },
                NotificationChannel(CHANNEL_SWAP, "Swaps", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications when swaps complete"
                },
                NotificationChannel(CHANNEL_FAILURE, "Failures", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications when transactions fail"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                }
            )
            channels.forEach { notificationManager?.createNotificationChannel(it) }
        }

        NotificationService.onLocalNotification = { notification ->
            showLocal(notification)
        }
    }

    private fun showLocal(notification: WalletNotification) {
        val ctx = appContext ?: return

        val channelId = when (notification.type) {
            NotificationType.TOKENS_RECEIVED -> CHANNEL_RECEIVE
            NotificationType.TOKENS_SENT -> CHANNEL_SEND
            NotificationType.SWAP_COMPLETED -> CHANNEL_SWAP
            NotificationType.SWAP_FAILED, NotificationType.SEND_FAILED -> CHANNEL_FAILURE
            NotificationType.GENERIC -> CHANNEL_RECEIVE
        }

        val priority = when (notification.type) {
            NotificationType.TOKENS_RECEIVED -> NotificationCompat.PRIORITY_HIGH
            NotificationType.SWAP_FAILED, NotificationType.SEND_FAILED -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        // Tap intent — open the main activity
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pendingIntent = if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            PendingIntent.getActivity(
                ctx, notifId.get(), launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val explorerUrl = if (notification.txHash != null && notification.network != null) {
            notification.network.explorerUrl + notification.txHash
        } else null

        val bigText = buildString {
            append(notification.body)
            if (explorerUrl != null) {
                append("\n\nTap to open wallet")
            }
        }

        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(priority)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        if (notification.type == NotificationType.TOKENS_RECEIVED) {
            builder.setVibrate(longArrayOf(0, 250, 100, 250))
        } else if (notification.type.isFailure) {
            builder.setVibrate(longArrayOf(0, 400, 200, 400))
        }

        notificationManager?.notify(notifId.incrementAndGet(), builder.build())
    }
}
