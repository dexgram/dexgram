package chat.simplex.common.views.wallet

import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.res.MR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * WalletCoreService - Main wallet orchestration service
 * Supports multiple wallets (like Trust Wallet / Unstoppable).
 * Each wallet has its own mnemonic, derived accounts, tokens, and transactions.
 * Wallet profiles and encrypted mnemonics are persisted via PlatformWallet.
 */
object WalletCoreService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // ── Multi-wallet storage ────────────────────────────────────────────
    private val walletProfiles = mutableListOf<WalletProfile>()
    private var activeWalletId: String? = null

    // NOTE: mnemonics are NEVER held in memory.  They are decrypted on-demand
    // from PlatformWallet's encrypted storage, used briefly, then discarded.

    // ── Active wallet state ─────────────────────────────────────────────
    private var walletAccounts = mutableListOf<WalletAccount>()
    private var walletTransactions = mutableListOf<WalletTransaction>()
    private var walletTokens = mutableListOf<WalletToken>()

    // State flows
    private val _walletStateFlow = MutableStateFlow(WalletState())
    val walletStateFlow: StateFlow<WalletState> = _walletStateFlow.asStateFlow()

    @Volatile private var _initialized = false
    @Volatile private var _backgroundRefreshRunning = false
    private var backgroundRefreshJob: Job? = null

    // ════════════════════════════════════════════════════════════════════
    //  Persistence helpers
    // ════════════════════════════════════════════════════════════════════

    /** Persist the current wallet profiles list to storage */
    private fun persistProfiles() {
        try {
            val profilesJson = json.encodeToString(walletProfiles.toList())
            PlatformWallet.saveWalletProfiles(profilesJson)
        } catch (_: Exception) { }
    }

    /** Load wallet profiles from persistent storage into memory */
    private fun loadProfilesFromStorage() {
        try {
            val profilesJson = PlatformWallet.loadWalletProfiles() ?: return
            val loaded = json.decodeFromString<List<WalletProfile>>(profilesJson)
            walletProfiles.clear()
            walletProfiles.addAll(loaded)

            val active = walletProfiles.find { it.isActive }
            activeWalletId = active?.id ?: walletProfiles.firstOrNull()?.id
        } catch (_: Exception) { }
    }

    /** Persist a single wallet's mnemonic to encrypted storage */
    private fun persistMnemonic(walletId: String, mnemonic: String) {
        PlatformWallet.saveMnemonicForWallet(walletId, mnemonic)
    }

    /** Retrieve a wallet's mnemonic from encrypted storage (on-demand) */
    private fun loadMnemonic(walletId: String): String? {
        return PlatformWallet.loadMnemonicForWallet(walletId)
    }

    /** Remove a wallet's mnemonic from encrypted storage */
    private fun removeMnemonic(walletId: String) {
        PlatformWallet.deleteMnemonicForWallet(walletId)
    }

    // ════════════════════════════════════════════════════════════════════
    //  Multi-wallet API
    // ════════════════════════════════════════════════════════════════════

    /** Get all wallet profiles */
    fun getWalletProfiles(): List<WalletProfile> = walletProfiles.toList()

    /** Get currently active wallet id */
    fun getActiveWalletId(): String? = activeWalletId

    /** Get active wallet profile */
    fun getActiveWallet(): WalletProfile? =
        walletProfiles.find { it.id == activeWalletId }

    /**
     * Create a brand-new wallet (generates mnemonic, persists, and switches to it).
     */
    fun createNewWallet(name: String = String.format(generalGetString(MR.strings.wallet_default_name), walletProfiles.size + 1)): WalletCreationResult {
        val result = PlatformWallet.createWallet()
        if (!result.success) return result

        val walletId = UUID.randomUUID().toString()
        val profile = WalletProfile(
            id = walletId,
            name = name,
            isActive = true
        )

        // Deactivate all existing
        walletProfiles.forEachIndexed { i, p ->
            walletProfiles[i] = p.copy(isActive = false)
        }
        walletProfiles.add(profile)
        activeWalletId = walletId

        // Persist
        persistMnemonic(walletId, result.mnemonic)
        persistProfiles()

        walletAccounts.clear()
        walletAccounts.addAll(result.accounts)
        walletTransactions.clear()
        walletTokens.clear()
        updateWalletState()
        return result
    }

    /**
     * Import / recover a wallet from mnemonic and add it to the list.
     */
    fun importWallet(mnemonic: String, name: String = String.format(generalGetString(MR.strings.wallet_default_name), walletProfiles.size + 1)): WalletCreationResult {
        val result = PlatformWallet.recoverWallet(mnemonic)
        if (!result.success) return result

        val walletId = UUID.randomUUID().toString()
        val profile = WalletProfile(
            id = walletId,
            name = name,
            isActive = true
        )

        walletProfiles.forEachIndexed { i, p ->
            walletProfiles[i] = p.copy(isActive = false)
        }
        walletProfiles.add(profile)
        activeWalletId = walletId

        // Persist
        persistMnemonic(walletId, mnemonic)
        persistProfiles()

        walletAccounts.clear()
        walletAccounts.addAll(result.accounts)
        walletTransactions.clear()
        walletTokens.clear()
        updateWalletState()
        return result
    }

    /**
     * Switch to a different wallet by its profile id.
     */
    fun switchWallet(walletId: String): Boolean {
        val profile = walletProfiles.find { it.id == walletId } ?: return false
        val mnemonic = loadMnemonic(walletId) ?: return false

        // Re-derive keys from the selected wallet's mnemonic
        val result = PlatformWallet.recoverWallet(mnemonic)
        if (!result.success) return false

        walletProfiles.forEachIndexed { i, p ->
            walletProfiles[i] = p.copy(isActive = p.id == walletId)
        }
        activeWalletId = walletId
        persistProfiles()

        walletAccounts.clear()
        walletAccounts.addAll(result.accounts)
        walletTransactions.clear()
        walletTokens.clear()

        // Reload stored transactions for this wallet
        val storedTxs = PlatformWallet.getStoredTransactions()
        walletTransactions.addAll(storedTxs)

        updateWalletState()
        return true
    }

    /** Rename a wallet */
    fun renameWallet(walletId: String, newName: String): Boolean {
        val idx = walletProfiles.indexOfFirst { it.id == walletId }
        if (idx < 0) return false
        walletProfiles[idx] = walletProfiles[idx].copy(name = newName)
        persistProfiles()
        updateWalletState()
        return true
    }

    /** Delete a wallet (cannot delete the last one) */
    fun deleteWallet(walletId: String): Boolean {
        if (walletProfiles.size <= 1) return false
        val idx = walletProfiles.indexOfFirst { it.id == walletId }
        if (idx < 0) return false

        walletProfiles.removeAt(idx)
        removeMnemonic(walletId)
        persistProfiles()

        // If we deleted the active wallet, switch to the first remaining one
        if (activeWalletId == walletId) {
            val next = walletProfiles.firstOrNull()
            if (next != null) {
                switchWallet(next.id)
            }
        } else {
            updateWalletState()
        }
        return true
    }

    // ════════════════════════════════════════════════════════════════════
    //  Initialization
    // ════════════════════════════════════════════════════════════════════

    fun initialize(context: Any?) {
        if (_initialized) return
        PlatformWallet.initialize(context)
        WalletPriceService.initialize()
        AddressBook.initialize()

        loadProfilesFromStorage()

        val isInit = PlatformWallet.isInitialized()

        if (isInit && walletProfiles.isEmpty()) {
            val mnemonic = PlatformWallet.getMnemonic()
            if (mnemonic != null) {
                val walletId = UUID.randomUUID().toString()
                val profile = WalletProfile(id = walletId, name = generalGetString(MR.strings.wallet_main_wallet_name), isActive = true)
                walletProfiles.add(profile)
                activeWalletId = walletId

                persistMnemonic(walletId, mnemonic)
                persistProfiles()
            }
        }

        var walletLoaded = false

        if (walletProfiles.isNotEmpty()) {
            val activeId = activeWalletId ?: walletProfiles.first().id
            val mnemonic = loadMnemonic(activeId)

            if (mnemonic != null) {
                if (!isInit || PlatformWallet.getMnemonic() != mnemonic) {
                    PlatformWallet.recoverWallet(mnemonic)
                }
                walletLoaded = populateAccountsFromPlatform()
            }
        }

        if (!walletLoaded && isInit) {
            val mnemonic = PlatformWallet.getMnemonic()
            if (mnemonic != null) {
                walletProfiles.clear()
                val walletId = UUID.randomUUID().toString()
                walletProfiles.add(WalletProfile(id = walletId, name = generalGetString(MR.strings.wallet_main_wallet_name), isActive = true))
                activeWalletId = walletId
                persistMnemonic(walletId, mnemonic)
                persistProfiles()
                walletLoaded = populateAccountsFromPlatform()
            }
        }
        NetworkTokenPreferences.load()
        _initialized = true
    }

    /**
     * Called once at app startup (from SimplexApp.onCreate / MainActivity).
     * Initializes the wallet and immediately kicks off background balance,
     * token, and transaction fetches so data is ready before the user
     * opens the wallet screen.
     */
    fun initializeInBackground(context: Any?) {
        scope.launch {
            initialize(context)
            if (isWalletInitialized()) {
                startBackgroundRefresh()
            }
        }
    }

    /**
     * Kicks off a single background refresh cycle: balances, tokens,
     * transactions, and blockchain watchers.  Safe to call multiple times;
     * concurrent calls are ignored.
     */
    fun startBackgroundRefresh() {
        if (_backgroundRefreshRunning) return
        _backgroundRefreshRunning = true
        backgroundRefreshJob?.cancel()
        backgroundRefreshJob = scope.launch {
            try {
                NotificationService.seedFromStoredTransactions()
                BlockchainService.seedNotifiedHashes()

                _isUpdatingBalances = true
                updateWalletState()

                // Run balances and transactions in parallel
                supervisorScope {
                    val balJob = async { try { refreshAllBalances() } catch (_: Exception) { } }
                    val txJob = async { try { fetchAllTransactions() } catch (_: Exception) { } }
                    balJob.await()
                    txJob.await()
                }

                _isUpdatingBalances = false
                _isCachedData = false
                updateWalletState()

                startBlockchainWatchers()
            } catch (_: Exception) {
                _isUpdatingBalances = false
                _isCachedData = false
                updateWalletState()
            } finally {
                _backgroundRefreshRunning = false
            }
        }
    }

    /**
     * Periodic background sync.  Runs every [intervalMs] while the
     * wallet is initialized.  Launched once from app startup.
     */
    fun startPeriodicSync(intervalMs: Long = 30_000L) {
        scope.launch {
            while (true) {
                delay(intervalMs)
                if (!isWalletInitialized()) continue
                if (!NetworkStatusService.isConnected.value) continue
                try {
                    refreshAllBalances()
                    updatePendingTransactions()
                } catch (_: Exception) { }
            }
        }
    }

    private fun populateAccountsFromPlatform(): Boolean {
        val accounts = BlockchainNetwork.ALL_SUPPORTED.mapNotNull { network ->
            val address = PlatformWallet.getAddress(network)
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
        if (accounts.isEmpty()) return false
        walletAccounts.clear()
        walletAccounts.addAll(accounts)

        // Restore cached balances from encrypted storage
        val snapshot = PlatformWallet.loadBalanceSnapshot()
        if (snapshot != null) {
            val (cachedAccounts, cachedTokens) = snapshot
            for (cached in cachedAccounts) {
                val idx = walletAccounts.indexOfFirst { it.network == cached.network }
                if (idx >= 0) {
                    walletAccounts[idx] = walletAccounts[idx].copy(
                        balance = cached.balance,
                        balanceUsd = cached.balanceUsd
                    )
                }
            }
            walletTokens.clear()
            walletTokens.addAll(cachedTokens)
            _isCachedData = true
        }

        val storedTxs = PlatformWallet.getStoredTransactions()
        walletTransactions.clear()
        walletTransactions.addAll(storedTxs)

        NotificationService.seedFromStoredTransactions()
        BlockchainService.seedNotifiedHashes()
        updateWalletState()
        return true
    }

    // ════════════════════════════════════════════════════════════════════
    //  Legacy single-wallet API (used by Create/Recover views)
    // ════════════════════════════════════════════════════════════════════

    fun createWallet(): WalletCreationResult {
        val result = PlatformWallet.createWallet()
        if (result.success) {
            if (walletProfiles.isEmpty()) {
                val walletId = UUID.randomUUID().toString()
                walletProfiles.add(WalletProfile(id = walletId, name = generalGetString(MR.strings.wallet_main_wallet_name), isActive = true))
                activeWalletId = walletId
                persistMnemonic(walletId, result.mnemonic)
                persistProfiles()
            }
            walletAccounts.clear()
            walletAccounts.addAll(result.accounts)
            updateWalletState()
        }
        return result
    }

    fun generateNewMnemonic(): String = PlatformWallet.generateNewMnemonic()

    fun recoverWallet(mnemonicPhrase: String): WalletCreationResult {
        val result = PlatformWallet.recoverWallet(mnemonicPhrase)
        if (result.success) {
            if (walletProfiles.isEmpty()) {
                val walletId = UUID.randomUUID().toString()
                walletProfiles.add(WalletProfile(id = walletId, name = generalGetString(MR.strings.wallet_main_wallet_name), isActive = true))
                activeWalletId = walletId
                persistMnemonic(walletId, mnemonicPhrase)
                persistProfiles()
            }
            walletAccounts.clear()
            walletAccounts.addAll(result.accounts)
            updateWalletState()
        }
        return result
    }

    fun validateMnemonic(mnemonicPhrase: String): Boolean =
        PlatformWallet.validateMnemonic(mnemonicPhrase)

    fun getAccounts(): List<WalletAccount> = walletAccounts.toList()
    fun getAccounts(walletId: String): List<WalletAccount> {
        if (walletId == activeWalletId) return walletAccounts.toList()
        return emptyList()
    }
    fun getAccount(network: BlockchainNetwork): WalletAccount? =
        walletAccounts.find { it.network == network }

    fun getTransactions(): List<WalletTransaction> = walletTransactions.toList()

    suspend fun fetchTransactions(account: WalletAccount): List<WalletTransaction> {
        return try {
            val transactions = PlatformWallet.fetchTransactions(account.address, account.network)
            walletTransactions.removeAll { it.network == account.network }
            walletTransactions.addAll(transactions)
            walletTransactions = walletTransactions
                .distinctBy { it.txHash + (it.tokenSymbol ?: "") }
                .sortedByDescending { it.timestamp }
                .toMutableList()
            updateWalletState()
            transactions
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAllTransactions(): List<WalletTransaction> = supervisorScope {
        val results = walletAccounts.map { account ->
            async {
                try {
                    PlatformWallet.fetchTransactions(account.address, account.network)
                } catch (_: Exception) { emptyList() }
            }
        }.map { try { it.await() } catch (_: Exception) { emptyList() } }

        val unique = results.flatten()
            .distinctBy { it.txHash + (it.tokenSymbol ?: "") }
            .sortedByDescending { it.timestamp }
        walletTransactions.clear()
        walletTransactions.addAll(unique)
        updateWalletState()
        unique
    }

    suspend fun fetchBalance(account: WalletAccount): String =
        PlatformWallet.fetchBalance(account)

    fun validateAddress(address: String, network: BlockchainNetwork): AddressValidation {
        val isValid = PlatformWallet.validateAddress(address, network)
        return AddressValidation(isValid, if (isValid) network else null)
    }

    suspend fun estimateFee(network: BlockchainNetwork): FeeEstimate =
        PlatformWallet.estimateFee(network)

    /**
     * Send a transaction.  Guarded by [WalletLockManager] -- callers must
     * ensure the user has authenticated (biometric / PIN) before calling.
     */
    suspend fun sendTransaction(request: SendTransactionRequest): Result<WalletTransaction> {
        if (WalletLockManager.requiresAuth(WalletAction.SEND) && WalletLockManager.isLocked()) {
            return Result.failure(SecurityException(generalGetString(MR.strings.wallet_auth_required_send)))
        }
        WalletLockManager.refreshAuthTime()
        val result = PlatformWallet.sendTransaction(request)
        result.onSuccess { tx ->
            walletTransactions.add(0, tx)
            updateWalletState()

            if (tx.isPending) {
                scope.launch {
                    pollAndPersistTxConfirmation(tx)
                }
            }
        }
        result.onFailure { e ->
            NotificationService.notifySendFailed(
                network = request.network,
                symbol = request.tokenContractAddress ?: request.network.symbol,
                amount = request.amount,
                reason = e.message ?: generalGetString(MR.strings.wallet_unknown_error)
            )
        }
        return result
    }

    /**
     * Poll a freshly-broadcast transaction until it lands on-chain, then update
     * both the in-memory list and persistent storage.  Works for every supported
     * network — EVM uses BlockchainService (which can also count confirmations),
     * non-EVM (TRON, etc.) uses the generic PlatformWallet.checkTransactionStatus.
     */
    private suspend fun pollAndPersistTxConfirmation(tx: WalletTransaction) {
        val finalStatus: TransactionStatus
        val finalConfirmations: Int

        if (tx.network.isEvm) {
            val conf = BlockchainService.pollTransactionConfirmation(
                txHash = tx.txHash, network = tx.network, requiredConfirmations = 1
            )
            finalStatus = conf.status
            finalConfirmations = conf.confirmations
        } else {
            // Non-EVM (TRON, etc.): poll PlatformWallet.checkTransactionStatus
            // with exponential backoff up to ~5 minutes total.
            val timeoutMs = 300_000L
            val start = System.currentTimeMillis()
            var pollInterval = 5_000L
            var status = TransactionStatus.PENDING
            while (System.currentTimeMillis() - start < timeoutMs) {
                status = try {
                    PlatformWallet.checkTransactionStatus(tx.txHash, tx.network)
                } catch (_: Exception) { TransactionStatus.PENDING }
                if (status != TransactionStatus.PENDING) break
                kotlinx.coroutines.delay(pollInterval)
                pollInterval = (pollInterval * 1.3).toLong().coerceAtMost(30_000L)
            }
            finalStatus = status
            finalConfirmations = if (status == TransactionStatus.CONFIRMED) 1 else 0
        }

        val idx = walletTransactions.indexOfFirst { it.txHash == tx.txHash }
        if (idx < 0) return
        val updated = walletTransactions[idx].copy(
            status = finalStatus, confirmations = finalConfirmations
        )
        walletTransactions[idx] = updated
        PlatformWallet.addTransaction(updated)
        updateWalletState()
        WalletCache.clearBalances()

        when (finalStatus) {
            TransactionStatus.CONFIRMED -> NotificationService.notifyTokensSent(
                network = tx.network,
                symbol = tx.tokenSymbol ?: tx.network.symbol,
                amount = tx.amount,
                txHash = tx.txHash,
                toAddress = tx.toAddress
            )
            TransactionStatus.FAILED -> NotificationService.notifySendFailed(
                network = tx.network,
                symbol = tx.tokenSymbol ?: tx.network.symbol,
                amount = tx.amount,
                reason = generalGetString(MR.strings.wallet_tx_failed_on_chain)
            )
            else -> { /* still pending after timeout — periodic sync will pick it up */ }
        }
    }

    fun isWalletInitialized(): Boolean = PlatformWallet.isInitialized()

    fun hasPersistedWallet(): Boolean = PlatformWallet.hasPersistedWallet()

    /**
     * Returns the active mnemonic. Guarded -- requires prior authentication.
     */
    fun getMnemonic(): String? {
        if (WalletLockManager.requiresAuth(WalletAction.EXPORT_MNEMONIC) && WalletLockManager.isLocked()) return null
        WalletLockManager.refreshAuthTime()
        return PlatformWallet.getMnemonic()
    }

    /** Get mnemonic for a specific wallet (for backup seed view).
     *  Guarded. Decrypted on-demand -- caller should discard result promptly. */
    fun getMnemonicForWallet(walletId: String): String? {
        if (WalletLockManager.requiresAuth(WalletAction.EXPORT_MNEMONIC) && WalletLockManager.isLocked()) return null
        WalletLockManager.refreshAuthTime()
        return loadMnemonic(walletId)
    }

    private var _isUpdatingBalances = false
    private var _isCachedData = false

    private fun updateWalletState() {
        val accountsUsd = walletAccounts.sumOf { it.balanceUsd }
        val tokensUsd = walletTokens.sumOf { it.balanceUsd }
        _walletStateFlow.value = WalletState(
            accounts = walletAccounts.toList(),
            tokens = walletTokens.toList(),
            transactions = walletTransactions.toList(),
            totalBalanceUsd = accountsUsd + tokensUsd,
            isInitialized = isWalletInitialized(),
            isLocked = false,
            wallets = walletProfiles.toList(),
            activeWalletId = activeWalletId,
            isUpdatingBalances = _isUpdatingBalances,
            isCachedData = _isCachedData
        )
    }

    private fun persistBalanceSnapshot() {
        try {
            PlatformWallet.saveBalanceSnapshot(walletAccounts.toList(), walletTokens.toList())
        } catch (_: Exception) { }
    }

    fun clearWallet() {
        PlatformWallet.clearWallet()
        walletAccounts.clear()
        walletTransactions.clear()
        walletTokens.clear()
        PlatformWallet.saveBalanceSnapshot(emptyList(), emptyList())
        // Remove the active wallet from multi-wallet list
        val currentId = activeWalletId
        if (currentId != null) {
            walletProfiles.removeAll { it.id == currentId }
            removeMnemonic(currentId)
            persistProfiles()
        }
        if (walletProfiles.isNotEmpty()) {
            switchWallet(walletProfiles.first().id)
        } else {
            activeWalletId = null
            updateWalletState()
        }
    }

    suspend fun fetchTokenBalances(account: WalletAccount): List<WalletToken> {
        return try {
            val tokens = PlatformWallet.fetchAllTokenBalances(account.network, account.address)
            walletTokens.removeAll { it.network == account.network }
            walletTokens.addAll(tokens)
            updateWalletState()
            tokens
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchAllTokenBalances(): List<WalletToken> = supervisorScope {
        val enabledAccounts = walletAccounts.filter { NetworkTokenPreferences.isNetworkEnabled(it.network) }
        val results = enabledAccounts.map { account ->
            async {
                try {
                    PlatformWallet.fetchAllTokenBalances(account.network, account.address)
                } catch (_: Exception) { emptyList() }
            }
        }.map { try { it.await() } catch (_: Exception) { emptyList() } }

        val allTokens = results.flatten().filter {
            NetworkTokenPreferences.isTokenEnabled(it.network, it.symbol, it.contractAddress)
        }
        walletTokens.clear()
        walletTokens.addAll(allTokens)
        updateWalletState()
        allTokens
    }

    fun getAllTokens(): List<WalletToken> = walletTokens.toList()

    suspend fun sendTokenTransaction(
        token: WalletToken,
        toAddress: String,
        amount: String
    ): Result<WalletTransaction> {
        if (WalletLockManager.requiresAuth(WalletAction.SEND) && WalletLockManager.isLocked()) {
            return Result.failure(SecurityException(generalGetString(MR.strings.wallet_auth_required_send_tokens)))
        }
        WalletLockManager.refreshAuthTime()
        val fromAddress = walletAccounts.find { it.network == token.network }?.address
            ?: return Result.failure(Exception("No account for network ${token.network}"))
        val result = PlatformWallet.sendTokenTransaction(token, fromAddress, toAddress, amount)
        result.onSuccess { tx ->
            walletTransactions.add(0, tx)
            updateWalletState()

            if (tx.isPending) {
                scope.launch {
                    pollAndPersistTxConfirmation(tx)
                }
            }
        }
        result.onFailure { e ->
            NotificationService.notifySendFailed(
                network = token.network,
                symbol = token.symbol,
                amount = amount,
                reason = e.message ?: generalGetString(MR.strings.wallet_unknown_error)
            )
        }
        return result
    }

    suspend fun updatePendingTransactions(): Int {
        var updatedCount = 0
        val pending = walletTransactions.filter { it.status == TransactionStatus.PENDING }
        for (tx in pending) {
            try {
                val newStatus = PlatformWallet.checkTransactionStatus(tx.txHash, tx.network)
                if (newStatus != TransactionStatus.PENDING) {
                    val index = walletTransactions.indexOfFirst { it.txHash == tx.txHash }
                    if (index >= 0) {
                        val updated = tx.copy(status = newStatus)
                        walletTransactions[index] = updated
                        PlatformWallet.addTransaction(updated)
                        updatedCount++

                        if (newStatus == TransactionStatus.CONFIRMED) {
                            if (tx.type == TransactionType.SEND || tx.type == TransactionType.TOKEN_TRANSFER) {
                                NotificationService.notifyTokensSent(
                                    network = tx.network,
                                    symbol = tx.tokenSymbol ?: tx.network.symbol,
                                    amount = tx.amount,
                                    txHash = tx.txHash,
                                    toAddress = tx.toAddress
                                )
                            }
                        } else if (newStatus == TransactionStatus.FAILED) {
                            NotificationService.notifySendFailed(
                                network = tx.network,
                                symbol = tx.tokenSymbol ?: tx.network.symbol,
                                amount = tx.amount,
                                reason = generalGetString(MR.strings.wallet_tx_failed_on_chain)
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        if (updatedCount > 0) {
            WalletCache.clearBalances()
            updateWalletState()
        }
        return updatedCount
    }

    // ════════════════════════════════════════════════════════════════════
    //  Production: full balance refresh for a wallet
    // ════════════════════════════════════════════════════════════════════

    suspend fun refreshAllBalances(walletId: String? = null) {
        val targetId = walletId ?: activeWalletId ?: return
        if (targetId != activeWalletId) return

        WalletCache.clearBalances()

        // Phase 1: Fetch native balances only for enabled networks
        supervisorScope {
            val balanceJobs = walletAccounts
                .filter { NetworkTokenPreferences.isNetworkEnabled(it.network) }
                .map { account ->
                    async {
                        try { account.network to PlatformWallet.fetchBalance(account) }
                        catch (_: Exception) { null }
                    }
                }

            val priceJob = async {
                try { WalletPriceService.refreshPrices() } catch (_: Exception) { }
            }

            balanceJobs.mapNotNull { try { it.await() } catch (_: Exception) { null } }
                .forEach { (net, bal) ->
                    val idx = walletAccounts.indexOfFirst { it.network == net }
                    if (idx >= 0) walletAccounts[idx] = walletAccounts[idx].copy(balance = bal)
                }

            updateWalletState()

            try { priceJob.await() } catch (_: Exception) { }
        }

        // Phase 2: Token balances (already parallelized internally)
        try { fetchAllTokenBalances() } catch (_: Exception) { }

        // Phase 3: Apply USD values from refreshed prices
        for (i in walletAccounts.indices) {
            val a = walletAccounts[i]
            val bal = a.balance.toDoubleOrNull() ?: 0.0
            val price = WalletPriceService.getPrice(a.network.symbol)
            walletAccounts[i] = a.copy(balanceUsd = bal * price)
        }
        for (i in walletTokens.indices) {
            val t = walletTokens[i]
            val bal = t.balance.toDoubleOrNull() ?: 0.0
            val price = WalletPriceService.getPrice(t.symbol)
            walletTokens[i] = t.copy(balanceUsd = bal * price)
        }

        persistBalanceSnapshot()
        updateWalletState()
    }

    // ════════════════════════════════════════════════════════════════════
    //  Production: start blockchain watchers for active accounts
    // ════════════════════════════════════════════════════════════════════

    fun startBlockchainWatchers() {
        val watchableNetworks = setOf(
            *BlockchainNetwork.EVM_NETWORKS.toTypedArray(),
            BlockchainNetwork.TRON,
            BlockchainNetwork.SOLANA,
            BlockchainNetwork.BITCOIN
        )
        walletAccounts.filter { it.network in watchableNetworks }.forEach { account ->
            BlockchainService.watchIncomingTransfers(
                account.address,
                account.network,
                if (account.network.isEvm) ProductionConfig.TRANSFER_SCAN_INTERVAL_MS
                else 30_000L // non-EVM: poll every 30s to avoid rate limits
            )
        }
        BlockchainService.startBlockPolling()
        SwapManager.resumePendingSwaps()
    }

    fun stopBlockchainWatchers() {
        BlockchainService.stopAllWatchers()
        BlockchainService.stopBlockPolling()
    }
}
