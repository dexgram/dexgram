package chat.simplex.common.views.wallet

/**
 * Platform-specific wallet operations
 * Expect declarations - implemented separately for Android and Desktop
 */

expect object PlatformWallet {
    /**
     * Initialize platform-specific wallet services
     */
    fun initialize(context: Any?)
    
    /**
     * Create a new wallet with mnemonic
     */
    fun createWallet(): WalletCreationResult

    /**
     * Generate a new mnemonic WITHOUT creating/persisting a wallet.
     * Used for "show + verify seed phrase" flows.
     */
    fun generateNewMnemonic(): String
    
    /**
     * Recover wallet from mnemonic
     */
    fun recoverWallet(mnemonic: String): WalletCreationResult
    
    /**
     * Validate mnemonic phrase
     */
    fun validateMnemonic(mnemonic: String): Boolean
    
    /**
     * Get wallet address for network
     */
    fun getAddress(network: BlockchainNetwork): String
    
    /**
     * Fetch balance (suspend function wrapper)
     */
    suspend fun fetchBalance(account: WalletAccount): String
    
    /**
     * Send transaction
     */
    suspend fun sendTransaction(request: SendTransactionRequest): Result<WalletTransaction>
    
    /**
     * Estimate fee
     */
    suspend fun estimateFee(network: BlockchainNetwork): FeeEstimate
    
    /**
     * Check if wallet is initialized (decrypted and in memory)
     */
    fun isInitialized(): Boolean

    /**
     * Check if encrypted wallet data exists on disk (even if not yet decrypted).
     * Used to detect "wallet was saved but Keystore failed" on reboot.
     */
    fun hasPersistedWallet(): Boolean
    
    /**
     * Get current mnemonic
     */
    fun getMnemonic(): String?
    
    /**
     * Clear wallet data
     */
    fun clearWallet()
    
    /**
     * Validate address for network
     */
    fun validateAddress(address: String, network: BlockchainNetwork): Boolean
    
    /**
     * Fetch transaction history from blockchain
     */
    suspend fun fetchTransactions(address: String, network: BlockchainNetwork): List<WalletTransaction>
    
    /**
     * Check transaction status by hash (for updating pending transactions)
     */
    suspend fun checkTransactionStatus(txHash: String, network: BlockchainNetwork): TransactionStatus
    
    /**
     * Get stored transactions from persistent storage
     */
    fun getStoredTransactions(): List<WalletTransaction>

    /**
     * Add or update a transaction in persistent storage
     */
    fun addTransaction(tx: WalletTransaction)
    
    /**
     * Fetch ERC20 token balance
     */
    suspend fun fetchTokenBalance(token: WalletToken, walletAddress: String): String
    
    /**
     * Fetch all token balances for a network
     */
    suspend fun fetchAllTokenBalances(network: BlockchainNetwork, walletAddress: String): List<WalletToken>
    
    /**
     * Send ERC20 token transaction
     */
    suspend fun sendTokenTransaction(
        token: WalletToken,
        fromAddress: String,
        toAddress: String,
        amount: String
    ): Result<WalletTransaction>
    
    /**
     * Send raw transaction (for swaps and complex transactions)
     * Returns transaction hash on success
     */
    suspend fun sendRawTransaction(
        network: BlockchainNetwork,
        to: String,
        data: String,
        value: String,
        gasLimit: String
    ): Result<String>

    // ── Multi-wallet persistent storage ─────────────────────────────

    /**
     * Save the wallet profiles list as JSON string to persistent storage.
     */
    fun saveWalletProfiles(profilesJson: String)

    /**
     * Load the wallet profiles JSON string from persistent storage.
     * Returns null if nothing has been stored yet.
     */
    fun loadWalletProfiles(): String?

    /**
     * Save encrypted mnemonic for a specific wallet id.
     */
    fun saveMnemonicForWallet(walletId: String, mnemonic: String)

    /**
     * Load and decrypt the mnemonic for a specific wallet id.
     * Returns null if not found.
     */
    fun loadMnemonicForWallet(walletId: String): String?

    /**
     * Delete the stored mnemonic for a specific wallet id.
     */
    fun deleteMnemonicForWallet(walletId: String)

    // ── Encrypted balance cache ──────────────────────────────────────

    fun saveBalanceSnapshot(accounts: List<WalletAccount>, tokens: List<WalletToken>)

    fun loadBalanceSnapshot(): BalanceSnapshot?
}

