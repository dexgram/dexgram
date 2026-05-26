package chat.simplex.common.views.wallet

/**
 * Logging wrapper that is a no-op in release builds.
 * All wallet/payment/swap logging should go through this
 * instead of platform logging directly.
 */
expect object SecureLog {
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, t: Throwable?)
    fun i(tag: String, msg: String)
}
