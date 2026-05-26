package chat.simplex.common.views.wallet

actual object SecureLog {
    actual fun d(tag: String, msg: String) {
        // Desktop: no-op in production; uncomment for local debugging
        // println("D/$tag: $msg")
    }

    actual fun w(tag: String, msg: String) {
        // println("W/$tag: $msg")
    }

    actual fun e(tag: String, msg: String, t: Throwable?) {
        // println("E/$tag: $msg")
    }

    actual fun i(tag: String, msg: String) {
        // println("I/$tag: $msg")
    }
}
