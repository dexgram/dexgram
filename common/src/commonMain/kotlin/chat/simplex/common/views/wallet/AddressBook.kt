package chat.simplex.common.views.wallet

import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Address Book - Manage saved addresses for easy sending
 * Now with persistent storage!
 */
object AddressBook {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    // In-memory storage (synced with persistent storage)
    private val addresses = ConcurrentHashMap<String, SavedAddress>()
    
    // State flow for reactive updates
    private val _addressesFlow = MutableStateFlow<List<SavedAddress>>(emptyList())
    val addressesFlow: StateFlow<List<SavedAddress>> = _addressesFlow.asStateFlow()
    
    // Flag to track if we've loaded from storage
    private var isLoaded = false
    
    /**
     * Saved address entry
     */
    @Serializable
    data class SavedAddress(
        val id: String = java.util.UUID.randomUUID().toString(),
        val address: String,
        val label: String,
        val network: BlockchainNetwork? = null, // null = all networks (EVM)
        val notes: String? = null,
        val isFavorite: Boolean = false,
        val useCount: Int = 0,
        val lastUsed: Long? = null,
        val addedAt: Long = System.currentTimeMillis()
    ) {
        /**
         * Check if this address is valid for a given network
         */
        fun isValidFor(targetNetwork: BlockchainNetwork): Boolean {
            // If no specific network, valid for all EVM networks
            if (network == null) {
                return targetNetwork.coinType == 60 // EVM networks use coin type 60
            }
            return network == targetNetwork
        }
        
        /**
         * Shortened address for display
         */
        fun shortAddress(): String {
            return if (address.length > 16) {
                "${address.take(8)}...${address.takeLast(6)}"
            } else address
        }
    }
    
    /**
     * Initialize and load from persistent storage
     */
    fun initialize() {
        if (!isLoaded) {
            loadFromStorage()
            isLoaded = true
        }
    }
    
    /**
     * Add or update an address
     */
    fun save(savedAddress: SavedAddress): SavedAddress {
        initialize() // Ensure loaded
        
        // Check for existing address (by address string)
        val existing = addresses.values.find { 
            it.address.equals(savedAddress.address, ignoreCase = true) 
        }
        
        val toSave = if (existing != null) {
            // Update existing
            savedAddress.copy(id = existing.id, addedAt = existing.addedAt)
        } else {
            savedAddress
        }
        
        addresses[toSave.id] = toSave
        updateFlow()
        saveToStorage() // Persist changes
        return toSave
    }
    
    /**
     * Quick save address with label
     */
    fun quickSave(address: String, label: String, network: BlockchainNetwork? = null): SavedAddress {
        return save(SavedAddress(
            address = address,
            label = label,
            network = network
        ))
    }
    
    /**
     * Get address by ID
     */
    fun get(id: String): SavedAddress? {
        initialize()
        return addresses[id]
    }
    
    /**
     * Get address by address string
     */
    fun getByAddress(address: String): SavedAddress? {
        initialize()
        return addresses.values.find { it.address.equals(address, ignoreCase = true) }
    }
    
    /**
     * Get all saved addresses
     */
    fun getAll(): List<SavedAddress> {
        initialize()
        return addresses.values.toList()
            .sortedByDescending { it.lastUsed ?: it.addedAt }
    }
    
    /**
     * Get addresses for a specific network
     */
    fun getForNetwork(network: BlockchainNetwork): List<SavedAddress> {
        initialize()
        return addresses.values
            .filter { it.isValidFor(network) }
            .sortedByDescending { it.lastUsed ?: it.addedAt }
    }
    
    /**
     * Get favorite addresses
     */
    fun getFavorites(): List<SavedAddress> {
        initialize()
        return addresses.values
            .filter { it.isFavorite }
            .sortedByDescending { it.lastUsed ?: it.addedAt }
    }
    
    /**
     * Get recently used addresses
     */
    fun getRecent(limit: Int = 5): List<SavedAddress> {
        initialize()
        return addresses.values
            .filter { it.lastUsed != null }
            .sortedByDescending { it.lastUsed }
            .take(limit)
    }
    
    /**
     * Search addresses by label or address
     */
    fun search(query: String): List<SavedAddress> {
        initialize()
        val lowerQuery = query.lowercase()
        return addresses.values.filter { 
            it.label.lowercase().contains(lowerQuery) ||
            it.address.lowercase().contains(lowerQuery) ||
            it.notes?.lowercase()?.contains(lowerQuery) == true
        }.sortedByDescending { it.lastUsed ?: it.addedAt }
    }
    
    /**
     * Toggle favorite status
     */
    fun toggleFavorite(id: String): SavedAddress? {
        initialize()
        val address = addresses[id] ?: return null
        val updated = address.copy(isFavorite = !address.isFavorite)
        addresses[id] = updated
        updateFlow()
        saveToStorage()
        return updated
    }
    
    /**
     * Mark address as used (updates lastUsed and useCount)
     */
    fun markUsed(addressOrId: String): SavedAddress? {
        initialize()
        // Find by ID or address string
        val address = addresses[addressOrId] 
            ?: addresses.values.find { it.address.equals(addressOrId, ignoreCase = true) }
            ?: return null
        
        val updated = address.copy(
            lastUsed = System.currentTimeMillis(),
            useCount = address.useCount + 1
        )
        addresses[updated.id] = updated
        updateFlow()
        saveToStorage()
        return updated
    }
    
    /**
     * Delete an address
     */
    fun delete(id: String): Boolean {
        initialize()
        val removed = addresses.remove(id) != null
        if (removed) {
            updateFlow()
            saveToStorage()
        }
        return removed
    }
    
    /**
     * Delete all addresses
     */
    fun deleteAll() {
        addresses.clear()
        updateFlow()
        saveToStorage()
    }
    
    /**
     * Check if an address is saved
     */
    fun isSaved(address: String): Boolean {
        initialize()
        return addresses.values.any { it.address.equals(address, ignoreCase = true) }
    }
    
    /**
     * Get label for an address if saved
     */
    fun getLabel(address: String): String? {
        return getByAddress(address)?.label
    }
    
    /**
     * Export address book as JSON
     */
    fun export(): String {
        initialize()
        return json.encodeToString(addresses.values.toList())
    }
    
    /**
     * Import addresses from JSON
     */
    fun import(jsonString: String, overwrite: Boolean = false): Int {
        initialize()
        return try {
            val imported = json.decodeFromString<List<SavedAddress>>(jsonString)
            
            if (overwrite) {
                addresses.clear()
            }
            
            var count = 0
            imported.forEach { addr ->
                if (overwrite || !isSaved(addr.address)) {
                    // Don't recursively save to storage - we'll do it once at the end
                    addresses[addr.id] = addr
                    count++
                }
            }
            
            if (count > 0) {
                updateFlow()
                saveToStorage()
            }
            
            count
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get total count
     */
    fun count(): Int {
        initialize()
        return addresses.size
    }
    
    private fun updateFlow() {
        _addressesFlow.value = addresses.values.toList()
            .sortedByDescending { it.lastUsed ?: it.addedAt }
    }
    
    // ==================== Persistence ====================
    
    private const val STORAGE_KEY = "address_book_data"
    
    /**
     * Save address book to persistent storage
     */
    private fun saveToStorage() {
        try {
            val jsonData = json.encodeToString(addresses.values.toList())
            AddressBookStorage.save(STORAGE_KEY, jsonData)
        } catch (e: Exception) {
            // Silently handle save errors
        }
    }
    
    /**
     * Load address book from persistent storage
     */
    private fun loadFromStorage() {
        try {
            val jsonData = AddressBookStorage.load(STORAGE_KEY)
            if (jsonData != null && jsonData.isNotBlank()) {
                val loaded = json.decodeFromString<List<SavedAddress>>(jsonData)
                addresses.clear()
                loaded.forEach { addr ->
                    addresses[addr.id] = addr
                }
                updateFlow()
            }
        } catch (e: Exception) {
            // Silently handle load errors
        }
    }
}

/**
 * Platform-agnostic storage interface
 * Implemented by platform-specific code
 */
expect object AddressBookStorage {
    fun save(key: String, value: String)
    fun load(key: String): String?
}

