package chat.simplex.common.views.wallet

actual object SecureLog {
    @Volatile
    var debugEnabled: Boolean = false

    actual fun d(tag: String, msg: String) {
        if (debugEnabled) android.util.Log.d(tag, msg)
    }

    actual fun w(tag: String, msg: String) {
        if (debugEnabled) android.util.Log.w(tag, msg)
    }

    actual fun e(tag: String, msg: String, t: Throwable?) {
        if (debugEnabled) {
            if (t != null) android.util.Log.e(tag, msg, t)
            else android.util.Log.e(tag, msg)
        }
    }

    actual fun i(tag: String, msg: String) {
        if (debugEnabled) android.util.Log.i(tag, msg)
    }
}
