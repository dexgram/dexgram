package chat.simplex.common.views.wallet

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import chat.simplex.common.platform.androidAppContext
import cash.z.ecc.android.bip39.Mnemonics
import kotlinx.coroutines.*
import org.web3j.crypto.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.Transfer
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

/**
 * Android implementation of PlatformWallet using Web3j
 * With persistent encrypted storage
 */
actual object PlatformWallet {
    private var web3j: Web3j? = null
    private var credentials: Credentials? = null
    private var currentMnemonicChars: CharArray? = null
    private var walletAccounts = mutableListOf<WalletAccount>()
    private var appContext: Context? = null
    
    // Storage constants
    private const val PREFS_NAME = "wallet_secure_prefs"
    private const val KEY_ENCRYPTED_MNEMONIC = "encrypted_mnemonic"
    private const val KEY_WALLET_INITIALIZED = "wallet_initialized"
    private const val KEY_TRANSACTION_HISTORY = "transaction_history"
    private const val SECURE_ALIAS_TX_HISTORY = "tx_history_enc"
    
    // Stored transactions list
    private var storedTransactions = mutableListOf<WalletTransaction>()
    
    @Deprecated("Legacy migration only — will be removed in a future release")
    private fun getLegacyEncryptionKey(): ByteArray? {
        val context = appContext ?: androidAppContext
        val deviceId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        } catch (_: Exception) { null }
        if (deviceId.isNullOrBlank()) return null
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(deviceId.toByteArray())
    }

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS_MNEMONIC = "wallet_mnemonic_aes_gcm"

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        // Try to retrieve existing key — wrap in try/catch because some devices
        // lose StrongBox-backed keys after OTA updates or reboots.
        try {
            val existing = keyStore.getKey(KEY_ALIAS_MNEMONIC, null) as? SecretKey
            if (existing != null) return existing
        } catch (_: Exception) {
            // Key entry exists but is unreadable (StrongBox firmware bug).
            // Delete the broken entry so we can create a fresh key below.
            try { keyStore.deleteEntry(KEY_ALIAS_MNEMONIC) } catch (_: Exception) { }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS_MNEMONIC,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        try {
            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        } catch (_: android.security.keystore.StrongBoxUnavailableException) {
            // StrongBox hardware not available — fall back to TEE-backed key
        } catch (_: Exception) {
            // Some OEMs throw generic exceptions for StrongBox failures
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(false)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
    
    private fun getPrefs(): SharedPreferences? {
        // Use appContext if available, otherwise fallback to androidAppContext
        val context = appContext ?: try { androidAppContext } catch (e: Exception) { null }
        return context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun encryptMnemonic(mnemonic: CharArray): String? {
        val encoder = Charsets.UTF_8.newEncoder()
        val byteBuffer = encoder.encode(java.nio.CharBuffer.wrap(mnemonic))
        val plainBytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(plainBytes)
        return try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainBytes)

            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        } finally {
            WalletSecurity.wipe(plainBytes)
        }
    }
    
    /**
     * Decrypt stored mnemonic. Returns CharArray so callers can wipe after use.
     */
    private fun decryptMnemonicChars(encryptedData: String): CharArray? {
        val combined = try { Base64.decode(encryptedData, Base64.DEFAULT) } catch (_: Exception) { return null }
        if (combined.size <= 12) return null
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)

        // 1. Try Keystore key (primary path)
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS_MNEMONIC, null) as? SecretKey
            if (secretKey != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                val plainBytes = cipher.doFinal(encrypted)
                val chars = String(plainBytes, Charsets.UTF_8).toCharArray()
                WalletSecurity.wipe(plainBytes)
                return chars
            }
        } catch (_: Exception) { }

        // 2. Fallback: legacy device-ID-based key (migration path)
        return decryptMnemonicLegacyChars(encryptedData)
    }

    @Deprecated("Migration path only — will be removed in a future release")
    private fun decryptMnemonicLegacyChars(encryptedData: String): CharArray? {
        val key = getLegacyEncryptionKey() ?: return null
        return try {
            val secretKey = SecretKeySpec(key, "AES")
            val combined = Base64.decode(encryptedData, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(encrypted)
            val chars = String(plainBytes, Charsets.UTF_8).toCharArray()
            WalletSecurity.wipe(plainBytes)
            chars
        } catch (_: Exception) {
            null
        } finally {
            WalletSecurity.wipe(key)
        }
    }
    
    /**
     * Save wallet to persistent storage (encrypted via Android Keystore).
     */
    private fun saveWalletToStorage() {
        val mnemonic = currentMnemonicChars ?: return
        val encrypted = encryptMnemonic(mnemonic) ?: return
        val prefs = getPrefs() ?: return
        prefs.edit().apply {
            putString(KEY_ENCRYPTED_MNEMONIC, encrypted)
            putBoolean(KEY_WALLET_INITIALIZED, true)
            commit()
        }
    }
    
    /**
     * Load wallet from persistent encrypted storage.
     * Legacy-encrypted data is silently migrated to Keystore encryption on first load.
     */
    private fun loadWalletFromStorage(): Boolean {
        val prefs = getPrefs() ?: return false
        if (!prefs.getBoolean(KEY_WALLET_INITIALIZED, false)) return false

        val encryptedMnemonic = prefs.getString(KEY_ENCRYPTED_MNEMONIC, null) ?: return false
        val mnemonicChars = decryptMnemonicChars(encryptedMnemonic) ?: return false

        // One-time migration from legacy encryption to Keystore
        runCatching {
            val reEncrypted = encryptMnemonic(mnemonicChars)
            if (reEncrypted != null && reEncrypted != encryptedMnemonic) {
                prefs.edit().putString(KEY_ENCRYPTED_MNEMONIC, reEncrypted).commit()
            }
        }

        return try {
            currentMnemonicChars = mnemonicChars
            val mnemonicStr = String(mnemonicChars)
            InvoiceHmac.cacheKey(mnemonicStr)
            deriveCredentials(mnemonicStr)
            initializeWeb3j(BlockchainNetwork.ETHEREUM)

            val accounts = createDefaultAccounts()
            walletAccounts.clear()
            walletAccounts.addAll(accounts)

            loadTransactionHistory()
            true
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Wallet load failed", null)
            false
        }
    }
    
    /**
     * Clear wallet from storage
     */
    private fun clearWalletFromStorage() {
        getPrefs()?.edit()?.apply {
            remove(KEY_ENCRYPTED_MNEMONIC)
            remove(KEY_TRANSACTION_HISTORY)
            putBoolean(KEY_WALLET_INITIALIZED, false)
            commit()
        }
        SecureStorage.deleteKey(SECURE_ALIAS_TX_HISTORY)
        SecureStorage.deleteKey(SECURE_ALIAS_EXPLORER_KEYS)
        storedTransactions.clear()
    }
    
    /**
     * Save transaction history to persistent storage
     * Format: id|txHash|network|type|status|from|to|amount|fee|timestamp|confirmations|memo|tokenSymbol|tokenContractAddress
     */
    private fun saveTransactionHistory() {
        try {
            val txStrings = storedTransactions.map { tx ->
                "${tx.id}|${tx.txHash}|${tx.network.name}|${tx.type.name}|${tx.status.name}|${tx.fromAddress}|${tx.toAddress}|${tx.amount}|${tx.fee}|${tx.timestamp}|${tx.confirmations}|${tx.memo ?: ""}|${tx.tokenSymbol ?: ""}|${tx.tokenContractAddress ?: ""}"
            }
            val serialized = txStrings.joinToString("\n")
            SecureStorage.encryptAndStore(SECURE_ALIAS_TX_HISTORY, serialized.toByteArray(Charsets.UTF_8).clone())
        } catch (_: Exception) { }
    }
    
    /**
     * Load transaction history from persistent storage
     * Format: id|txHash|network|type|status|from|to|amount|fee|timestamp|confirmations|memo|tokenSymbol|tokenContractAddress
     */
    private fun loadTransactionHistory() {
        try {
            // Try encrypted storage first, fall back to legacy plain prefs
            val serialized = try {
                val bytes = SecureStorage.decryptFromStore(SECURE_ALIAS_TX_HISTORY)
                bytes?.let { String(it, Charsets.UTF_8) }
            } catch (_: Exception) { null }
                ?: getPrefs()?.getString(KEY_TRANSACTION_HISTORY, null)
                ?: return

            if (serialized.isBlank()) return

            val transactions = serialized.split("\n").mapNotNull { line ->
                try {
                    val parts = line.split("|")
                    if (parts.size >= 10) {
                        WalletTransaction(
                            id = parts[0],
                            txHash = parts[1],
                            network = BlockchainNetwork.valueOf(parts[2]),
                            type = TransactionType.valueOf(parts[3]),
                            status = TransactionStatus.valueOf(parts[4]),
                            fromAddress = parts[5],
                            toAddress = parts[6],
                            amount = parts[7],
                            fee = parts[8],
                            timestamp = parts[9].toLongOrNull() ?: 0L,
                            confirmations = parts.getOrNull(10)?.toIntOrNull() ?: 0,
                            memo = parts.getOrNull(11)?.takeIf { it.isNotBlank() },
                            tokenSymbol = parts.getOrNull(12)?.takeIf { it.isNotBlank() },
                            tokenContractAddress = parts.getOrNull(13)?.takeIf { it.isNotBlank() }
                        )
                    } else null
                } catch (e: Exception) {
                    SecureLog.e("PlatformWallet", "Transaction parsing failed", null)
                    null
                }
            }

            storedTransactions.clear()
            storedTransactions.addAll(transactions)

            // Migrate legacy plain-text history to encrypted storage
            if (transactions.isNotEmpty()) {
                getPrefs()?.edit()?.remove(KEY_TRANSACTION_HISTORY)?.apply()
                saveTransactionHistory()
            }
        } catch (_: Exception) { }
    }
    
    /**
     * Add a transaction and save to storage
     */
    actual fun addTransaction(tx: WalletTransaction) {
        val existingIndex = storedTransactions.indexOfFirst { it.txHash == tx.txHash }
        if (existingIndex >= 0) {
            // Update existing transaction
            storedTransactions[existingIndex] = tx
        } else {
            // Add new transaction at the beginning
            storedTransactions.add(0, tx)
        }
        saveTransactionHistory()
    }
    
    /**
     * Update transaction status and save
     */
    fun updateTransactionStatus(txHash: String, newStatus: TransactionStatus) {
        val index = storedTransactions.indexOfFirst { it.txHash == txHash }
        if (index >= 0) {
            storedTransactions[index] = storedTransactions[index].copy(status = newStatus)
            saveTransactionHistory()
        
        }
    }
    
    /**
     * Get stored transactions from persistent storage
     */
    actual fun getStoredTransactions(): List<WalletTransaction> = storedTransactions.toList()
    
    // RPC endpoints with fallbacks — first reachable endpoint wins
    private val rpcEndpoints = mapOf(
        BlockchainNetwork.ETHEREUM to listOf(
            "https://ethereum-rpc.publicnode.com",
            "https://rpc.ankr.com/eth",
            "https://eth.llamarpc.com",
            "https://1rpc.io/eth"
        ),
        BlockchainNetwork.BINANCE_SMART_CHAIN to listOf(
            "https://bsc-rpc.publicnode.com",
            "https://rpc.ankr.com/bsc",
            "https://bsc-dataseed1.binance.org",
            "https://1rpc.io/bnb"
        ),
        BlockchainNetwork.POLYGON to listOf(
            "https://polygon-bor-rpc.publicnode.com",
            "https://rpc.ankr.com/polygon",
            "https://polygon-rpc.com",
            "https://1rpc.io/matic"
        ),
        BlockchainNetwork.ARBITRUM to listOf(
            "https://arbitrum-one-rpc.publicnode.com",
            "https://rpc.ankr.com/arbitrum",
            "https://arb1.arbitrum.io/rpc",
            "https://1rpc.io/arb"
        ),
        BlockchainNetwork.OPTIMISM to listOf(
            "https://optimism-rpc.publicnode.com",
            "https://rpc.ankr.com/optimism",
            "https://mainnet.optimism.io",
            "https://1rpc.io/op"
        ),
        BlockchainNetwork.AVALANCHE to listOf(
            "https://avalanche-c-chain-rpc.publicnode.com",
            "https://rpc.ankr.com/avalanche",
            "https://api.avax.network/ext/bc/C/rpc",
            "https://1rpc.io/avax/c"
        ),
        BlockchainNetwork.BASE to listOf(
            "https://base-rpc.publicnode.com",
            "https://mainnet.base.org",
            "https://rpc.ankr.com/base",
            "https://1rpc.io/base"
        ),
        BlockchainNetwork.NEAR to listOf(
            "https://rpc.mainnet.near.org",
            "https://near.lava.build",
            "https://rpc.ankr.com/near"
        )
    )

    // Track which endpoint is currently working per network
    private val activeRpcIndex = mutableMapOf<BlockchainNetwork, Int>()
    
    /**
     * Initialize with Android context and load existing wallet if any
     */
    actual fun initialize(context: Any?) {
        appContext = (context as? Context)?.applicationContext
            ?: try { androidAppContext } catch (_: Exception) { null }

        appContext?.let { SecureStorage.initialize(it) }

        // Register a listener so that when the wallet locks, we wipe keys from RAM
        WalletLockManager.addOnLockListener {
            ChainKeyDeriver.clear()
            InvoiceHmac.clearKey()
        }

        if (currentMnemonicChars == null) {
            loadWalletFromStorage()
        }
    }
    
    /**
     * Create a new wallet with 12-word mnemonic (BIP39)
     */
    actual fun createWallet(): WalletCreationResult {
        return try {
            // Auto-initialize context if needed
            if (appContext == null) {
                appContext = androidAppContext
            }
            
            val mnemonicString = generateMnemonicString()
            
            currentMnemonicChars = mnemonicString.toCharArray()
            InvoiceHmac.cacheKey(mnemonicString)

            deriveCredentials(mnemonicString)
            initializeWeb3j(BlockchainNetwork.ETHEREUM)

            val accounts = createDefaultAccounts()
            walletAccounts.clear()
            walletAccounts.addAll(accounts)

            saveWalletToStorage()

            WalletCreationResult(mnemonicString, accounts, true)
        } catch (e: Exception) {
            WalletCreationResult("", emptyList(), false, e.message)
        }
    }

    actual fun generateNewMnemonic(): String {
        // IMPORTANT: do NOT set currentMnemonicChars, do NOT derive credentials, do NOT save.
        return generateMnemonicString()
    }

    private fun generateMnemonicString(): String {
        // Generate 12-word mnemonic using kotlin-bip39
        val entropy = ByteArray(16) // 128 bits for 12 words
        SecureRandom().nextBytes(entropy)
        val mnemonicCode = Mnemonics.MnemonicCode(entropy)
        val words = mnemonicCode.words.map { String(it) }
        return words.joinToString(" ")
    }
    
    /**
     * Recover wallet from mnemonic phrase
     */
    actual fun recoverWallet(mnemonic: String): WalletCreationResult {
        return try {
            // Auto-initialize context if needed
            if (appContext == null) {
                appContext = androidAppContext
            }
            
            // Validate mnemonic
            val words = mnemonic.trim().lowercase().split("\\s+".toRegex())
            if (words.size != 12 && words.size != 24) {
                return WalletCreationResult("", emptyList(), false, "Invalid mnemonic: must be 12 or 24 words")
            }
            
            // Validate using kotlin-bip39
            try {
                Mnemonics.MnemonicCode(mnemonic)
            } catch (e: Exception) {
                return WalletCreationResult("", emptyList(), false, "Invalid mnemonic phrase")
            }
            
            currentMnemonicChars = mnemonic.toCharArray()
            InvoiceHmac.cacheKey(mnemonic)

            deriveCredentials(mnemonic)
            initializeWeb3j(BlockchainNetwork.ETHEREUM)

            val accounts = createDefaultAccounts()
            walletAccounts.clear()
            walletAccounts.addAll(accounts)

            saveWalletToStorage()

            WalletCreationResult(mnemonic, accounts, true)
        } catch (e: Exception) {
            WalletCreationResult("", emptyList(), false, "Invalid recovery phrase: ${e.message}")
        }
    }
    
    /**
     * Derive credentials for all chains from mnemonic via ChainKeyDeriver
     */
    private fun deriveCredentials(mnemonic: String) {
        ChainKeyDeriver.initialize(mnemonic)
        credentials = ChainKeyDeriver.getEvmCredentials()
    }
    
    /**
     * Initialize Web3j for a specific network
     */
    private fun initializeWeb3j(network: BlockchainNetwork) {
        val endpoints = rpcEndpoints[network] ?: rpcEndpoints[BlockchainNetwork.ETHEREUM]!!
        val startIdx = activeRpcIndex[network] ?: 0
        val rpcUrl = endpoints[startIdx.coerceIn(endpoints.indices)]
        web3j = Web3j.build(HttpService(rpcUrl))
    }

    /**
     * Try the next fallback RPC for this network and re-init Web3j.
     * Returns true if there is another endpoint to try.
     */
    private fun rotateRpc(network: BlockchainNetwork): Boolean {
        val endpoints = rpcEndpoints[network] ?: return false
        val current = activeRpcIndex[network] ?: 0
        val next = current + 1
        if (next >= endpoints.size) {
            activeRpcIndex[network] = 0
            return false
        }
        activeRpcIndex[network] = next
        initializeWeb3j(network)
        return true
    }
    
    /**
     * Validate mnemonic phrase
     */
    actual fun validateMnemonic(mnemonic: String): Boolean {
        return try {
            val words = mnemonic.trim().lowercase().split("\\s+".toRegex())
            if (words.size != 12 && words.size != 24) return false
            Mnemonics.MnemonicCode(mnemonic)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create default accounts for ALL supported networks.
     * Each chain gets the correct address derived from the single mnemonic.
     */
    private fun createDefaultAccounts(): List<WalletAccount> {
        return BlockchainNetwork.ALL_SUPPORTED.mapNotNull { network ->
            try {
                val address = ChainKeyDeriver.getAddress(network)
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
            } catch (e: Exception) {
                SecureLog.w("PlatformWallet", "Skipping ${network.displayName}")
                null
            }
        }
    }
    
    /**
     * Get wallet address for any supported network
     */
    actual fun getAddress(network: BlockchainNetwork): String {
        return if (ChainKeyDeriver.isInitialized()) {
            ChainKeyDeriver.getAddress(network)
        } else {
            ""
        }
    }
    
    /**
     * Fetch balance from blockchain — supports EVM and non-EVM chains
     */
    actual suspend fun fetchBalance(account: WalletAccount): String = withContext(Dispatchers.IO) {
        try {
            if (account.network.isEvm) {
                fetchEvmBalanceWithFallback(account)
            } else {
                fetchNonEvmBalance(account)
            }
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Balance fetch failed for ${account.network}", null)
            "0"
        }
    }

    private fun fetchEvmBalanceWithFallback(account: WalletAccount): String {
        val endpoints = rpcEndpoints[account.network] ?: return "0"
        val json = Json { ignoreUnknownKeys = true }

        // Thread-safe: use raw RPC instead of shared Web3j instance
        for (endpoint in endpoints) {
            try {
                val payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "eth_getBalance")
                    put("params", buildJsonArray {
                        add(account.address)
                        add("latest")
                    })
                }.toString()

                val response = httpPost(endpoint, payload) ?: continue
                val resultHex = json.parseToJsonElement(response).jsonObject["result"]
                    ?.jsonPrimitive?.contentOrNull ?: continue
                val stripped = resultHex.removePrefix("0x")
                if (stripped.isBlank() || stripped.all { it == '0' }) return "0"

                val balanceWei = BigInteger(stripped, 16)
                val balanceEth = Convert.fromWei(balanceWei.toBigDecimal(), Convert.Unit.ETHER)
                return formatBalance(balanceEth)
            } catch (_: Exception) { }
        }
        return "0"
    }

    private fun fetchNonEvmBalance(account: WalletAccount): String {
        val address = account.address
        if (address.isEmpty()) return "0"
        return when (account.network) {
            BlockchainNetwork.BITCOIN -> fetchUtxoBalance("https://mempool.space/api/address/$address", 8)
            BlockchainNetwork.LITECOIN -> fetchUtxoBalance("https://litecoinspace.org/api/address/$address", 8)
            BlockchainNetwork.DOGECOIN -> fetchDogecoinBalance(address)
            BlockchainNetwork.TRON -> fetchTronBalance(address)
            BlockchainNetwork.SOLANA -> fetchSolanaBalance(address)
            BlockchainNetwork.RIPPLE -> fetchXrpBalance(address)
            BlockchainNetwork.NEAR -> fetchNearBalance(address)
            else -> "0"
        }
    }

    private fun fetchUtxoBalance(url: String, decimals: Int): String {
        val response = httpGet(url) ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val chainStats = obj["chain_stats"]?.jsonObject ?: return "0"
        val funded = chainStats["funded_txo_sum"]?.jsonPrimitive?.longOrNull ?: 0L
        val spent = chainStats["spent_txo_sum"]?.jsonPrimitive?.longOrNull ?: 0L
        val satoshis = funded - spent
        if (satoshis <= 0) return "0"
        val balance = java.math.BigDecimal(satoshis).divide(java.math.BigDecimal.TEN.pow(decimals), decimals, java.math.RoundingMode.HALF_UP)
        return formatBalance(balance)
    }

    private fun fetchDogecoinBalance(address: String): String {
        val response = httpGet("https://dogechain.info/api/v1/address/balance/$address") ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val balance = obj["balance"]?.jsonPrimitive?.content ?: return "0"
        return formatBalance(java.math.BigDecimal(balance))
    }

    private fun fetchTronBalance(address: String): String {
        val response = httpGet("https://api.trongrid.io/v1/accounts/$address") ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val dataArr = obj["data"]?.jsonArray ?: return "0"
        if (dataArr.isEmpty()) return "0"
        val balance = dataArr[0].jsonObject["balance"]?.jsonPrimitive?.longOrNull ?: 0L
        if (balance <= 0) return "0"
        val trx = java.math.BigDecimal(balance).divide(java.math.BigDecimal("1000000"), 6, java.math.RoundingMode.HALF_UP)
        return formatBalance(trx)
    }

    private fun fetchSolanaBalance(address: String): String {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getBalance")
            put("params", buildJsonArray { add(address) })
        }.toString()
        val response = httpPost("https://api.mainnet-beta.solana.com", payload) ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val lamports = obj["result"]?.jsonObject?.get("value")?.jsonPrimitive?.longOrNull ?: 0L
        if (lamports <= 0) return "0"
        val sol = java.math.BigDecimal(lamports).divide(java.math.BigDecimal("1000000000"), 9, java.math.RoundingMode.HALF_UP)
        return formatBalance(sol)
    }

    private fun fetchNearBalance(address: String): String {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "query")
            put("params", buildJsonObject {
                put("request_type", "view_account")
                put("finality", "final")
                put("account_id", address)
            })
        }.toString()
        val response = httpPost("https://rpc.mainnet.near.org", payload) ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val result = obj["result"]?.jsonObject ?: return "0"
        val amountStr = result["amount"]?.jsonPrimitive?.content ?: return "0"
        val yoctoNear = try { java.math.BigDecimal(amountStr) } catch (_: Exception) { return "0" }
        if (yoctoNear.compareTo(java.math.BigDecimal.ZERO) <= 0) return "0"
        val near = yoctoNear.divide(java.math.BigDecimal.TEN.pow(24), 8, java.math.RoundingMode.HALF_UP)
        return formatBalance(near)
    }

    private fun fetchXrpBalance(address: String): String {
        val payload = buildJsonObject {
            put("method", "account_info")
            put("params", buildJsonArray {
                add(buildJsonObject {
                    put("account", address)
                    put("ledger_index", "validated")
                })
            })
        }.toString()
        val response = httpPost("https://xrplcluster.com", payload) ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val result = obj["result"]?.jsonObject ?: return "0"
        val accountData = result["account_data"]?.jsonObject ?: return "0"
        val drops = accountData["Balance"]?.jsonPrimitive?.content ?: return "0"
        val xrp = java.math.BigDecimal(drops).divide(java.math.BigDecimal("1000000"), 6, java.math.RoundingMode.HALF_UP)
        return formatBalance(xrp)
    }

    private fun formatBalance(value: java.math.BigDecimal): String {
        return when {
            value.compareTo(java.math.BigDecimal.ZERO) == 0 -> "0"
            value < java.math.BigDecimal("0.000001") -> value.setScale(18, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            value < java.math.BigDecimal("0.0001") -> value.setScale(10, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            value < java.math.BigDecimal("0.01") -> value.setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            value < java.math.BigDecimal("1") -> value.setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            else -> value.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }

    private fun httpGet(url: String): String? = SecureHttp.get(url, emptyMap())

    private fun httpPost(url: String, body: String): String? = SecureHttp.postJson(url, body)
    
    /**
     * Fetch token balance — routes to EVM / Tron / Solana implementation
     */
    actual suspend fun fetchTokenBalance(token: WalletToken, walletAddress: String): String = withContext(Dispatchers.IO) {
        try {
            when (token.network) {
                BlockchainNetwork.TRON -> fetchTrc20TokenBalance(token, walletAddress)
                BlockchainNetwork.SOLANA -> fetchSplTokenBalance(token, walletAddress)
                BlockchainNetwork.NEAR -> fetchNep141TokenBalance(token, walletAddress)
                else -> if (token.network.isEvm) fetchErc20TokenBalance(token, walletAddress) else "0"
            }
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Token balance fetch failed for ${token.network}", null)
            "0"
        }
    }

    private fun fetchErc20TokenBalance(token: WalletToken, walletAddress: String): String {
        val endpoints = rpcEndpoints[token.network] ?: return "0"
        activeRpcIndex.putIfAbsent(token.network, 0)
        for (i in endpoints.indices) {
            try {
                initializeWeb3j(token.network)
                val web3 = web3j ?: return "0"
                val cleanAddress = walletAddress.lowercase().removePrefix("0x")
                val data = "0x70a08231000000000000000000000000$cleanAddress"
                val transaction = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    walletAddress, token.contractAddress, data
                )
                val response = web3.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
                if (response.hasError()) return "0"
                val balanceHex = response.value
                if (balanceHex == null || balanceHex == "0x" || balanceHex == "0x0" ||
                    balanceHex == "0x0000000000000000000000000000000000000000000000000000000000000000") return "0"
                val balanceWei = try { BigInteger(balanceHex.removePrefix("0x"), 16) } catch (_: Exception) { return "0" }
                if (balanceWei == BigInteger.ZERO) return "0"
                val divisor = BigDecimal.TEN.pow(token.decimals)
                val balance = balanceWei.toBigDecimal().divide(divisor, token.decimals, BigDecimal.ROUND_HALF_UP)
                return formatBalance(balance)
            } catch (e: Exception) {
                if (!rotateRpc(token.network)) throw e
            }
        }
        return "0"
    }

    /**
     * Fetch a single TRC-20 token balance via TronGrid account endpoint.
     */
    private fun fetchTrc20TokenBalance(token: WalletToken, walletAddress: String): String {
        val response = httpGet("https://api.trongrid.io/v1/accounts/$walletAddress") ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val dataArr = obj["data"]?.jsonArray
        if (dataArr == null || dataArr.isEmpty()) return "0"
        val trc20List = dataArr[0].jsonObject["trc20"]?.jsonArray ?: return "0"
        for (entry in trc20List) {
            val entryObj = entry.jsonObject
            val rawBalance = entryObj[token.contractAddress]?.jsonPrimitive?.content
            if (rawBalance != null) {
                val balanceBig = java.math.BigDecimal(rawBalance)
                    .divide(java.math.BigDecimal.TEN.pow(token.decimals), token.decimals, java.math.RoundingMode.HALF_UP)
                return formatBalance(balanceBig)
            }
        }
        return "0"
    }

    /**
     * Fetch a single SPL token balance via Solana RPC.
     */
    private fun fetchSplTokenBalance(token: WalletToken, walletAddress: String): String {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getTokenAccountsByOwner")
            put("params", buildJsonArray {
                add(walletAddress)
                add(buildJsonObject { put("mint", token.contractAddress) })
                add(buildJsonObject { put("encoding", "jsonParsed") })
            })
        }.toString()
        val response = httpPost("https://api.mainnet-beta.solana.com", payload) ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val value = obj["result"]?.jsonObject?.get("value")?.jsonArray ?: return "0"
        if (value.isEmpty()) return "0"
        val parsed = value[0].jsonObject["account"]?.jsonObject
            ?.get("data")?.jsonObject?.get("parsed")?.jsonObject
            ?.get("info")?.jsonObject?.get("tokenAmount")?.jsonObject ?: return "0"
        val uiAmount = parsed["uiAmountString"]?.jsonPrimitive?.content ?: return "0"
        return formatBalance(java.math.BigDecimal(uiAmount))
    }
    
    /**
     * Fetch all token balances for a network.
     * Tron and Solana use a single batch API call; EVM iterates per token.
     */
    actual suspend fun fetchAllTokenBalances(network: BlockchainNetwork, walletAddress: String): List<WalletToken> = withContext(Dispatchers.IO) {
        val knownTokens = NetworkTokenPreferences.getEnabledTokensForNetwork(network)
        if (knownTokens.isEmpty()) return@withContext emptyList()

        val balanceMap: Map<String, String> = when (network) {
            BlockchainNetwork.TRON -> fetchAllTrc20Balances(walletAddress, knownTokens)
            BlockchainNetwork.SOLANA -> fetchAllSplBalances(walletAddress, knownTokens)
            BlockchainNetwork.NEAR -> fetchAllNep141Balances(walletAddress, knownTokens)
            else -> fetchAllEvmBalances(walletAddress, knownTokens)
        }

        // Only return tokens that have a balance — zero-balance tokens are
        // still available via "Manage Tokens" but don't clutter the main view
        // or slow down state updates.
        knownTokens.mapNotNull { token ->
            val bal = balanceMap[token.contractAddress]
            if (bal != null && bal != "0") token.copy(balance = bal) else null
        }
    }

    private fun fetchAllEvmBalances(walletAddress: String, tokens: List<WalletToken>): Map<String, String> {
        if (tokens.isEmpty()) return emptyMap()
        val network = tokens.first().network
        val endpoints = rpcEndpoints[network] ?: return emptyMap()
        val cleanAddr = walletAddress.lowercase().removePrefix("0x").padStart(64, '0')
        val calldata = "0x70a08231$cleanAddr"
        val json = Json { ignoreUnknownKeys = true }

        val redactedAddr = walletAddress.take(6) + "…" + walletAddress.takeLast(4)

        for ((epIdx, endpoint) in endpoints.withIndex()) {
            // Attempt 1: JSON-RPC batch
            try {
                val batchResult = fetchEvmTokensBatch(endpoint, tokens, calldata, json)
                if (batchResult.isNotEmpty()) return batchResult
            } catch (e: Exception) {
                Log.w("WalletEVM", "  batch ep#$epIdx failed: ${e.message}")
            }

            // Attempt 2: Individual calls
            try {
                val seqResult = fetchEvmTokensOneByOne(endpoint, tokens, calldata, json)
                if (seqResult.isNotEmpty()) return seqResult
            } catch (e: Exception) {
                Log.w("WalletEVM", "  seq ep#$epIdx failed: ${e.message}")
            }
        }
        Log.w("WalletEVM", "fetchAllEvmBalances: all endpoints exhausted for $network, returning empty")
        return emptyMap()
    }

    private fun fetchEvmTokensBatch(
        rpcUrl: String,
        tokens: List<WalletToken>,
        calldata: String,
        json: Json
    ): Map<String, String> {
        val batchPayload = buildJsonArray {
            for ((idx, token) in tokens.withIndex()) {
                add(buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", idx)
                    put("method", "eth_call")
                    put("params", buildJsonArray {
                        add(buildJsonObject {
                            put("to", token.contractAddress)
                            put("data", calldata)
                        })
                        add("latest")
                    })
                })
            }
        }.toString()

        val response = httpPost(rpcUrl, batchPayload) ?: return emptyMap()
        val parsed = json.parseToJsonElement(response)
        if (parsed !is JsonArray) return emptyMap()

        val balanceMap = mutableMapOf<String, String>()
        for (element in parsed.jsonArray) {
            try {
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: continue
                if (id < 0 || id >= tokens.size) continue
                val balHex = obj["result"]?.jsonPrimitive?.contentOrNull ?: continue
                val stripped = balHex.removePrefix("0x")
                if (stripped.isBlank() || stripped.all { it == '0' }) continue

                val balWei = try { BigInteger(stripped.trimStart('0').ifEmpty { "0" }, 16) } catch (_: Exception) { BigInteger.ZERO }
                if (balWei > BigInteger.ZERO) {
                    val token = tokens[id]
                    val divisor = BigDecimal.TEN.pow(token.decimals)
                    val balance = balWei.toBigDecimal().divide(divisor, token.decimals, BigDecimal.ROUND_HALF_UP)
                    balanceMap[token.contractAddress] = formatBalance(balance)
                }
            } catch (_: Exception) { }
        }
        return balanceMap
    }

    private fun fetchEvmTokensOneByOne(
        rpcUrl: String,
        tokens: List<WalletToken>,
        calldata: String,
        json: Json
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (token in tokens) {
            try {
                val payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "eth_call")
                    put("params", buildJsonArray {
                        add(buildJsonObject {
                            put("to", token.contractAddress)
                            put("data", calldata)
                        })
                        add("latest")
                    })
                }.toString()

                val response = httpPost(rpcUrl, payload) ?: continue
                val balHex = json.parseToJsonElement(response).jsonObject["result"]
                    ?.jsonPrimitive?.contentOrNull ?: continue
                val stripped = balHex.removePrefix("0x")
                if (stripped.isBlank() || stripped.all { it == '0' }) continue

                val balWei = try { BigInteger(stripped.trimStart('0').ifEmpty { "0" }, 16) } catch (_: Exception) { BigInteger.ZERO }
                if (balWei > BigInteger.ZERO) {
                    val divisor = BigDecimal.TEN.pow(token.decimals)
                    val balance = balWei.toBigDecimal().divide(divisor, token.decimals, BigDecimal.ROUND_HALF_UP)
                    result[token.contractAddress] = formatBalance(balance)
                }
            } catch (_: Exception) { }
        }
        return result
    }

    /**
     * Single TronGrid call fetches balances for every TRC-20 the account holds.
     */
    private fun fetchAllTrc20Balances(walletAddress: String, knownTokens: List<WalletToken>): Map<String, String> {
        val response = httpGet("https://api.trongrid.io/v1/accounts/$walletAddress") ?: return emptyMap()
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val dataArr = obj["data"]?.jsonArray
        if (dataArr == null || dataArr.isEmpty()) return emptyMap()
        val trc20List = dataArr[0].jsonObject["trc20"]?.jsonArray ?: return emptyMap()

        val contractToDecimals = knownTokens.associate { it.contractAddress to it.decimals }
        val result = mutableMapOf<String, String>()
        for (entry in trc20List) {
            for ((contract, rawBalance) in entry.jsonObject) {
                val decimals = contractToDecimals[contract] ?: continue
                val bal = java.math.BigDecimal(rawBalance.jsonPrimitive.content)
                    .divide(java.math.BigDecimal.TEN.pow(decimals), decimals, java.math.RoundingMode.HALF_UP)
                result[contract] = formatBalance(bal)
            }
        }
        return result
    }

    /**
     * Single Solana RPC call fetches balances for every SPL token the account holds.
     */
    private fun fetchAllSplBalances(walletAddress: String, knownTokens: List<WalletToken>): Map<String, String> {
        val tokenProgramId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getTokenAccountsByOwner")
            put("params", buildJsonArray {
                add(walletAddress)
                add(buildJsonObject { put("programId", tokenProgramId) })
                add(buildJsonObject { put("encoding", "jsonParsed") })
            })
        }.toString()
        val response = httpPost("https://api.mainnet-beta.solana.com", payload) ?: return emptyMap()
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val value = obj["result"]?.jsonObject?.get("value")?.jsonArray ?: return emptyMap()

        val knownMints = knownTokens.map { it.contractAddress }.toSet()
        val result = mutableMapOf<String, String>()
        for (item in value) {
            val info = item.jsonObject["account"]?.jsonObject
                ?.get("data")?.jsonObject?.get("parsed")?.jsonObject
                ?.get("info")?.jsonObject ?: continue
            val mint = info["mint"]?.jsonPrimitive?.content ?: continue
            if (mint !in knownMints) continue
            val uiAmount = info["tokenAmount"]?.jsonObject?.get("uiAmountString")?.jsonPrimitive?.content ?: continue
            result[mint] = formatBalance(java.math.BigDecimal(uiAmount))
        }
        return result
    }
    
    /**
     * Fetch a single NEP-141 (NEAR fungible token) balance via NEAR RPC view call.
     */
    private fun fetchNep141TokenBalance(token: WalletToken, walletAddress: String): String {
        val argsJson = """{"account_id":"$walletAddress"}"""
        val argsBase64 = Base64.encodeToString(argsJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "query")
            put("params", buildJsonObject {
                put("request_type", "call_function")
                put("finality", "final")
                put("account_id", token.contractAddress)
                put("method_name", "ft_balance_of")
                put("args_base64", argsBase64)
            })
        }.toString()
        val response = httpPost("https://rpc.mainnet.near.org", payload) ?: return "0"
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(response).jsonObject
        val resultArr = obj["result"]?.jsonObject?.get("result")?.jsonArray ?: return "0"
        val bytes = ByteArray(resultArr.size) { resultArr[it].jsonPrimitive.int.toByte() }
        val balStr = String(bytes, Charsets.UTF_8).trim('"')
        val balance = try { java.math.BigDecimal(balStr) } catch (_: Exception) { return "0" }
        if (balance.compareTo(java.math.BigDecimal.ZERO) <= 0) return "0"
        val human = balance.divide(java.math.BigDecimal.TEN.pow(token.decimals), token.decimals, java.math.RoundingMode.HALF_UP)
        return formatBalance(human)
    }

    private fun fetchAllNep141Balances(walletAddress: String, knownTokens: List<WalletToken>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (token in knownTokens) {
            try {
                val bal = fetchNep141TokenBalance(token, walletAddress)
                if (bal != "0") result[token.contractAddress] = bal
            } catch (_: Exception) { }
        }
        return result
    }

    /**
     * Send ERC20 token transaction
     */
    actual suspend fun sendTokenTransaction(
        token: WalletToken,
        fromAddress: String,
        toAddress: String,
        amount: String
    ): Result<WalletTransaction> = withContext(Dispatchers.IO) {
        try {
            initializeWeb3j(token.network)
            val web3 = web3j ?: return@withContext Result.failure(Exception("Web3j not initialized"))
            val creds = credentials ?: return@withContext Result.failure(Exception("Credentials not available"))
            
            val chainId = chainIds[token.network] ?: 1L
            
            // Get nonce
            val nonce = web3.ethGetTransactionCount(creds.address, DefaultBlockParameterName.PENDING).send().transactionCount
            
            // Get gas price
            val gasPrice = web3.ethGasPrice().send().gasPrice
            val adjustedGasPrice = gasPrice.multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100))
            
            // Gas limit for ERC20 transfer (higher than ETH transfer)
            val gasLimit = BigInteger.valueOf(100000)
            
            // Convert amount to token units
            val multiplier = BigDecimal.TEN.pow(token.decimals)
            val amountInTokenUnits = BigDecimal(amount).multiply(multiplier).toBigInteger()
            
            // ERC20 transfer function: transfer(address,uint256)
            // Function selector: 0xa9059cbb
            val toAddressPadded = toAddress.removePrefix("0x").padStart(64, '0')
            val amountHex = amountInTokenUnits.toString(16).padStart(64, '0')
            val data = "0xa9059cbb$toAddressPadded$amountHex"
            
            // Create raw transaction
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                adjustedGasPrice,
                gasLimit,
                token.contractAddress,
                BigInteger.ZERO, // No ETH value for token transfer
                data
            )
            
            // Sign and send
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, creds)
            val hexValue = org.web3j.utils.Numeric.toHexString(signedMessage)
            
            val txResponse = web3.ethSendRawTransaction(hexValue).send()
            
            if (txResponse.hasError()) {
                return@withContext Result.failure(Exception(txResponse.error.message))
            }
            
            val txHash = txResponse.transactionHash
            
            val feeWei = adjustedGasPrice.multiply(gasLimit)
            val feeEth = Convert.fromWei(feeWei.toBigDecimal(), Convert.Unit.ETHER).toPlainString()
            
            val tx = WalletTransaction(
                id = UUID.randomUUID().toString(),
                txHash = txHash,
                network = token.network,
                type = TransactionType.SEND,  // Outgoing token transfer = SEND
                status = TransactionStatus.PENDING,
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
                fee = feeEth,
                tokenSymbol = token.symbol,
                tokenContractAddress = token.contractAddress,
                timestamp = System.currentTimeMillis()
            )
            
            addTransaction(tx)
            Result.success(tx)
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Token transfer failed on ${token.network}", null)
            Result.failure(Exception("Failed to send token: ${e.message}"))
        }
    }
    
    /**
     * Send real transaction to blockchain
     */
    // Chain IDs for each network
    private val chainIds = mapOf(
        BlockchainNetwork.ETHEREUM to 1L,
        BlockchainNetwork.BINANCE_SMART_CHAIN to 56L,
        BlockchainNetwork.POLYGON to 137L,
        BlockchainNetwork.ARBITRUM to 42161L,
        BlockchainNetwork.OPTIMISM to 10L,
        BlockchainNetwork.AVALANCHE to 43114L,
        BlockchainNetwork.BASE to 8453L
    )
    
    actual suspend fun sendTransaction(request: SendTransactionRequest): Result<WalletTransaction> = 
        withContext(Dispatchers.IO) {
            try {
                if (request.network == BlockchainNetwork.TRON) {
                    return@withContext sendTronTransaction(request)
                }
                if (request.network == BlockchainNetwork.SOLANA) {
                    return@withContext sendSolanaTransaction(request)
                }
                if (request.network == BlockchainNetwork.NEAR) {
                    return@withContext sendNearTransaction(request)
                }
                if (!request.network.isEvm) {
                    return@withContext Result.failure(
                        Exception("Send not yet supported on ${request.network.displayName}")
                    )
                }

                // EVM path
                initializeWeb3j(request.network)
                
                val web3 = web3j ?: return@withContext Result.failure(Exception("Web3j not initialized"))
                val creds = credentials ?: return@withContext Result.failure(Exception("Credentials not available"))
                
                // Get chain ID
                val chainId = chainIds[request.network] ?: 1L
                
                // Get nonce
                val nonce = web3.ethGetTransactionCount(
                    creds.address,
                    DefaultBlockParameterName.PENDING
                ).send().transactionCount
                
                // Get gas price (add 10% for faster confirmation)
                val gasPrice = web3.ethGasPrice().send().gasPrice
                val adjustedGasPrice = gasPrice.multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100))
                
                // Standard gas limit for ETH transfer
                val gasLimit = BigInteger.valueOf(21000)
                
                // Convert amount to Wei
                val amountWei = Convert.toWei(BigDecimal(request.amount), Convert.Unit.ETHER).toBigInteger()
                
                // Create raw transaction
                val rawTransaction = RawTransaction.createEtherTransaction(
                    nonce,
                    adjustedGasPrice,
                    gasLimit,
                    request.toAddress,
                    amountWei
                )
                
                // Sign transaction with chain ID (EIP-155)
                val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, creds)
                val hexValue = org.web3j.utils.Numeric.toHexString(signedMessage)
                
                // Broadcast transaction - this returns immediately!
                val txResponse = web3.ethSendRawTransaction(hexValue).send()
                
                if (txResponse.hasError()) {
                    SecureLog.e("PlatformWallet", "Broadcast failed on ${request.network}", null)
                    return@withContext Result.failure(Exception(txResponse.error.message))
                }
                
                val txHash = txResponse.transactionHash
                
                // Calculate estimated fee
                val feeWei = adjustedGasPrice.multiply(gasLimit)
                val feeEth = Convert.fromWei(feeWei.toBigDecimal(), Convert.Unit.ETHER).toPlainString()
                
                val tx = WalletTransaction(
                    id = UUID.randomUUID().toString(),
                    txHash = txHash,
                    network = request.network,
                    type = TransactionType.SEND,
                    status = TransactionStatus.PENDING, // Pending until confirmed
                    fromAddress = request.fromAddress,
                    toAddress = request.toAddress,
                    amount = request.amount,
                    fee = feeEth,
                    timestamp = System.currentTimeMillis(),
                    memo = request.memo
                )
                
                // Save transaction to persistent storage
                addTransaction(tx)
                
                Result.success(tx)
            } catch (e: Exception) {
                SecureLog.e("PlatformWallet", "Send transaction failed on ${request.network}", null)
                Result.failure(Exception("Failed to send: ${e.message}"))
            }
        }
    
    /**
     * Send TRX on the Tron network via TronGrid HTTP API.
     * Flow: createtransaction → sign locally (secp256k1) → broadcasttransaction
     */
    private fun sendTronTransaction(request: SendTransactionRequest): Result<WalletTransaction> {
        val privKeyBigInt = ChainKeyDeriver.getPrivateKeyBigInt(BlockchainNetwork.TRON)
            ?: return Result.failure(Exception("Tron private key not available"))

        val amountSun = try {
            java.math.BigDecimal(request.amount)
                .multiply(java.math.BigDecimal("1000000"))
                .toBigInteger().toString()
        } catch (e: Exception) {
            return Result.failure(Exception("Invalid amount: ${request.amount}"))
        }

        val createBody = """{"owner_address":"${request.fromAddress}","to_address":"${request.toAddress}","amount":$amountSun,"visible":true}"""

        val createResponse = httpPost("https://api.trongrid.io/wallet/createtransaction", createBody)
            ?: return Result.failure(Exception("TronGrid createtransaction failed — network error"))

        val json = Json { ignoreUnknownKeys = true }
        val txObj = try {
            json.parseToJsonElement(createResponse).jsonObject
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to parse TronGrid response"))
        }

        if (txObj.containsKey("Error") || txObj.containsKey("error")) {
            val errMsg = txObj["Error"]?.jsonPrimitive?.contentOrNull
                ?: txObj["error"]?.jsonPrimitive?.contentOrNull
                ?: txObj["message"]?.jsonPrimitive?.contentOrNull
                ?: createResponse
            return Result.failure(Exception("Tron error: $errMsg"))
        }

        val rawDataHex = txObj["raw_data_hex"]?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(Exception("No raw_data_hex in Tron response"))
        val txID = txObj["txID"]?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(Exception("No txID in Tron response"))


        val txHashBytes = org.web3j.utils.Numeric.hexStringToByteArray(txID)
        val ecKeyPair = ECKeyPair.create(privKeyBigInt)
        // Web3j Sign.signMessage with prefixHash=false signs raw bytes without
        // Ethereum's "\x19Ethereum Signed Message" prefix — required for Tron.
        // If this library changes the behavior of false, Tron signing will break.
        val sigData = Sign.signMessage(txHashBytes, ecKeyPair, false)

        val sigBytes = ByteArray(65)
        System.arraycopy(sigData.r, 0, sigBytes, 0, 32)
        System.arraycopy(sigData.s, 0, sigBytes, 32, 32)
        sigBytes[64] = (sigData.v[0] - 27).toByte()
        val sigHex = org.web3j.utils.Numeric.toHexStringNoPrefix(sigBytes)

        val broadcastBody = """{"raw_data":${txObj["raw_data"]},"raw_data_hex":"$rawDataHex","txID":"$txID","signature":["$sigHex"],"visible":true}"""


        val broadcastResponse = httpPost("https://api.trongrid.io/wallet/broadcasttransaction", broadcastBody)
            ?: return Result.failure(Exception("TronGrid broadcast failed — network error"))

        val broadcastObj = try {
            json.parseToJsonElement(broadcastResponse).jsonObject
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to parse broadcast response"))
        }

        val success = broadcastObj["result"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            val code = broadcastObj["code"]?.jsonPrimitive?.contentOrNull ?: ""
            val msg = broadcastObj["message"]?.jsonPrimitive?.contentOrNull ?: ""
            val decodedMsg = if (msg.isNotBlank()) try {
                String(org.web3j.utils.Numeric.hexStringToByteArray(msg), Charsets.UTF_8)
            } catch (_: Exception) { msg } else ""
            SecureLog.e("TronSend", "Broadcast failed: code=$code", null)
            return Result.failure(Exception("Tron broadcast failed: $code ${decodedMsg}".trim()))
        }


        val tx = WalletTransaction(
            id = java.util.UUID.randomUUID().toString(),
            txHash = txID,
            network = BlockchainNetwork.TRON,
            type = TransactionType.SEND,
            status = TransactionStatus.PENDING,
            fromAddress = request.fromAddress,
            toAddress = request.toAddress,
            amount = request.amount,
            fee = "0",
            timestamp = System.currentTimeMillis(),
            memo = request.memo
        )
        addTransaction(tx)
        return Result.success(tx)
    }

    /**
     * Send SOL on Solana via JSON-RPC.
     * Builds a SystemProgram.transfer instruction, signs with Ed25519, broadcasts.
     */
    private fun sendSolanaTransaction(request: SendTransactionRequest): Result<WalletTransaction> {
        val SOLANA_RPC = "https://api.mainnet-beta.solana.com"
        val json = Json { ignoreUnknownKeys = true }

        val (privateKeyBytes, publicKeyBytes) = ChainKeyDeriver.getSolanaKeypair()

        val lamports = try {
            java.math.BigDecimal(request.amount)
                .multiply(java.math.BigDecimal("1000000000"))
                .toBigIntegerExact().toLong()
        } catch (e: Exception) {
            return Result.failure(Exception("Invalid SOL amount: ${request.amount}"))
        }

        // 1. Get recent blockhash
        val bhPayload = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""
        val bhResponse = httpPost(SOLANA_RPC, bhPayload)
            ?: return Result.failure(Exception("Failed to get Solana blockhash — network error"))
        val bhObj = try { json.parseToJsonElement(bhResponse).jsonObject } catch (_: Exception) {
            return Result.failure(Exception("Failed to parse blockhash response"))
        }
        val blockhash = bhObj["result"]?.jsonObject?.get("value")?.jsonObject?.get("blockhash")?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(Exception("No blockhash in Solana response"))

        // 2. Build the transaction message
        val fromPubKey = base58Decode(request.fromAddress)
            ?: return Result.failure(Exception("Invalid from address"))
        val toPubKey = base58Decode(request.toAddress)
            ?: return Result.failure(Exception("Invalid to address"))
        val blockhashBytes = base58Decode(blockhash)
            ?: return Result.failure(Exception("Invalid blockhash"))
        val systemProgramId = ByteArray(32) // all zeros = System Program

        // Compact message format (legacy, v0):
        // [num_signatures(1)] [signature(64)] [message...]
        // Message: [header(3)] [num_accounts(compact)] [accounts(32 each)]
        //          [recent_blockhash(32)] [num_instructions(compact)]
        //          [instruction...]
        // System.Transfer instruction: programIdIndex(1), num_account_indices(compact),
        //    account_indices(1 each), data_len(compact), data(4 + 8 bytes)

        val messageBytes = buildSolanaTransferMessage(
            fromPubKey = fromPubKey,
            toPubKey = toPubKey,
            lamports = lamports,
            recentBlockhash = blockhashBytes,
            systemProgramId = systemProgramId
        )

        // 3. Sign with Ed25519
        val edPrivKey = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
        signer.init(true, edPrivKey)
        signer.update(messageBytes, 0, messageBytes.size)
        val signature = signer.generateSignature()

        // 4. Assemble full transaction: [num_sigs=1][sig(64)][message]
        val txBytes = ByteArray(1 + 64 + messageBytes.size)
        txBytes[0] = 1 // one signature
        System.arraycopy(signature, 0, txBytes, 1, 64)
        System.arraycopy(messageBytes, 0, txBytes, 65, messageBytes.size)

        val txBase64 = Base64.encodeToString(txBytes, Base64.NO_WRAP)

        // 5. Send
        val sendPayload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "sendTransaction")
            put("params", buildJsonArray {
                add(txBase64)
                add(buildJsonObject {
                    put("encoding", "base64")
                    put("preflightCommitment", "finalized")
                })
            })
        }.toString()
        val sendResponse = httpPost(SOLANA_RPC, sendPayload)
            ?: return Result.failure(Exception("Solana RPC sendTransaction failed — network error"))

        val sendObj = try { json.parseToJsonElement(sendResponse).jsonObject } catch (_: Exception) {
            return Result.failure(Exception("Failed to parse sendTransaction response"))
        }

        if (sendObj.containsKey("error")) {
            val errMsg = sendObj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: sendResponse
            SecureLog.e("SolanaSend", "RPC error", null)
            return Result.failure(Exception("Solana error: $errMsg"))
        }

        val txHash = sendObj["result"]?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(Exception("No transaction signature in response"))


        val tx = WalletTransaction(
            id = UUID.randomUUID().toString(),
            txHash = txHash,
            network = BlockchainNetwork.SOLANA,
            type = TransactionType.SEND,
            status = TransactionStatus.PENDING,
            fromAddress = request.fromAddress,
            toAddress = request.toAddress,
            amount = request.amount,
            fee = "0.000005",
            timestamp = System.currentTimeMillis(),
            memo = request.memo
        )
        addTransaction(tx)
        return Result.success(tx)
    }

    /**
     * Send NEAR native transfer via RPC.
     * Constructs a Transfer action, signs with Ed25519, serialises with Borsh-like encoding,
     * and broadcasts via NEAR JSON-RPC sendTransaction.
     */
    private fun sendNearTransaction(request: SendTransactionRequest): Result<WalletTransaction> {
        val NEAR_RPC = "https://rpc.mainnet.near.org"
        val json = Json { ignoreUnknownKeys = true }

        val (privateKeyBytes, publicKeyBytes) = ChainKeyDeriver.getNearKeypair()
        if (privateKeyBytes.isEmpty()) return Result.failure(Exception("NEAR private key not available"))

        val senderAddress = org.web3j.utils.Numeric.toHexStringNoPrefix(publicKeyBytes)
        val receiverAddress = request.toAddress

        val yoctoNear = try {
            java.math.BigDecimal(request.amount)
                .multiply(java.math.BigDecimal.TEN.pow(24))
                .toBigInteger()
        } catch (e: Exception) {
            return Result.failure(Exception("Invalid amount: ${e.message}"))
        }

        // Get access key (nonce + block hash)
        val accessKeyPayload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "query")
            put("params", buildJsonObject {
                put("request_type", "view_access_key")
                put("finality", "final")
                put("account_id", senderAddress)
                put("public_key", "ed25519:${Base58Encoder.encode(publicKeyBytes)}")
            })
        }.toString()

        val akResponse = httpPost(NEAR_RPC, accessKeyPayload)
            ?: return Result.failure(Exception("Failed to get NEAR access key — network error"))
        val akObj = try { json.parseToJsonElement(akResponse).jsonObject } catch (_: Exception) {
            return Result.failure(Exception("Failed to parse access key response"))
        }
        val akResult = akObj["result"]?.jsonObject
            ?: return Result.failure(Exception("Access key query failed: ${akObj["error"]}"))
        val nonce = (akResult["nonce"]?.jsonPrimitive?.longOrNull ?: 0L) + 1
        val blockHashStr = akResult["block_hash"]?.jsonPrimitive?.content
            ?: return Result.failure(Exception("No block hash in access key response"))

        val blockHashBytes = base58Decode(blockHashStr)
            ?: return Result.failure(Exception("Invalid block hash encoding"))

        // Build Borsh-serialised transaction
        val txBytes = buildNearTransaction(
            signerId = senderAddress,
            publicKey = publicKeyBytes,
            nonce = nonce,
            receiverId = receiverAddress,
            blockHash = blockHashBytes,
            amountYocto = yoctoNear
        )

        // Sign with Ed25519
        val edPriv = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
        signer.init(true, edPriv)

        val txHash = MessageDigest.getInstance("SHA-256").digest(txBytes)
        signer.update(txHash, 0, txHash.size)
        val signature = signer.generateSignature()

        // Assemble signed transaction: [signature(64 bytes) + transaction bytes]
        val signedTx = ByteArray(1 + 64 + txBytes.size)
        signedTx[0] = 0 // ED25519 key type
        System.arraycopy(signature, 0, signedTx, 1, 64)
        System.arraycopy(txBytes, 0, signedTx, 65, txBytes.size)

        // Actually NEAR signed tx format: borsh-serialize SignedTransaction
        // which is: [transaction_bytes, Signature{key_type(u8), data([u8;64])}]
        val fullSignedTx = ByteArray(txBytes.size + 1 + 64)
        System.arraycopy(txBytes, 0, fullSignedTx, 0, txBytes.size)
        fullSignedTx[txBytes.size] = 0 // ED25519 key type
        System.arraycopy(signature, 0, fullSignedTx, txBytes.size + 1, 64)

        val signedBase64 = Base64.encodeToString(fullSignedTx, Base64.NO_WRAP)

        val sendPayload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "broadcast_tx_commit")
            put("params", buildJsonArray { add(signedBase64) })
        }.toString()

        val sendResponse = httpPost(NEAR_RPC, sendPayload)
            ?: return Result.failure(Exception("NEAR RPC broadcast failed — network error"))
        val sendObj = try { json.parseToJsonElement(sendResponse).jsonObject } catch (_: Exception) {
            return Result.failure(Exception("Failed to parse broadcast response"))
        }

        if (sendObj.containsKey("error")) {
            val errMsg = sendObj["error"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
                ?: sendObj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: "NEAR transaction failed"
            return Result.failure(Exception(errMsg))
        }

        val resultObj = sendObj["result"]?.jsonObject
        val txHashHex = resultObj?.get("transaction")?.jsonObject?.get("hash")?.jsonPrimitive?.content
            ?: MessageDigest.getInstance("SHA-256").digest(txBytes).let {
                Base58Encoder.encode(it)
            }

        val tx = WalletTransaction(
            id = UUID.randomUUID().toString(),
            txHash = txHashHex,
            network = BlockchainNetwork.NEAR,
            type = TransactionType.SEND,
            status = TransactionStatus.PENDING,
            fromAddress = request.fromAddress,
            toAddress = request.toAddress,
            amount = request.amount,
            fee = "0.0001",
            timestamp = System.currentTimeMillis(),
            memo = request.memo
        )
        addTransaction(tx)
        return Result.success(tx)
    }

    /**
     * Build a Borsh-serialised NEAR transaction with a single Transfer action.
     */
    private fun buildNearTransaction(
        signerId: String,
        publicKey: ByteArray,
        nonce: Long,
        receiverId: String,
        blockHash: ByteArray,
        amountYocto: BigInteger
    ): ByteArray {
        val buf = java.io.ByteArrayOutputStream()

        fun writeU32(v: Int) {
            buf.write(v and 0xFF)
            buf.write((v shr 8) and 0xFF)
            buf.write((v shr 16) and 0xFF)
            buf.write((v shr 24) and 0xFF)
        }
        fun writeU64(v: Long) {
            for (i in 0..7) buf.write(((v shr (i * 8)) and 0xFF).toInt())
        }
        fun writeU128(v: BigInteger) {
            val bytes = v.toByteArray()
            val le = ByteArray(16)
            for (i in bytes.indices) {
                le[bytes.size - 1 - i] = bytes[i]
            }
            buf.write(le)
        }
        fun writeString(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            writeU32(b.size)
            buf.write(b)
        }

        // signer_id
        writeString(signerId)
        // public_key: key_type (0 = ED25519) + 32 bytes
        buf.write(0)
        buf.write(publicKey)
        // nonce
        writeU64(nonce)
        // receiver_id
        writeString(receiverId)
        // block_hash (32 bytes)
        buf.write(blockHash)
        // actions: array length (1 action)
        writeU32(1)
        // action type: Transfer = 3
        buf.write(3)
        // Transfer.deposit: u128 (little-endian)
        writeU128(amountYocto)

        return buf.toByteArray()
    }

    /**
     * Build a Solana legacy transaction message for a SystemProgram.transfer.
     * Accounts: [from(signer+writable), to(writable), systemProgram]
     */
    private fun buildSolanaTransferMessage(
        fromPubKey: ByteArray,
        toPubKey: ByteArray,
        lamports: Long,
        recentBlockhash: ByteArray,
        systemProgramId: ByteArray
    ): ByteArray {
        val buf = java.io.ByteArrayOutputStream()

        // Header: numRequiredSignatures, numReadOnlySignedAccounts, numReadOnlyUnsignedAccounts
        buf.write(1) // 1 signer (from)
        buf.write(0) // 0 read-only signed
        buf.write(1) // 1 read-only unsigned (system program)

        // Account keys: [from, to, systemProgram]
        buf.write(3) // compact-u16 for 3 accounts
        buf.write(fromPubKey)
        buf.write(toPubKey)
        buf.write(systemProgramId)

        // Recent blockhash
        buf.write(recentBlockhash)

        // Instructions: 1 instruction
        buf.write(1) // compact-u16 for 1 instruction

        // Instruction: SystemProgram.Transfer (index 2)
        buf.write(2) // programIdIndex = 2 (systemProgram)
        buf.write(2) // num account indices (compact-u16)
        buf.write(0) // from account index
        buf.write(1) // to account index

        // Data: 4 bytes (transfer instruction index = 2 as u32 LE) + 8 bytes (lamports as u64 LE)
        buf.write(12) // data length (compact-u16)
        // Transfer instruction discriminator: 2 as little-endian u32
        buf.write(2); buf.write(0); buf.write(0); buf.write(0)
        // Lamports as little-endian u64
        for (i in 0 until 8) {
            buf.write(((lamports ushr (i * 8)) and 0xFF).toInt())
        }

        return buf.toByteArray()
    }

    private fun base58Decode(input: String): ByteArray? {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var bi = BigInteger.ZERO
        for (c in input) {
            val idx = alphabet.indexOf(c)
            if (idx < 0) return null
            bi = bi.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(idx.toLong()))
        }
        val bytes = bi.toByteArray()
        val leadingZeros = input.takeWhile { it == '1' }.length
        val stripped = if (bytes.isNotEmpty() && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
        return ByteArray(leadingZeros) + stripped
    }

    /**
     * Estimate transaction fee
     */
    actual suspend fun estimateFee(network: BlockchainNetwork): FeeEstimate = withContext(Dispatchers.IO) {
        try {
            initializeWeb3j(network)
            
            val web3 = web3j
            if (web3 != null) {
                val gasPrice = web3.ethGasPrice().send().gasPrice
                val gasPriceGwei = Convert.fromWei(gasPrice.toBigDecimal(), Convert.Unit.GWEI)
                val gasLimit = 21000L
                
                val baseFee = gasPriceGwei.multiply(BigDecimal(gasLimit)).divide(BigDecimal.TEN.pow(9))
                
                FeeEstimate(
                    network = network,
                    slow = FeeOption(
                        gasPrice = gasPriceGwei.multiply(BigDecimal("0.8")).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
                        gasLimit = gasLimit,
                        estimatedFee = baseFee.multiply(BigDecimal("0.8")).setScale(8, BigDecimal.ROUND_HALF_UP).toPlainString(),
                        estimatedFeeUsd = baseFee.multiply(BigDecimal("0.8")).toDouble() * 2000,
                        estimatedTime = "~10 min"
                    ),
                    normal = FeeOption(
                        gasPrice = gasPriceGwei.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
                        gasLimit = gasLimit,
                        estimatedFee = baseFee.setScale(8, BigDecimal.ROUND_HALF_UP).toPlainString(),
                        estimatedFeeUsd = baseFee.toDouble() * 2000,
                        estimatedTime = "~3 min"
                    ),
                    fast = FeeOption(
                        gasPrice = gasPriceGwei.multiply(BigDecimal("1.5")).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
                        gasLimit = gasLimit,
                        estimatedFee = baseFee.multiply(BigDecimal("1.5")).setScale(8, BigDecimal.ROUND_HALF_UP).toPlainString(),
                        estimatedFeeUsd = baseFee.multiply(BigDecimal("1.5")).toDouble() * 2000,
                        estimatedTime = "~30 sec"
                    )
                )
            } else {
                getDefaultFeeEstimate(network)
            }
        } catch (e: Exception) {
            getDefaultFeeEstimate(network)
        }
    }
    
    private fun getDefaultFeeEstimate(network: BlockchainNetwork): FeeEstimate {
        return FeeEstimate(
            network = network,
            slow = FeeOption("20", 21000, "0.00042", 0.84, "~10 min"),
            normal = FeeOption("30", 21000, "0.00063", 1.26, "~3 min"),
            fast = FeeOption("50", 21000, "0.00105", 2.10, "~30 sec")
        )
    }
    
    /**
     * Check if wallet is initialized
     */
    actual fun isInitialized(): Boolean = currentMnemonicChars != null && credentials != null

    actual fun hasPersistedWallet(): Boolean {
        val prefs = getPrefs() ?: return false
        if (prefs.getBoolean(KEY_WALLET_INITIALIZED, false)) return true
        if (prefs.getString(KEY_WALLET_PROFILES, null) != null) return true
        return false
    }
    
    /**
     * Get current mnemonic
     */
    actual fun getMnemonic(): String? = currentMnemonicChars?.let { String(it) }
    
    /**
     * Clear all wallet data
     */
    actual fun clearWallet() {
        web3j?.shutdown()
        web3j = null
        credentials = null

        WalletSecurity.wipe(currentMnemonicChars)
        currentMnemonicChars = null

        walletAccounts.clear()
        ChainKeyDeriver.clear() // zeros all byte arrays
        clearWalletFromStorage()
    }
    
    /**
     * Validate address for any supported network
     */
    actual fun validateAddress(address: String, network: BlockchainNetwork): Boolean {
        return try {
            when {
                network.isEvm -> WalletUtils.isValidAddress(address)
                network == BlockchainNetwork.BITCOIN -> {
                    if (address.startsWith("bc1")) {
                        address.length in 42..62 && address.all { it.isLetterOrDigit() }
                    } else {
                        validateBase58Check(address) && (address.startsWith("1") || address.startsWith("3")) && address.length in 25..34
                    }
                }
                network == BlockchainNetwork.LITECOIN -> {
                    if (address.startsWith("ltc1")) {
                        address.length in 42..62 && address.all { it.isLetterOrDigit() }
                    } else {
                        validateBase58Check(address) && (address.startsWith("L") || address.startsWith("M")) && address.length in 25..34
                    }
                }
                network == BlockchainNetwork.DOGECOIN ->
                    validateBase58Check(address) && address.startsWith("D") && address.length in 25..34
                network == BlockchainNetwork.TRON ->
                    address.startsWith("T") && address.length == 34 && validateBase58Check(address)
                network == BlockchainNetwork.SOLANA -> {
                    val decoded = base58Decode(address)
                    decoded != null && decoded.size == 32
                }
                network == BlockchainNetwork.RIPPLE ->
                    address.startsWith("r") && address.length in 25..35
                network == BlockchainNetwork.CARDANO ->
                    address.startsWith("addr1") && address.length > 50
                network == BlockchainNetwork.NEAR -> {
                    // Implicit account (64-char hex of ed25519 pubkey) or named account (*.near)
                    (address.length == 64 && address.all { it in '0'..'9' || it in 'a'..'f' }) ||
                        (address.endsWith(".near") && address.length >= 5 && address.length <= 64)
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun validateBase58Check(address: String): Boolean {
        return try {
            val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
            var bi = java.math.BigInteger.ZERO
            for (c in address) {
                val idx = alphabet.indexOf(c)
                if (idx < 0) return false
                bi = bi.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(idx.toLong()))
            }
            val bytes = bi.toByteArray()
            val leadingZeros = address.takeWhile { it == '1' }.length
            val stripped = if (bytes.isNotEmpty() && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
            val full = ByteArray(leadingZeros) + stripped
            if (full.size < 5) return false
            val payload = full.copyOfRange(0, full.size - 4)
            val checksum = full.copyOfRange(full.size - 4, full.size)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest(md.digest(payload))
            hash.copyOfRange(0, 4).contentEquals(checksum)
        } catch (_: Exception) {
            false
        }
    }
    
    // Explorer API endpoints for transaction history
    private val explorerApiEndpoints = mapOf(
        BlockchainNetwork.ETHEREUM to "https://api.etherscan.io/api",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "https://api.bscscan.com/api",
        BlockchainNetwork.POLYGON to "https://api.polygonscan.com/api",
        BlockchainNetwork.ARBITRUM to "https://api.arbiscan.io/api",
        BlockchainNetwork.OPTIMISM to "https://api-optimistic.etherscan.io/api",
        BlockchainNetwork.AVALANCHE to "https://api.snowtrace.io/api",
        BlockchainNetwork.BASE to "https://api.basescan.org/api"
    )

    // Fallback explorer APIs that don't usually require keys (Etherscan-compatible)
    // These are used automatically if the primary endpoint returns NOTOK / API key required / rate limit.
    private val explorerFallbackApiEndpoints = mapOf(
        BlockchainNetwork.ETHEREUM to listOf(
            "https://eth.blockscout.com/api",
            "https://api.routescan.io/v2/network/mainnet/evm/1/etherscan/api"
        ),
        BlockchainNetwork.BINANCE_SMART_CHAIN to listOf(
            "https://api.routescan.io/v2/network/mainnet/evm/56/etherscan/api"
        ),
        BlockchainNetwork.POLYGON to listOf(
            "https://polygon.blockscout.com/api",
            "https://api.routescan.io/v2/network/mainnet/evm/137/etherscan/api"
        ),
        BlockchainNetwork.ARBITRUM to listOf(
            "https://arbitrum.blockscout.com/api",
            "https://api.routescan.io/v2/network/mainnet/evm/42161/etherscan/api"
        ),
        BlockchainNetwork.OPTIMISM to listOf(
            "https://optimism.blockscout.com/api",
            "https://api.routescan.io/v2/network/mainnet/evm/10/etherscan/api"
        ),
        BlockchainNetwork.AVALANCHE to listOf(
            "https://api.routescan.io/v2/network/mainnet/evm/43114/etherscan/api"
        ),
        BlockchainNetwork.BASE to listOf(
            "https://base.blockscout.com/api",
            "https://api.routescan.io/v2/network/mainnet/evm/8453/etherscan/api"
        )
    )

    private const val SECURE_ALIAS_EXPLORER_KEYS = "explorer_api_keys_enc"

    private fun getExplorerApiKey(network: BlockchainNetwork): String? {
        val raw = try {
            val bytes = SecureStorage.decryptFromStore(SECURE_ALIAS_EXPLORER_KEYS)
            bytes?.let { String(it, Charsets.UTF_8) }
        } catch (_: Exception) { null } ?: return null
        return raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .firstOrNull { (name, _) -> name == network.name }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }

    fun setExplorerApiKey(network: BlockchainNetwork, apiKey: String?) {
        val existing = try {
            val bytes = SecureStorage.decryptFromStore(SECURE_ALIAS_EXPLORER_KEYS)
            bytes?.let { String(it, Charsets.UTF_8) } ?: ""
        } catch (_: Exception) { "" }
        val map = mutableMapOf<String, String>()
        existing.lineSequence()
            .filter { it.contains(':') }
            .forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val k = line.substring(0, idx)
                    val v = line.substring(idx + 1)
                    if (k.isNotBlank()) map[k] = v
                }
            }
        if (apiKey.isNullOrBlank()) map.remove(network.name) else map[network.name] = apiKey.trim()
        val serialized = map.entries.joinToString("\n") { "${it.key}:${it.value}" }
        SecureStorage.encryptAndStore(SECURE_ALIAS_EXPLORER_KEYS, serialized.toByteArray(Charsets.UTF_8).clone())
    }

    private val explorerJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private data class ExplorerResult(
        val ok: Boolean,
        val message: String?,
        val resultArray: JsonArray
    )

    private fun parseExplorerResultArray(jsonString: String): ExplorerResult? {
        return try {
            val root = explorerJson.parseToJsonElement(jsonString)
            // Some providers might return raw arrays (rare). Handle it.
            if (root is JsonArray) {
                return ExplorerResult(ok = true, message = null, resultArray = root)
            }
            val obj = root.jsonObject
            val status = obj.stringOrNull("status")
            val message = obj.stringOrNull("message")
            val resultEl = obj["result"]
            val arr = resultEl?.jsonArray ?: JsonArray(emptyList())

            // Etherscan style:
            // - status=1: OK
            // - status=0 + "No transactions found": treat as OK empty
            // - status=0 + message=NOTOK: error
            val ok = when (status) {
                "1" -> true
                "0" -> {
                    val lower = (message ?: "") + " " + (obj.stringOrNull("result") ?: "")
                    lower.contains("no transactions found", ignoreCase = true)
                }
                else -> arr.isNotEmpty() // be permissive for non-standard providers
            }
            ExplorerResult(ok = ok, message = message, resultArray = if (ok) arr else JsonArray(emptyList()))
        } catch (_: Exception) {
            null
        }
    }

    private fun shouldFallbackExplorer(response: String): Boolean {
        val lower = response.lowercase()
        return lower.contains("\"message\":\"notok\"") ||
            lower.contains("api key") ||
            lower.contains("apikey") ||
            lower.contains("rate limit") ||
            lower.contains("max rate") ||
            lower.contains("too many requests") ||
            lower.contains("access denied") ||
            lower.contains("forbidden")
    }

    private fun buildExplorerUrl(
        base: String,
        action: String,
        addressLower: String,
        offset: Int,
        apiKey: String?
    ): java.net.URL {
        val keyPart = apiKey?.takeIf { it.isNotBlank() }?.let { "&apikey=$it" } ?: ""
        val url = "$base?module=account&action=$action&address=$addressLower&startblock=0&endblock=99999999&page=1&offset=$offset&sort=desc$keyPart"
        return java.net.URL(url)
    }

    private fun fetchExplorerArrayWithFallbacks(
        network: BlockchainNetwork,
        action: String,
        addressLower: String,
        offset: Int
    ): JsonArray {
        val primary = explorerApiEndpoints[network]
        val fallbacks = explorerFallbackApiEndpoints[network].orEmpty()
        val key = getExplorerApiKey(network)
        val bases = buildList {
            if (primary != null) add(primary)
            addAll(fallbacks)
        }.distinct()

        for (base in bases) {
            val url = buildExplorerUrl(base, action, addressLower, offset, key)
            val response = fetchFromUrl(url) ?: continue

            // If clearly blocked, try fallback.
            if (shouldFallbackExplorer(response) && base == primary) {
                SecureLog.w("PlatformWallet", "Explorer [$action] primary looks blocked; falling back...")
                continue
            }

            val parsed = parseExplorerResultArray(response)
            if (parsed == null) {
                SecureLog.w("PlatformWallet", "Explorer [$action] failed to parse JSON from $base")
                continue
            }
            if (parsed.ok) return parsed.resultArray

            SecureLog.w("PlatformWallet", "Explorer [$action] NOTOK on $base")
        }
        return JsonArray(emptyList())
    }
    
    // ─── TRON helpers ──────────────────────────────────────────────────
    //
    // TRON does not expose an Etherscan-compatible explorer API, so we hit
    // TronGrid's REST endpoints directly:
    //   - Native TRX transfers : /v1/accounts/{addr}/transactions
    //   - TRC-20 token transfers : /v1/accounts/{addr}/transactions/trc20
    //   - Tx confirmation status : /walletsolidity/gettransactioninfobyid

    private val TRON_BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    /**
     * Convert a Tron hex address (41-prefixed, 21 bytes) to its base58check
     * representation (T-prefixed, 34 chars). Returns the input if it is
     * already base58 / cannot be parsed.
     */
    private fun tronHexToBase58(hex: String): String {
        if (hex.isBlank()) return hex
        val cleaned = hex.removePrefix("0x")
        if (!cleaned.matches(Regex("[0-9a-fA-F]+"))) return hex
        if (cleaned.length != 42 || !cleaned.startsWith("41")) return hex
        return try {
            val bytes = ByteArray(cleaned.length / 2) {
                cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val checksum = md.digest(md.digest(bytes)).copyOfRange(0, 4)
            val full = bytes + checksum
            // Base58 encode
            var bi = BigInteger(1, full)
            val sb = StringBuilder()
            while (bi > BigInteger.ZERO) {
                val divmod = bi.divideAndRemainder(BigInteger.valueOf(58))
                bi = divmod[0]
                sb.append(TRON_BASE58_ALPHABET[divmod[1].toInt()])
            }
            // Preserve leading zero bytes as '1' chars
            for (b in full) {
                if (b.toInt() == 0) sb.append(TRON_BASE58_ALPHABET[0]) else break
            }
            sb.reverse().toString()
        } catch (_: Exception) { hex }
    }

    private suspend fun fetchTronTransactions(address: String): List<WalletTransaction> = withContext(Dispatchers.IO) {
        try {
            val fetched = mutableListOf<WalletTransaction>()
            fetched.addAll(fetchTronNativeTransactions(address))
            fetched.addAll(fetchTronTrc20Transactions(address))

            // Merge into persistent storage (same pattern as the EVM path).
            for (tx in fetched) {
                val key = tx.txHash + (tx.tokenSymbol ?: "")
                val idx = storedTransactions.indexOfFirst {
                    it.txHash + (it.tokenSymbol ?: "") == key
                }
                if (idx >= 0) {
                    // Preserve the locally-set fee on outgoing tx if explorer returns 0/blank.
                    val existing = storedTransactions[idx]
                    val mergedFee = if (tx.fee.isBlank() || tx.fee == "0") existing.fee else tx.fee
                    storedTransactions[idx] = tx.copy(fee = mergedFee)
                } else {
                    storedTransactions.add(tx)
                }
            }
            storedTransactions.sortByDescending { it.timestamp }
            saveTransactionHistory()

            storedTransactions.filter { it.network == BlockchainNetwork.TRON }
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "TRON transaction fetch failed", null)
            storedTransactions.filter { it.network == BlockchainNetwork.TRON }
        }
    }

    private fun fetchTronNativeTransactions(address: String): List<WalletTransaction> {
        val url = "https://api.trongrid.io/v1/accounts/$address/transactions" +
            "?limit=50&order_by=block_timestamp,desc"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val root = explorerJson.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            val ownerHexLower = "41" + (try {
                base58Decode(address)?.copyOfRange(1, 21)?.joinToString("") {
                    "%02x".format(it.toInt() and 0xFF)
                }
            } catch (_: Exception) { null } ?: "")

            val txs = mutableListOf<WalletTransaction>()
            for (el in data) {
                val obj = el.jsonObject
                val txId = obj["txID"]?.jsonPrimitive?.contentOrNull ?: continue
                val timestamp = obj["block_timestamp"]?.jsonPrimitive?.longOrNull ?: 0L

                val ret = obj["ret"]?.jsonArray?.firstOrNull()?.jsonObject
                val contractRet = ret?.get("contractRet")?.jsonPrimitive?.contentOrNull
                val status = when (contractRet) {
                    "SUCCESS" -> TransactionStatus.CONFIRMED
                    null, "" -> TransactionStatus.PENDING
                    else -> TransactionStatus.FAILED
                }

                val rawData = obj["raw_data"]?.jsonObject ?: continue
                val contracts = rawData["contract"]?.jsonArray ?: continue
                val contract = contracts.firstOrNull()?.jsonObject ?: continue
                val type = contract["type"]?.jsonPrimitive?.contentOrNull
                if (type != "TransferContract") continue   // skip TRC-10 / contract calls / etc.

                val value = contract["parameter"]?.jsonObject
                    ?.get("value")?.jsonObject ?: continue
                val ownerAddress = value["owner_address"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                val toAddressHex = value["to_address"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                val amountSun = value["amount"]?.jsonPrimitive?.longOrNull ?: 0L
                val amountTrx = BigDecimal(amountSun)
                    .divide(BigDecimal.TEN.pow(6), 6, BigDecimal.ROUND_HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()

                val isOutgoing = ownerAddress == ownerHexLower
                txs.add(
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        txHash = txId,
                        network = BlockchainNetwork.TRON,
                        type = if (isOutgoing) TransactionType.SEND else TransactionType.RECEIVE,
                        status = status,
                        fromAddress = tronHexToBase58(ownerAddress),
                        toAddress = tronHexToBase58(toAddressHex),
                        amount = amountTrx,
                        fee = "0",
                        timestamp = timestamp
                    )
                )
            }
            txs
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "TRON native tx parsing failed", null)
            emptyList()
        }
    }

    private fun fetchTronTrc20Transactions(address: String): List<WalletTransaction> {
        val url = "https://api.trongrid.io/v1/accounts/$address/transactions/trc20" +
            "?limit=50&order_by=block_timestamp,desc"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val root = explorerJson.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            val txs = mutableListOf<WalletTransaction>()
            for (el in data) {
                val obj = el.jsonObject
                val txId = obj["transaction_id"]?.jsonPrimitive?.contentOrNull ?: continue
                val timestamp = obj["block_timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                val from = obj["from"]?.jsonPrimitive?.contentOrNull ?: ""
                val to = obj["to"]?.jsonPrimitive?.contentOrNull ?: ""
                val rawValue = obj["value"]?.jsonPrimitive?.contentOrNull ?: "0"

                val tokenInfo = obj["token_info"]?.jsonObject
                val symbol = tokenInfo?.get("symbol")?.jsonPrimitive?.contentOrNull ?: "TOKEN"
                val name = tokenInfo?.get("name")?.jsonPrimitive?.contentOrNull ?: symbol
                val contract = tokenInfo?.get("address")?.jsonPrimitive?.contentOrNull ?: ""
                val decimals = tokenInfo?.get("decimals")?.jsonPrimitive?.intOrNull ?: 6

                val amountStr = try {
                    BigInteger(rawValue).toBigDecimal()
                        .divide(BigDecimal.TEN.pow(decimals), decimals, BigDecimal.ROUND_HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                } catch (_: Exception) { "0" }

                val isOutgoing = from.equals(address, ignoreCase = true)
                txs.add(
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        txHash = txId,
                        network = BlockchainNetwork.TRON,
                        type = if (isOutgoing) TransactionType.SEND else TransactionType.RECEIVE,
                        status = TransactionStatus.CONFIRMED,
                        fromAddress = from,
                        toAddress = to,
                        amount = amountStr,
                        fee = "0",
                        tokenSymbol = symbol,
                        tokenContractAddress = contract,
                        timestamp = timestamp,
                        memo = "Token: $name"
                    )
                )
            }
            txs
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "TRON TRC-20 tx parsing failed", null)
            emptyList()
        }
    }

    /**
     * Check the status of a Tron transaction via TronGrid's solid (irreversible)
     * info endpoint. An empty response means the tx isn't in a solid block yet,
     * which we treat as PENDING.
     */
    private fun checkTronTransactionStatus(txHash: String): TransactionStatus {
        val payload = """{"value":"$txHash"}"""
        val response = httpPost("https://api.trongrid.io/walletsolidity/gettransactioninfobyid", payload)
            ?: return TransactionStatus.PENDING
        return try {
            val obj = explorerJson.parseToJsonElement(response).jsonObject
            // Empty object = not yet in a solid block.
            if (obj.isEmpty() || obj["id"] == null) return TransactionStatus.PENDING

            // receipt.result missing on plain transfers — fall back to contractResult parsing.
            val receiptResult = obj["receipt"]?.jsonObject?.get("result")?.jsonPrimitive?.contentOrNull
            if (receiptResult != null) {
                return if (receiptResult.equals("SUCCESS", ignoreCase = true)) TransactionStatus.CONFIRMED
                else TransactionStatus.FAILED
            }
            // Plain TRX transfer: presence of blockNumber means it executed.
            val blockNumber = obj["blockNumber"]?.jsonPrimitive?.longOrNull
            if (blockNumber != null && blockNumber > 0) TransactionStatus.CONFIRMED
            else TransactionStatus.PENDING
        } catch (_: Exception) {
            TransactionStatus.PENDING
        }
    }

    /**
     * Fetch transaction history from blockchain explorer APIs
     * Uses free APIs without API keys
     */
    actual suspend fun fetchTransactions(address: String, network: BlockchainNetwork): List<WalletTransaction> = 
        withContext(Dispatchers.IO) {
            // Non-EVM networks have their own explorer APIs.
            if (network == BlockchainNetwork.TRON) {
                return@withContext fetchTronTransactions(address)
            }
            try {
                val allTransactions = mutableListOf<WalletTransaction>()
                
                // Use lowercase address for API calls (some APIs are case-sensitive)
                val addressLower = address.lowercase()
                
                // 1. Fetch normal ETH transactions (both sent AND received)
                run {
                    val normalArray = fetchExplorerArrayWithFallbacks(
                        network = network,
                        action = "txlist",
                        addressLower = addressLower,
                        offset = 100
                    )
                    val normalTxs = parseNormalOrInternalTxArray(
                        resultArray = normalArray,
                        network = network,
                        walletAddress = addressLower,
                        isInternal = false
                    )
                    allTransactions.addAll(normalTxs)
                }
                
                // 2. Fetch ERC20 token transfers (includes both sent AND received tokens)
                run {
                    val tokenArray = fetchExplorerArrayWithFallbacks(
                        network = network,
                        action = "tokentx",
                        addressLower = addressLower,
                        offset = 100
                    )
                    val tokenTxs = parseTokenTxArray(
                        resultArray = tokenArray,
                        network = network,
                        walletAddress = addressLower
                    )
                    allTransactions.addAll(tokenTxs)
                }
                
                // 3. Fetch internal transactions (contract calls that transfer ETH - includes received)
                run {
                    val internalArray = fetchExplorerArrayWithFallbacks(
                        network = network,
                        action = "txlistinternal",
                        addressLower = addressLower,
                        offset = 50
                    )
                    val internalTxs = parseNormalOrInternalTxArray(
                        resultArray = internalArray,
                        network = network,
                        walletAddress = addressLower,
                        isInternal = true
                    )
                    allTransactions.addAll(internalTxs)
                }
                
                // Remove duplicates by txHash and sort
                val fetchedTransactions = allTransactions
                    .distinctBy { it.txHash + it.tokenSymbol.orEmpty() } // Unique by hash + token
                    .sortedByDescending { it.timestamp }
                
                // Merge fetched transactions with stored ones
                // Use txHash + tokenSymbol to distinguish between native and token transfers
                for (tx in fetchedTransactions) {
                    val txKey = tx.txHash + (tx.tokenSymbol ?: "")
                    val existingIndex = storedTransactions.indexOfFirst { 
                        it.txHash + (it.tokenSymbol ?: "") == txKey 
                    }
                    if (existingIndex >= 0) {
                        // Update existing transaction (status might have changed)
                        storedTransactions[existingIndex] = tx
                    } else {
                        // Add new transaction
                        storedTransactions.add(tx)
                    }
                }
                
                // Sort by timestamp (newest first)
                storedTransactions.sortByDescending { it.timestamp }
                
                // Save merged transactions to storage
                saveTransactionHistory()
                
                // Return all stored transactions for this network
                storedTransactions.filter { it.network == network }
            } catch (e: Exception) {
                SecureLog.e("PlatformWallet", "Transaction fetch failed for ${network}", null)
                // Return stored transactions even if fetch fails
                storedTransactions.filter { it.network == network }
            }
        }
    
    /**
     * Helper to fetch from URL
     */
    private fun fetchFromUrl(url: java.net.URL): String? {
        return SecureHttp.get(url.toString(), emptyMap())
    }
    
    private fun parseTokenTxArray(
        resultArray: JsonArray,
        network: BlockchainNetwork,
        walletAddress: String
    ): List<WalletTransaction> {
        return try {
            val transactions = mutableListOf<WalletTransaction>()
            for ((index, el) in resultArray.withIndex()) {
                val obj = el.jsonObject
                val hash = obj.stringOrNull("hash") ?: continue
                val from = obj.stringOrNull("from") ?: ""
                val to = obj.stringOrNull("to") ?: ""
                val valueStr = obj.stringOrNull("value") ?: "0"
                val timestamp = obj["timeStamp"]?.jsonPrimitive?.longOrNull ?: 0L
                val tokenSymbol = obj.stringOrNull("tokenSymbol") ?: ""
                val tokenName = obj.stringOrNull("tokenName") ?: ""
                val tokenDecimal = obj["tokenDecimal"]?.jsonPrimitive?.intOrNull ?: 18
                val contractAddress = obj.stringOrNull("contractAddress") ?: ""
                val gasUsed = obj.stringOrNull("gasUsed") ?: "0"
                val gasPrice = obj.stringOrNull("gasPrice") ?: "0"

                val isOutgoing = from.equals(walletAddress, ignoreCase = true)
                val isIncoming = to.equals(walletAddress, ignoreCase = true)
                if (!isOutgoing && !isIncoming) continue

                val tokenValue = try {
                    val valueWei = BigInteger(valueStr)
                    val divisor = BigDecimal.TEN.pow(tokenDecimal)
                    valueWei.toBigDecimal().divide(divisor, tokenDecimal, BigDecimal.ROUND_HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                } catch (_: Exception) { "0" }

                val feeEth = try {
                    val gasUsedBig = BigInteger(gasUsed)
                    val gasPriceBig = BigInteger(gasPrice)
                    val feeWei = gasUsedBig.multiply(gasPriceBig)
                    Convert.fromWei(feeWei.toBigDecimal(), Convert.Unit.ETHER)
                        .setScale(8, BigDecimal.ROUND_HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                } catch (_: Exception) { "0" }

                transactions.add(
                    WalletTransaction(
                        id = UUID.randomUUID().toString(),
                        txHash = hash,
                        network = network,
                        type = if (isOutgoing) TransactionType.SEND else TransactionType.RECEIVE,
                        status = TransactionStatus.CONFIRMED,
                        fromAddress = from,
                        toAddress = to,
                        amount = tokenValue,
                        fee = feeEth,
                        tokenSymbol = tokenSymbol,
                        tokenContractAddress = contractAddress,
                        timestamp = timestamp * 1000,
                        memo = "Token: $tokenName"
                    )
                )
            }
            transactions
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Token transaction parsing failed for ${network}", null)
            emptyList()
        }
    }
    
    /**
     * Parse transactions from Etherscan-like API response
     */
    private fun parseNormalOrInternalTxArray(
        resultArray: JsonArray,
        network: BlockchainNetwork,
        walletAddress: String,
        isInternal: Boolean = false
    ): List<WalletTransaction> {
        return try {
            val transactions = mutableListOf<WalletTransaction>()

            for ((index, el) in resultArray.withIndex()) {
                val obj = el.jsonObject
                val hash = obj.stringOrNull("hash") ?: continue
                val from = obj.stringOrNull("from") ?: ""
                // some internal tx entries may have null "to"
                val to = obj.stringOrNull("to") ?: ""
                val valueWei = obj.stringOrNull("value") ?: "0"
                val timestamp = obj["timeStamp"]?.jsonPrimitive?.longOrNull ?: 0L
                val gasUsed = obj.stringOrNull("gasUsed") ?: "0"
                val gasPrice = obj.stringOrNull("gasPrice") ?: "0"
                val isError = obj.stringOrNull("isError") == "1"
                val confirmations = obj["confirmations"]?.jsonPrimitive?.longOrNull ?: 0L
                
                // Calculate values
                val valueEth = try {
                    val wei = java.math.BigInteger(valueWei)
                    Convert.fromWei(wei.toBigDecimal(), Convert.Unit.ETHER)
                        .setScale(8, BigDecimal.ROUND_HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                } catch (e: Exception) { "0" }
                
                val feeEth = try {
                    val gasUsedBig = java.math.BigInteger(gasUsed)
                    val gasPriceBig = java.math.BigInteger(gasPrice)
                    val feeWei = gasUsedBig.multiply(gasPriceBig)
                    Convert.fromWei(feeWei.toBigDecimal(), Convert.Unit.ETHER)
                        .setScale(8, BigDecimal.ROUND_HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                } catch (e: Exception) { "0" }
                
                // Determine if incoming or outgoing - check both from and to
                val isOutgoing = from.equals(walletAddress, ignoreCase = true)
                val isIncoming = to.equals(walletAddress, ignoreCase = true)
                
                val tx = WalletTransaction(
                    id = UUID.randomUUID().toString(),
                    txHash = hash,
                    network = network,
                    type = if (isOutgoing) TransactionType.SEND else TransactionType.RECEIVE,
                    status = when {
                        isError -> TransactionStatus.FAILED
                        confirmations > 0 -> TransactionStatus.CONFIRMED
                        else -> TransactionStatus.PENDING
                    },
                    fromAddress = from,
                    toAddress = to,
                    amount = valueEth,
                    fee = feeEth,
                    timestamp = timestamp * 1000, // Convert to milliseconds
                    confirmations = confirmations.toInt(),
                    memo = if (isInternal) "Internal transfer" else null
                )
                
                transactions.add(tx)
            }
            
            transactions
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Transaction parsing failed for ${network}", null)
            emptyList()
        }
    }
    
    /**
     * Check transaction status by hash - returns CONFIRMED, PENDING, or FAILED
     * Also updates and saves the status if changed
     */
    actual suspend fun checkTransactionStatus(txHash: String, network: BlockchainNetwork): TransactionStatus = 
        withContext(Dispatchers.IO) {
            try {
                // TRON is not EVM — use TronGrid's tx-info endpoint instead of web3j.
                if (network == BlockchainNetwork.TRON) {
                    val tronStatus = checkTronTransactionStatus(txHash)
                    if (tronStatus != TransactionStatus.PENDING) {
                        updateTransactionStatus(txHash, tronStatus)
                    }
                    return@withContext tronStatus
                }
                if (!network.isEvm) {
                    return@withContext TransactionStatus.PENDING
                }

                initializeWeb3j(network)
                val web3 = web3j ?: return@withContext TransactionStatus.PENDING
                
                // Get transaction receipt
                val receipt = web3.ethGetTransactionReceipt(txHash).send()
                
                val newStatus = if (receipt.transactionReceipt.isPresent) {
                    val txReceipt = receipt.transactionReceipt.get()
                    if (txReceipt.isStatusOK) {
                        TransactionStatus.CONFIRMED
                    } else {
                        TransactionStatus.FAILED
                    }
                } else {
                    TransactionStatus.PENDING
                }
                
                // Update stored transaction status if changed
                if (newStatus != TransactionStatus.PENDING) {
                    updateTransactionStatus(txHash, newStatus)
                }
                
                newStatus
            } catch (e: Exception) {
                SecureLog.e("PlatformWallet", "Transaction status check failed on ${network}", null)
                TransactionStatus.PENDING
            }
        }
    
    /**
     * Send raw transaction (for swaps and complex contract interactions)
     * Used by SwapService to execute DEX aggregator transactions
     */
    actual suspend fun sendRawTransaction(
        network: BlockchainNetwork,
        to: String,
        data: String,
        value: String,
        gasLimit: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            initializeWeb3j(network)
            
            val web3 = web3j ?: return@withContext Result.failure(Exception("Web3j not initialized"))
            val creds = credentials ?: return@withContext Result.failure(Exception("Credentials not available"))
            
            val chainId = chainIds[network] ?: 1L
            
            // Get nonce
            val nonce = web3.ethGetTransactionCount(
                creds.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount
            
            // Get gas price (add 10% for faster confirmation)
            val gasPrice = web3.ethGasPrice().send().gasPrice
            val adjustedGasPrice = gasPrice.multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100))
            
            // Parse gas limit
            val gas = try {
                BigInteger(gasLimit)
            } catch (e: Exception) {
                BigInteger.valueOf(300000) // Default gas limit for swaps
            }
            
            // Parse value (in wei)
            val valueWei = try {
                if (value.startsWith("0x")) {
                    BigInteger(value.removePrefix("0x"), 16)
                } else {
                    BigInteger(value)
                }
            } catch (e: Exception) {
                BigInteger.ZERO
            }
            
            // Create raw transaction with data
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                adjustedGasPrice,
                gas,
                to,
                valueWei,
                data
            )
            
            // Sign transaction with chain ID (EIP-155)
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, creds)
            val hexValue = org.web3j.utils.Numeric.toHexString(signedMessage)
            
            // Broadcast transaction
            val txResponse = web3.ethSendRawTransaction(hexValue).send()
            
            if (txResponse.hasError()) {
                SecureLog.e("PlatformWallet", "Raw tx broadcast failed on ${network}", null)
                return@withContext Result.failure(Exception(txResponse.error.message))
            }
            
            val txHash = txResponse.transactionHash
            
            Result.success(txHash)
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Raw transaction failed on ${network}", null)
            Result.failure(Exception("Failed to send raw transaction: ${e.message}"))
        }
    }

    // ── Multi-wallet persistent storage ─────────────────────────────

    private const val KEY_WALLET_PROFILES = "multi_wallet_profiles"
    private const val KEY_WALLET_MNEMONIC_PREFIX = "wallet_mnemonic_"

    actual fun saveWalletProfiles(profilesJson: String) {
        val prefs = getPrefs() ?: return
        prefs.edit().putString(KEY_WALLET_PROFILES, profilesJson).commit()
    }

    actual fun loadWalletProfiles(): String? {
        return getPrefs()?.getString(KEY_WALLET_PROFILES, null)
    }

    actual fun saveMnemonicForWallet(walletId: String, mnemonic: String) {
        val chars = mnemonic.toCharArray()
        val encrypted = encryptMnemonic(chars) ?: run { WalletSecurity.wipe(chars); return }
        WalletSecurity.wipe(chars)
        val prefs = getPrefs() ?: return
        prefs.edit().putString(KEY_WALLET_MNEMONIC_PREFIX + walletId, encrypted).commit()
    }

    actual fun loadMnemonicForWallet(walletId: String): String? {
        val prefs = getPrefs() ?: return null
        val encrypted = prefs.getString(KEY_WALLET_MNEMONIC_PREFIX + walletId, null) ?: return null
        val chars = decryptMnemonicChars(encrypted) ?: return null
        val result = String(chars)
        WalletSecurity.wipe(chars)
        return result
    }

    actual fun deleteMnemonicForWallet(walletId: String) {
        val prefs = getPrefs() ?: return
        prefs.edit().remove(KEY_WALLET_MNEMONIC_PREFIX + walletId).commit()
    }

    // ── Encrypted balance snapshot ───────────────────────────────────

    private const val SECURE_ALIAS_BALANCE_SNAPSHOT = "balance_snapshot_enc"

    actual fun saveBalanceSnapshot(accounts: List<WalletAccount>, tokens: List<WalletToken>) {
        try {
            val sb = StringBuilder()
            sb.appendLine("__ACCOUNTS__")
            for (a in accounts) {
                sb.appendLine("${a.network.name}|${a.address}|${a.balance}|${a.balanceUsd}")
            }
            sb.appendLine("__TOKENS__")
            for (t in tokens) {
                sb.appendLine("${t.network.name}|${t.contractAddress}|${t.symbol}|${t.name}|${t.decimals}|${t.balance}|${t.balanceUsd}")
            }
            SecureStorage.encryptAndStore(
                SECURE_ALIAS_BALANCE_SNAPSHOT,
                sb.toString().toByteArray(Charsets.UTF_8).clone()
            )
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Balance snapshot save failed", null)
        }
    }

    actual fun loadBalanceSnapshot(): BalanceSnapshot? {
        return try {
            val bytes = SecureStorage.decryptFromStore(SECURE_ALIAS_BALANCE_SNAPSHOT) ?: return null
            val text = String(bytes, Charsets.UTF_8)
            if (text.isBlank()) return null

            val lines = text.lines()
            val accounts = mutableListOf<WalletAccount>()
            val tokens = mutableListOf<WalletToken>()
            var section = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed == "__ACCOUNTS__") { section = "A"; continue }
                if (trimmed == "__TOKENS__") { section = "T"; continue }
                if (trimmed.isBlank()) continue

                val parts = trimmed.split("|")
                when (section) {
                    "A" -> if (parts.size >= 4) {
                        val network = try { BlockchainNetwork.valueOf(parts[0]) } catch (_: Exception) { continue }
                        val address = parts[1]
                        val balance = parts[2]
                        val balanceUsd = parts[3].toDoubleOrNull() ?: 0.0
                        accounts.add(WalletAccount(
                            id = java.util.UUID.randomUUID().toString(),
                            name = network.displayName,
                            network = network,
                            address = address,
                            publicKey = "",
                            balance = balance,
                            balanceUsd = balanceUsd
                        ))
                    }
                    "T" -> if (parts.size >= 7) {
                        val network = try { BlockchainNetwork.valueOf(parts[0]) } catch (_: Exception) { continue }
                        tokens.add(WalletToken(
                            contractAddress = parts[1],
                            network = network,
                            symbol = parts[2],
                            name = parts[3],
                            decimals = parts[4].toIntOrNull() ?: 18,
                            balance = parts[5],
                            balanceUsd = parts[6].toDoubleOrNull() ?: 0.0
                        ))
                    }
                }
            }
            if (accounts.isEmpty() && tokens.isEmpty()) null
            else BalanceSnapshot(accounts, tokens)
        } catch (e: Exception) {
            SecureLog.e("PlatformWallet", "Balance snapshot load failed", null)
            null
        }
    }
}
