package chat.simplex.common.views.wallet

import chat.simplex.common.platform.ntfManager
import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Wallet notification service.
 *
 * Uses the same SimpleX NtfManager that powers chat notifications
 * so users see wallet events (send / receive / swap) in their
 * standard notification shade — no extra setup required.
 *
 * Also keeps an in-app notification list for the wallet history view.
 */
object NotificationService {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var config = NotificationConfig()

    fun configure(cfg: NotificationConfig) {
        config = cfg
        if (cfg.enabled) startListening()
    }

    // ── Public state ───────────────────────────────────────────────
    private val _notifications = MutableStateFlow<List<WalletNotification>>(emptyList())
    val notifications: StateFlow<List<WalletNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── De-duplication ─────────────────────────────────────────────
    private val seenIds = ConcurrentHashMap.newKeySet<String>()
    private const val MAX_SEEN = 5_000
    @Volatile private var _seeded = false

    /**
     * Pre-seed the dedup set from stored transactions so that
     * notifications for old transactions are never re-shown after
     * an app restart or phone reboot.
     */
    fun seedFromStoredTransactions() {
        if (_seeded) return
        _seeded = true
        try {
            val stored = PlatformWallet.getStoredTransactions()
            for (tx in stored) {
                seenIds.add("recv_${tx.txHash}")
                seenIds.add("send_${tx.txHash}")
                seenIds.add("swap_ok_${tx.txHash}")
            }
        } catch (_: Exception) { }
    }

    // ── Offline buffer ─────────────────────────────────────────────
    private val offlineQueue = LinkedBlockingQueue<WalletNotification>(500)
    private var retryJob: Job? = null

    var onLocalNotification: ((WalletNotification) -> Unit)? = null

    // ═══════════════════════════════════════════════════════════════
    //  Event emitters – called by WalletCoreService / SwapService
    // ═══════════════════════════════════════════════════════════════

    fun notifyTokensReceived(
        network: BlockchainNetwork,
        symbol: String,
        amount: String,
        txHash: String,
        fromAddress: String
    ) {
        emit(
            WalletNotification(
                id = "recv_$txHash",
                type = NotificationType.TOKENS_RECEIVED,
                title = String.format(generalGetString(MR.strings.wallet_notif_received_title), symbol),
                body = String.format(generalGetString(MR.strings.wallet_notif_received_body), amount, symbol, network.displayName),
                network = network,
                txHash = txHash,
                metadata = mapOf("from" to fromAddress, "symbol" to symbol, "amount" to amount)
            )
        )
    }

    fun notifyTokensSent(
        network: BlockchainNetwork,
        symbol: String,
        amount: String,
        txHash: String,
        toAddress: String
    ) {
        emit(
            WalletNotification(
                id = "send_$txHash",
                type = NotificationType.TOKENS_SENT,
                title = String.format(generalGetString(MR.strings.wallet_notif_sent_title), symbol),
                body = String.format(generalGetString(MR.strings.wallet_notif_sent_body), amount, symbol, network.displayName),
                network = network,
                txHash = txHash,
                metadata = mapOf("to" to toAddress, "symbol" to symbol, "amount" to amount)
            )
        )
    }

    fun notifySwapCompleted(
        network: BlockchainNetwork,
        fromSymbol: String,
        toSymbol: String,
        fromAmount: String,
        toAmount: String,
        txHash: String,
        provider: String
    ) {
        emit(
            WalletNotification(
                id = "swap_ok_$txHash",
                type = NotificationType.SWAP_COMPLETED,
                title = generalGetString(MR.strings.wallet_notif_swap_complete_title),
                body = String.format(generalGetString(MR.strings.wallet_notif_swap_complete_body), fromAmount, fromSymbol, toAmount, toSymbol, provider),
                network = network,
                txHash = txHash,
                metadata = mapOf(
                    "from" to fromSymbol, "to" to toSymbol,
                    "fromAmount" to fromAmount, "toAmount" to toAmount, "provider" to provider
                )
            )
        )
    }

    fun notifySwapFailed(
        network: BlockchainNetwork,
        fromSymbol: String,
        toSymbol: String,
        reason: String,
        txHash: String?
    ) {
        emit(
            WalletNotification(
                id = "swap_fail_${txHash ?: System.currentTimeMillis()}",
                type = NotificationType.SWAP_FAILED,
                title = generalGetString(MR.strings.wallet_notif_swap_failed_title),
                body = String.format(generalGetString(MR.strings.wallet_notif_swap_failed_body), fromSymbol, toSymbol, reason),
                network = network,
                txHash = txHash,
                metadata = mapOf("from" to fromSymbol, "to" to toSymbol, "reason" to reason)
            )
        )
    }

    fun notifySendFailed(
        network: BlockchainNetwork,
        symbol: String,
        amount: String,
        reason: String
    ) {
        emit(
            WalletNotification(
                id = "send_fail_${System.currentTimeMillis()}",
                type = NotificationType.SEND_FAILED,
                title = generalGetString(MR.strings.wallet_notif_send_failed_title),
                body = String.format(generalGetString(MR.strings.wallet_notif_send_failed_body), amount, symbol, reason),
                network = network,
                metadata = mapOf("symbol" to symbol, "amount" to amount, "reason" to reason)
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Mark read / clear
    // ═══════════════════════════════════════════════════════════════

    fun markRead(notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        _unreadCount.value = _notifications.value.count { !it.isRead }
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value = 0
    }

    fun clearAll() {
        _notifications.value = emptyList()
        _unreadCount.value = 0
    }

    // ═══════════════════════════════════════════════════════════════
    //  ntfy delivery
    // ═══════════════════════════════════════════════════════════════

    private fun publishToNtfy(notification: WalletNotification): Boolean {
        val baseUrl = config.ntfyServerUrl.ifBlank { return false }
        val topic = config.ntfyTopic.ifBlank { return false }
        val headers = mapOf(
            "Title" to notification.title,
            "Priority" to if (notification.type.isFailure) "high" else "default",
            "Tags" to notification.type.tag
        )
        return try {
            val result = SecureHttp.postJsonWithHeaders("$baseUrl/$topic", notification.body, headers)
            result != null
        } catch (_: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  WebSocket fallback listener (subscribes to ntfy via SSE)
    // ═══════════════════════════════════════════════════════════════

    private var wsJob: Job? = null
    @Volatile private var wsBackoffMs = 1_000L
    private const val WS_MAX_BACKOFF = 60_000L

    private fun startListening() {
        wsJob?.cancel()
        wsJob = scope.launch {
            while (isActive && config.enabled) {
                try {
                    _connectionState.value = ConnectionState.CONNECTING
                    connectSse()
                } catch (_: CancellationException) {
                    break
                } catch (_: Exception) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
                delay(wsBackoffMs)
                wsBackoffMs = (wsBackoffMs * 2).coerceAtMost(WS_MAX_BACKOFF)
            }
        }
    }

    // SSE requires a persistent streaming connection that SecureHttp cannot provide,
    // so we use raw HttpURLConnection here.
    private fun connectSse() {
        val baseUrl = config.ntfyServerUrl.ifBlank { return }
        val topic = config.ntfyTopic.ifBlank { return }

        val url = java.net.URL("$baseUrl/$topic/sse")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.connectTimeout = 15_000
        conn.readTimeout = 0 // infinite for SSE

        if (conn.responseCode != 200) return

        _connectionState.value = ConnectionState.CONNECTED
        wsBackoffMs = 1_000L

        conn.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    handleRemoteMessage(payload)
                }
            }
        }
    }

    private fun handleRemoteMessage(payload: String) {
        try {
            val obj = json.parseToJsonElement(payload).jsonObject
            val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return
            if (event != "message") return

            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
            val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: ""

            if (!seenIds.add("remote_$id")) return

            val notification = WalletNotification(
                id = "remote_$id",
                type = NotificationType.GENERIC,
                title = title,
                body = message
            )
            appendNotification(notification)
        } catch (_: Exception) { }
    }

    fun stopListening() {
        wsJob?.cancel()
        wsJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal plumbing
    // ═══════════════════════════════════════════════════════════════

    private fun emit(notification: WalletNotification) {
        if (!seenIds.add(notification.id)) return
        if (seenIds.size > MAX_SEEN) {
            seenIds.clear()
        }

        appendNotification(notification)
        onLocalNotification?.invoke(notification)

        // Show via the same SimpleX notification system used for chat messages
        try {
            ntfManager.showMessage(notification.title, notification.body)
        } catch (_: Exception) { }

        scope.launch {
            if (!publishToNtfy(notification)) {
                offlineQueue.offer(notification)
                scheduleRetry()
            }
        }
    }

    private fun appendNotification(notification: WalletNotification) {
        val current = _notifications.value.toMutableList()
        current.add(0, notification)
        if (current.size > 200) current.subList(200, current.size).clear()
        _notifications.value = current
        _unreadCount.value = current.count { !it.isRead }
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            var backoff = 2_000L
            while (offlineQueue.isNotEmpty()) {
                val item = offlineQueue.peek() ?: break
                if (publishToNtfy(item)) {
                    offlineQueue.poll()
                    backoff = 2_000L
                } else {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Data models
// ═══════════════════════════════════════════════════════════════════

data class NotificationConfig(
    val enabled: Boolean = true,
    val ntfyServerUrl: String = "https://ntfy.sh",
    val ntfyTopic: String = "",
    val showOnReceive: Boolean = true,
    val showOnSend: Boolean = true,
    val showOnSwap: Boolean = true,
    val showOnFailure: Boolean = true
)

@Serializable
data class WalletNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val network: BlockchainNetwork? = null,
    val txHash: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class NotificationType(val tag: String, val isFailure: Boolean = false) {
    TOKENS_RECEIVED("white_check_mark"),
    TOKENS_SENT("arrow_up"),
    SWAP_COMPLETED("repeat"),
    SWAP_FAILED("x", isFailure = true),
    SEND_FAILED("x", isFailure = true),
    GENERIC("bell")
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}
