package chat.simplex.common.views.wallet

import android.content.Context
import android.content.SharedPreferences
import chat.simplex.common.platform.androidAppContext

/**
 * Android implementation of AddressBookStorage using SharedPreferences
 */
actual object AddressBookStorage {
    private const val PREFS_NAME = "wallet_address_book"
    
    private fun getPrefs(): SharedPreferences? {
        return try {
            androidAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }
    
    actual fun save(key: String, value: String) {
        try {
            getPrefs()?.edit()?.apply {
                putString(key, value)
                apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("AddressBookStorage", "Error saving: ${e.message}")
        }
    }
    
    actual fun load(key: String): String? {
        return try {
            getPrefs()?.getString(key, null)
        } catch (e: Exception) {
            android.util.Log.e("AddressBookStorage", "Error loading: ${e.message}")
            null
        }
    }
}

