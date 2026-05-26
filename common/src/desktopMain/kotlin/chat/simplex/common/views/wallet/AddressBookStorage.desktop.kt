package chat.simplex.common.views.wallet

import java.io.File
import java.util.prefs.Preferences

/**
 * Desktop implementation of AddressBookStorage using Java Preferences API
 */
actual object AddressBookStorage {
    private val prefs: Preferences by lazy {
        Preferences.userNodeForPackage(AddressBookStorage::class.java)
    }
    
    actual fun save(key: String, value: String) {
        try {
            // Java Preferences has size limit, so we use file storage for large data
            if (value.length > 8000) {
                // Save to file for large data
                val file = getStorageFile(key)
                file.parentFile?.mkdirs()
                file.writeText(value)
                prefs.put(key, "FILE:${file.absolutePath}")
            } else {
                prefs.put(key, value)
            }
            prefs.flush()
        } catch (e: Exception) {
            // Silently handle save errors
        }
    }
    
    actual fun load(key: String): String? {
        return try {
            val value = prefs.get(key, null)
            if (value?.startsWith("FILE:") == true) {
                // Load from file
                val filePath = value.removePrefix("FILE:")
                File(filePath).takeIf { it.exists() }?.readText()
            } else {
                value
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getStorageFile(key: String): File {
        val userHome = System.getProperty("user.home")
        return File(userHome, ".simplex/wallet/$key.json")
    }
}

