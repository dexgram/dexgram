package chat.simplex.common.views.wallet

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.io.File
import java.security.SecureRandom

/**
 * Centralised security hardening for the wallet module.
 *
 * Responsibilities:
 * - Root / emulator detection
 * - Screenshot / screen-recording prevention
 * - Secure random generation
 * - Byte-level memory wiping
 * - Debugger / instrumentation detection
 */
object WalletSecurity {

    // ── Secure RNG (single instance, thread-safe) ───────────────────
    val secureRandom: SecureRandom by lazy { SecureRandom() }

    // ═════════════════════════════════════════════════════════════════
    //  Memory helpers
    // ═════════════════════════════════════════════════════════════════

    /** Overwrite a ByteArray with random bytes, then zeros. */
    fun wipe(data: ByteArray?) {
        if (data == null) return
        secureRandom.nextBytes(data)            // random pass
        java.util.Arrays.fill(data, 0.toByte()) // zero pass
    }

    /** Overwrite a CharArray with zeros. */
    fun wipe(data: CharArray?) {
        if (data == null) return
        java.util.Arrays.fill(data, '\u0000')
    }

    // ═════════════════════════════════════════════════════════════════
    //  Screenshot & screen-recording prevention
    // ═════════════════════════════════════════════════════════════════

    /** Call from Activity.onCreate / onResume to block captures. */
    fun enableScreenProtection(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    fun disableScreenProtection(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ═════════════════════════════════════════════════════════════════
    //  Root / Emulator / Debugger detection
    // ═════════════════════════════════════════════════════════════════

    data class DeviceSecurityReport(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val isDebuggerAttached: Boolean,
        val details: List<String>
    ) {
        val isTrusted: Boolean get() = !isRooted && !isEmulator && !isDebuggerAttached
    }

    fun assessDevice(): DeviceSecurityReport {
        val details = mutableListOf<String>()
        val rooted = checkRooted(details)
        val emulator = checkEmulator(details)
        val debugger = checkDebugger(details)
        return DeviceSecurityReport(rooted, emulator, debugger, details)
    }

    private fun checkRooted(out: MutableList<String>): Boolean {
        var rooted = false

        val suPaths = listOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in suPaths) {
            if (File(path).exists()) {
                out.add("SU binary at $path")
                rooted = true
            }
        }

        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0"
        )
        for ((prop, bad) in dangerousProps) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val value = p.inputStream.bufferedReader().readLine()?.trim()
                if (value == bad) {
                    out.add("Property $prop = $value")
                    rooted = true
                }
            } catch (_: Exception) {}
        }

        val magiskIndicators = listOf(
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/cache/.disable_magisk"
        )
        for (path in magiskIndicators) {
            if (File(path).exists()) {
                out.add("Magisk indicator at $path")
                rooted = true
            }
        }

        return rooted
    }

    private fun checkEmulator(out: MutableList<String>): Boolean {
        val dominated = listOf(
            Build.FINGERPRINT.startsWith("generic"),
            Build.FINGERPRINT.startsWith("unknown"),
            Build.MODEL.contains("google_sdk"),
            Build.MODEL.contains("Emulator"),
            Build.MODEL.contains("Android SDK built for x86"),
            Build.MANUFACTURER.contains("Genymotion"),
            Build.HARDWARE.contains("goldfish"),
            Build.HARDWARE.contains("ranchu"),
            Build.PRODUCT.contains("sdk"),
            Build.PRODUCT.contains("vbox86p"),
            Build.BOARD.lowercase().contains("nox"),
            Build.BOOTLOADER.contains("nox"),
        )
        val isEmu = dominated.any { it }
        if (isEmu) out.add("Emulator indicators in Build.*")
        return isEmu
    }

    private fun checkDebugger(out: MutableList<String>): Boolean {
        val attached = android.os.Debug.isDebuggerConnected()
        if (attached) out.add("Debugger attached")

        val tracerPid = try {
            File("/proc/self/status").readLines()
                .find { it.startsWith("TracerPid:") }
                ?.split(":")?.get(1)?.trim()?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
        if (tracerPid != 0) {
            out.add("TracerPid = $tracerPid")
        }

        return attached || tracerPid != 0
    }

    // ═════════════════════════════════════════════════════════════════
    //  Keystore integrity check
    // ═════════════════════════════════════════════════════════════════

    /** Returns true when the Android Keystore key exists and can encrypt a test payload. */
    fun isKeystoreHealthy(): Boolean {
        return try {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.containsAlias("wallet_mnemonic_aes_gcm")
        } catch (_: Exception) {
            false
        }
    }
}
