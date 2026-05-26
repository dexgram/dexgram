package chat.simplex.common.views.wallet

/**
 * Central production configuration.
 * Toggle PRODUCTION_MODE = true for release builds.
 */
object ProductionConfig {

    const val PRODUCTION_MODE = true
    const val REVIEW_MODE = false

    // ── Swap defaults ─────────────────────────────────────────────
    const val DEFAULT_SLIPPAGE_PERCENT = 0.5
    const val MAX_SLIPPAGE_PERCENT = 50.0
    const val MIN_SLIPPAGE_PERCENT = 0.01
    const val SWAP_QUOTE_TIMEOUT_MS = 15_000L
    const val SWAP_TX_TIMEOUT_MS = 300_000L
    const val SWAP_CONFIRMATION_BLOCKS = 1

    // ── Gas ───────────────────────────────────────────────────────
    const val DEFAULT_GAS_LIMIT_ERC20_TRANSFER = 65_000L
    const val DEFAULT_GAS_LIMIT_SWAP = 300_000L
    const val DEFAULT_GAS_LIMIT_APPROVE = 60_000L
    const val GAS_PRICE_MULTIPLIER = 1.1 // 10% buffer

    // ── RPC ───────────────────────────────────────────────────────
    const val RPC_CONNECT_TIMEOUT_MS = 15_000
    const val RPC_READ_TIMEOUT_MS = 15_000
    const val RPC_MAX_RETRIES = 3
    const val RPC_BACKOFF_BASE_MS = 1_000L
    const val RPC_BACKOFF_MAX_MS = 8_000L

    // ── Balance refresh ───────────────────────────────────────────
    const val BALANCE_AUTO_REFRESH_MS = 30_000L
    const val BALANCE_BACKGROUND_REFRESH_MS = 300_000L

    // ── Notifications ─────────────────────────────────────────────
    const val NTFY_DEFAULT_SERVER = "https://ntfy.sh"
    const val NOTIFICATION_MAX_STORED = 200
    const val TRANSFER_SCAN_INTERVAL_MS = 15_000L

    // ── Security ──────────────────────────────────────────────────
    const val AUTO_LOCK_TIMEOUT_MS = 5 * 60 * 1000L
    const val KEYSTORE_ALIAS = "wallet_mnemonic_aes_gcm"
    // Set to true to enable certificate pinning. Use dummy pins first to extract real ones from logcat.
    const val ENABLE_CERT_PINNING = true
    const val ENABLE_ROOT_DETECTION = true
    const val ENABLE_SCREENSHOT_PROTECTION = true

    // ── WorkManager tags ──────────────────────────────────────────
    const val WORK_BALANCE_SYNC = "wallet_balance_sync"
    const val WORK_PENDING_SWAP_CHECK = "wallet_pending_swap_check"
    const val WORK_TRANSFER_SCAN = "wallet_transfer_scan"
}
