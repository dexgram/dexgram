package chat.simplex.common.views.wallet

import java.math.BigDecimal

/**
 * Validates all user inputs before they reach the wallet / swap layer.
 * Prevents injection, overflow, and invalid data from propagating.
 */
object InputValidator {

    // ── Address validation ────────────────────────────────────────

    fun isValidEvmAddress(address: String): Boolean {
        if (!address.startsWith("0x")) return false
        if (address.length != 42) return false
        return address.substring(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    fun isValidBitcoinAddress(address: String): Boolean {
        if (address.startsWith("bc1")) return address.length in 42..62
        if (address.startsWith("1") || address.startsWith("3")) return address.length in 25..34
        return false
    }

    fun isValidTronAddress(address: String): Boolean {
        return address.startsWith("T") && address.length == 34 && address.all { it.isLetterOrDigit() }
    }

    fun isValidSolanaAddress(address: String): Boolean {
        return address.length in 32..44 && address.all { it.isLetterOrDigit() }
    }

    fun isValidAddress(address: String, network: BlockchainNetwork): Boolean {
        return when {
            network.isEvm -> isValidEvmAddress(address)
            network == BlockchainNetwork.BITCOIN -> isValidBitcoinAddress(address)
            network == BlockchainNetwork.LITECOIN -> address.length in 25..34
            network == BlockchainNetwork.DOGECOIN -> address.length in 25..34
            network == BlockchainNetwork.TRON -> isValidTronAddress(address)
            network == BlockchainNetwork.SOLANA -> isValidSolanaAddress(address)
            network == BlockchainNetwork.RIPPLE -> address.startsWith("r") && address.length in 25..34
            else -> address.isNotBlank()
        }
    }

    // ── Amount validation ─────────────────────────────────────────

    fun isValidAmount(amount: String): Boolean {
        if (amount.isBlank()) return false
        val value = amount.toDoubleOrNull() ?: return false
        if (value <= 0) return false
        if (value > 1e18) return false
        if (amount.contains("e", ignoreCase = true)) return false
        val parts = amount.split(".")
        if (parts.size > 2) return false
        if (parts.size == 2 && parts[1].length > 18) return false
        return true
    }

    fun sanitizeAmount(input: String): String {
        var result = input.filter { it.isDigit() || it == '.' }
        val dotIndex = result.indexOf('.')
        if (dotIndex >= 0) {
            val before = result.substring(0, dotIndex + 1)
            val after = result.substring(dotIndex + 1).replace(".", "")
            result = before + after
        }
        if (result.startsWith(".")) result = "0$result"
        return result
    }

    fun isAmountWithinBalance(amount: String, balance: String): Boolean {
        val amountDec = try { BigDecimal(amount) } catch (_: Exception) { return false }
        val balanceDec = try { BigDecimal(balance) } catch (_: Exception) { return false }
        return amountDec <= balanceDec
    }

    // ── Slippage validation ───────────────────────────────────────

    fun isValidSlippage(slippage: Double): Boolean {
        return slippage in ProductionConfig.MIN_SLIPPAGE_PERCENT..ProductionConfig.MAX_SLIPPAGE_PERCENT
    }

    // ── Token contract address ────────────────────────────────────

    fun isValidContractAddress(address: String, network: BlockchainNetwork): Boolean {
        if (network.isEvm) return isValidEvmAddress(address)
        if (network == BlockchainNetwork.TRON) return isValidTronAddress(address)
        if (network == BlockchainNetwork.SOLANA) return isValidSolanaAddress(address)
        return address.isNotBlank()
    }

    // ── Memo validation ───────────────────────────────────────────

    fun sanitizeMemo(memo: String, maxLength: Int = 256): String {
        return memo.take(maxLength).filter { it.code in 32..126 || it == '\n' }
    }

    // ── Hex validation ────────────────────────────────────────────

    fun isValidHex(hex: String): Boolean {
        val clean = hex.removePrefix("0x")
        return clean.isNotEmpty() && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}
