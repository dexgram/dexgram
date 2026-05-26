package chat.simplex.common.views.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Desktop implementation of PlatformWallet.
 * Generates format-correct addresses for all chains from the mnemonic hash.
 * Real transaction sending is only supported on Android.
 */
actual object PlatformWallet {
    private var currentMnemonic: String? = null
    private var walletAccounts = mutableListOf<WalletAccount>()

    private val bip39Words = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
        "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
        "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
        "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
        "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
        "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone",
        "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among",
        "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry",
        "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
        "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april",
        "arch", "arctic", "area", "arena", "argue", "arm", "armed", "armor",
        "army", "around", "arrange", "arrest", "arrive", "arrow", "art", "artefact",
        "artist", "artwork", "ask", "aspect", "assault", "asset", "assist", "assume",
        "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction",
        "audit", "august", "aunt", "author", "auto", "autumn", "average", "avocado",
        "avoid", "awake", "aware", "away", "awesome", "awful", "awkward", "axis",
        "baby", "bachelor", "bacon", "badge", "bag", "balance", "balcony", "ball",
        "bamboo", "banana", "banner", "bar", "barely", "bargain", "barrel", "base",
        "basic", "basket", "battle", "beach", "bean", "beauty", "because", "become",
        "beef", "before", "begin", "behave", "behind", "believe", "below", "belt",
        "bench", "benefit", "best", "betray", "better", "between", "beyond", "bicycle"
    )

    actual fun initialize(context: Any?) { /* no-op on desktop */ }

    actual fun createWallet(): WalletCreationResult {
        return try {
            val mnemonic = generateMnemonic()
            currentMnemonic = mnemonic
            val accounts = createAllChainAccounts(mnemonic)
            walletAccounts.clear()
            walletAccounts.addAll(accounts)
            WalletCreationResult(mnemonic, accounts, true)
        } catch (e: Exception) {
            WalletCreationResult("", emptyList(), false, e.message)
        }
    }

    actual fun generateNewMnemonic(): String = generateMnemonic()

    actual fun recoverWallet(mnemonic: String): WalletCreationResult {
        return try {
            if (!validateMnemonic(mnemonic)) {
                return WalletCreationResult("", emptyList(), false, "Invalid recovery phrase")
            }
            currentMnemonic = mnemonic
            val accounts = createAllChainAccounts(mnemonic)
            walletAccounts.clear()
            walletAccounts.addAll(accounts)
            WalletCreationResult(mnemonic, accounts, true)
        } catch (e: Exception) {
            WalletCreationResult("", emptyList(), false, e.message)
        }
    }

    actual fun validateMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().lowercase().split("\\s+".toRegex())
        return words.size == 12 || words.size == 24
    }

    actual fun getAddress(network: BlockchainNetwork): String {
        val mnemonic = currentMnemonic ?: return ""
        return generateDeterministicAddress(mnemonic, network)
    }

    actual suspend fun fetchBalance(account: WalletAccount): String = withContext(Dispatchers.IO) { "0" }

    actual suspend fun sendTransaction(request: SendTransactionRequest): Result<WalletTransaction> =
        withContext(Dispatchers.IO) {
            Result.failure(Exception("Transaction sending not supported on desktop. Please use the mobile app."))
        }

    actual suspend fun estimateFee(network: BlockchainNetwork): FeeEstimate = withContext(Dispatchers.IO) {
        FeeEstimate(
            network = network,
            slow = FeeOption("20", 21000, "0.00042", 0.84, "~10 min"),
            normal = FeeOption("30", 21000, "0.00063", 1.26, "~3 min"),
            fast = FeeOption("50", 21000, "0.00105", 2.10, "~30 sec")
        )
    }

    actual fun isInitialized(): Boolean = currentMnemonic != null
    actual fun hasPersistedWallet(): Boolean = false
    actual fun getMnemonic(): String? = currentMnemonic

    actual fun clearWallet() {
        currentMnemonic = null
        walletAccounts.clear()
    }

    actual fun validateAddress(address: String, network: BlockchainNetwork): Boolean {
        return when {
            network.isEvm -> address.startsWith("0x") && address.length == 42
            network == BlockchainNetwork.BITCOIN ->
                address.startsWith("bc1") || (address.startsWith("1") && address.length in 25..34)
            network == BlockchainNetwork.LITECOIN ->
                address.startsWith("ltc1") || address.startsWith("L")
            network == BlockchainNetwork.DOGECOIN ->
                address.startsWith("D") && address.length in 25..34
            network == BlockchainNetwork.TRON ->
                address.startsWith("T") && address.length == 34
            network == BlockchainNetwork.SOLANA ->
                address.length in 32..44
            network == BlockchainNetwork.RIPPLE ->
                address.startsWith("r") && address.length in 25..35
            network == BlockchainNetwork.NEAR ->
                (address.length == 64 && address.all { it in '0'..'9' || it in 'a'..'f' }) ||
                    (address.endsWith(".near") && address.length in 5..64)
            else -> false
        }
    }

    actual suspend fun fetchTransactions(address: String, network: BlockchainNetwork): List<WalletTransaction> =
        withContext(Dispatchers.IO) { emptyList() }

    actual suspend fun checkTransactionStatus(txHash: String, network: BlockchainNetwork): TransactionStatus =
        withContext(Dispatchers.IO) { TransactionStatus.PENDING }

    actual fun getStoredTransactions(): List<WalletTransaction> = emptyList()

    actual fun addTransaction(tx: WalletTransaction) { }

    actual suspend fun fetchTokenBalance(token: WalletToken, walletAddress: String): String =
        withContext(Dispatchers.IO) { "0" }

    actual suspend fun fetchAllTokenBalances(network: BlockchainNetwork, walletAddress: String): List<WalletToken> =
        withContext(Dispatchers.IO) { PopularTokens.getTokensForNetwork(network) }

    actual suspend fun sendTokenTransaction(
        token: WalletToken, fromAddress: String, toAddress: String, amount: String
    ): Result<WalletTransaction> = withContext(Dispatchers.IO) {
        Result.failure(Exception("Token transfers not supported on desktop"))
    }

    actual suspend fun sendRawTransaction(
        network: BlockchainNetwork, to: String, data: String, value: String, gasLimit: String
    ): Result<String> = withContext(Dispatchers.IO) {
        Result.failure(Exception("Raw transactions not supported on desktop"))
    }

    // ── Multi-wallet persistent storage (in-memory for desktop) ─────

    private val storedProfiles = mutableMapOf<String, String>()
    private val storedMnemonics = mutableMapOf<String, String>()

    actual fun saveWalletProfiles(profilesJson: String) { storedProfiles["profiles"] = profilesJson }
    actual fun loadWalletProfiles(): String? = storedProfiles["profiles"]
    actual fun saveMnemonicForWallet(walletId: String, mnemonic: String) { storedMnemonics[walletId] = mnemonic }
    actual fun loadMnemonicForWallet(walletId: String): String? = storedMnemonics[walletId]
    actual fun deleteMnemonicForWallet(walletId: String) { storedMnemonics.remove(walletId) }

    actual fun saveBalanceSnapshot(accounts: List<WalletAccount>, tokens: List<WalletToken>) { /* desktop stub */ }
    actual fun loadBalanceSnapshot(): BalanceSnapshot? = null

    // ── Internal helpers ────────────────────────────────────────────

    private fun generateMnemonic(): String {
        val random = SecureRandom()
        return (1..12).joinToString(" ") { bip39Words[random.nextInt(bip39Words.size)] }
    }

    private fun createAllChainAccounts(mnemonic: String): List<WalletAccount> {
        return BlockchainNetwork.ALL_SUPPORTED.mapNotNull { network ->
            val address = generateDeterministicAddress(mnemonic, network)
            if (address.isEmpty()) return@mapNotNull null
            WalletAccount(
                id = UUID.randomUUID().toString(),
                name = network.displayName,
                network = network,
                address = address,
                publicKey = "",
                isImported = false,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * Generates a deterministic, format-correct address from mnemonic for display.
     * On desktop these are NOT cryptographically valid — real derivation is on Android.
     */
    private fun generateDeterministicAddress(mnemonic: String, network: BlockchainNetwork): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest("${mnemonic}:${network.coinType}".toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }

        return when (network) {
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.BINANCE_SMART_CHAIN,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.ARBITRUM,
            BlockchainNetwork.OPTIMISM,
            BlockchainNetwork.AVALANCHE,
            BlockchainNetwork.BASE -> "0x${hex.substring(0, 40)}"

            BlockchainNetwork.BITCOIN -> "bc1q${hex.substring(0, 38)}"
            BlockchainNetwork.LITECOIN -> "ltc1q${hex.substring(0, 38)}"
            BlockchainNetwork.DOGECOIN -> "D${hex.substring(0, 33)}"
            BlockchainNetwork.TRON -> "T${hex.substring(0, 33)}"
            BlockchainNetwork.SOLANA -> hex.substring(0, 44)
            BlockchainNetwork.RIPPLE -> "r${hex.substring(0, 33)}"
            BlockchainNetwork.NEAR -> hex.substring(0, 64)
            BlockchainNetwork.CARDANO -> ""
        }
    }
}
