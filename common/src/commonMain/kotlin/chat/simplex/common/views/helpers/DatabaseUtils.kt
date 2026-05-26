package chat.simplex.common.views.helpers

import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.res.MR
import kotlinx.serialization.*
import java.io.File
import java.security.SecureRandom

object DatabaseUtils {
  private val appPreferences: AppPreferences = ChatController.appPrefs

  private const val DATABASE_PASSWORD_ALIAS: String = "databasePassword"
  private const val APP_PASSWORD_ALIAS: String = "appPassword"
  private const val SELF_DESTRUCT_PASSWORD_ALIAS: String = "selfDestructPassword"
  // SECURITY: All YubiKey secrets encrypted via Android Keystore (AES-GCM, StrongBox where available)
  private const val YUBIKEY_MANAGEMENT_KEY_ALIAS: String = "yubiKeyManagementKey"
  private const val YUBIKEY_CHALLENGE_ALIAS: String = "yubiKeyChallenge"
  private const val YUBIKEY_IDENTITY_SECRET_ALIAS: String = "yubiKeyIdentitySecret"
  private const val YUBIKEY_PUBLIC_KEY_ALIAS: String = "yubiKeyPublicKey"
  private const val YUBIKEY_PUK_HASH_ALIAS: String = "yubiKeyPukHash"
  private const val YUBIKEY_LOCKOUT_STATE_ALIAS: String = "yubiKeyLockoutState"
  private const val YUBIKEY_WRAPPED_DMK_ALIAS: String = "yubiKeyWrappedDMK"
  private const val YUBIKEY_EPHEMERAL_PUB_ALIAS: String = "yubiKeyEphemeralPub"
  private const val YUBIKEY_HKDF_SALT_ALIAS: String = "yubiKeyHkdfSalt"

  val ksDatabasePassword = KeyStoreItem(DATABASE_PASSWORD_ALIAS, appPreferences.encryptedDBPassphrase, appPreferences.initializationVectorDBPassphrase)
  val ksAppPassword = KeyStoreItem(APP_PASSWORD_ALIAS, appPreferences.encryptedAppPassphrase, appPreferences.initializationVectorAppPassphrase)
  val ksSelfDestructPassword = KeyStoreItem(SELF_DESTRUCT_PASSWORD_ALIAS, appPreferences.encryptedSelfDestructPassphrase, appPreferences.initializationVectorSelfDestructPassphrase)
  val ksYubiKeyManagementKey = KeyStoreItem(YUBIKEY_MANAGEMENT_KEY_ALIAS, appPreferences.encryptedYubiKeyManagementKey, appPreferences.initializationVectorYubiKeyManagementKey)
  val ksYubiKeyChallenge = KeyStoreItem(YUBIKEY_CHALLENGE_ALIAS, appPreferences.encryptedYubiKeyChallenge, appPreferences.initializationVectorYubiKeyChallenge)
  val ksYubiKeyIdentitySecret = KeyStoreItem(YUBIKEY_IDENTITY_SECRET_ALIAS, appPreferences.encryptedYubiKeyIdentitySecret, appPreferences.initializationVectorYubiKeyIdentitySecret)
  val ksYubiKeyPublicKey = KeyStoreItem(YUBIKEY_PUBLIC_KEY_ALIAS, appPreferences.encryptedYubiKeyPublicKey, appPreferences.initializationVectorYubiKeyPublicKey)
  val ksYubiKeyPukHash = KeyStoreItem(YUBIKEY_PUK_HASH_ALIAS, appPreferences.encryptedYubiKeyPukHash, appPreferences.initializationVectorYubiKeyPukHash)
  val ksYubiKeyLockoutState = KeyStoreItem(YUBIKEY_LOCKOUT_STATE_ALIAS, appPreferences.encryptedYubiKeyLockoutState, appPreferences.initializationVectorYubiKeyLockoutState)
  val ksYubiKeyWrappedDMK = KeyStoreItem(YUBIKEY_WRAPPED_DMK_ALIAS, appPreferences.encryptedYubiKeyWrappedDMK, appPreferences.initializationVectorYubiKeyWrappedDMK)
  val ksYubiKeyEphemeralPub = KeyStoreItem(YUBIKEY_EPHEMERAL_PUB_ALIAS, appPreferences.encryptedYubiKeyEphemeralPub, appPreferences.initializationVectorYubiKeyEphemeralPub)
  val ksYubiKeyHkdfSalt = KeyStoreItem(YUBIKEY_HKDF_SALT_ALIAS, appPreferences.encryptedYubiKeyHkdfSalt, appPreferences.initializationVectorYubiKeyHkdfSalt)

  class KeyStoreItem(private val alias: String, val passphrase: SharedPreference<String?>, val initVector: SharedPreference<String?>) {
    fun get(): String? {
      return cryptor.decryptData(
        passphrase.get()?.toByteArrayFromBase64ForPassphrase() ?: return null,
        initVector.get()?.toByteArrayFromBase64ForPassphrase() ?: return null,
        alias,
      )
    }

    fun set(key: String) {
      val data = cryptor.encryptText(key, alias)
      passphrase.set(data.first.toBase64StringForPassphrase())
      initVector.set(data.second.toBase64StringForPassphrase())
    }

    fun remove() {
      cryptor.deleteKey(alias)
      passphrase.set(null)
      initVector.set(null)
    }
  }

  fun hasAtLeastOneDatabase(rootDir: String): Boolean =
    File(rootDir + File.separator + chatDatabaseFileName).exists() || File(rootDir + File.separator + agentDatabaseFileName).exists()

  fun hasOnlyOneDatabase(rootDir: String): Boolean =
    File(rootDir + File.separator + chatDatabaseFileName).exists() != File(rootDir + File.separator + agentDatabaseFileName).exists()

  fun useDatabaseKey(): String {
    var dbKey = ""
    val useKeychain = appPreferences.storeDBPassphrase.get()
    val useYubiKey = appPreferences.useYubiKeyForDB.get()
    
    if (useYubiKey) {
      // For YubiKey mode, return empty string to trigger the unlock screen
      // The stored key will only be used AFTER YubiKey PIN verification in DatabaseErrorView
      // This ensures the user must tap their YubiKey and enter PIN on every app restart
      dbKey = ""
    } else if (useKeychain) {
      if (!hasAtLeastOneDatabase(dataDir.absolutePath)) {
        dbKey = randomDatabasePassword()
        ksDatabasePassword.set(dbKey)
        appPreferences.initialRandomDBPassphrase.set(true)
      } else {
        dbKey = ksDatabasePassword.get() ?: ""
      }
    } else if (appPlatform.isDesktop && !hasAtLeastOneDatabase(dataDir.absolutePath)) {
      // In case of database was deleted by hand
      dbKey = randomDatabasePassword()
      ksDatabasePassword.set(dbKey)
      appPreferences.initialRandomDBPassphrase.set(true)
      appPreferences.storeDBPassphrase.set(true)
    }
    return dbKey
  }

  fun randomDatabasePassword(): String {
    val s = ByteArray(32)
    SecureRandom().nextBytes(s)
    return s.toBase64StringForPassphrase().replace("\n", "")
  }
}

@Serializable
sealed class DBMigrationResult {
  @Serializable @SerialName("ok") object OK: DBMigrationResult()
  @Serializable @SerialName("invalidConfirmation") object InvalidConfirmation: DBMigrationResult()
  @Serializable @SerialName("errorNotADatabase") data class ErrorNotADatabase(val dbFile: String): DBMigrationResult()
  @Serializable @SerialName("errorMigration") data class ErrorMigration(val dbFile: String, val migrationError: MigrationError): DBMigrationResult()
  @Serializable @SerialName("errorSQL") data class ErrorSQL(val dbFile: String, val migrationSQLError: String): DBMigrationResult()
  @Serializable @SerialName("errorKeychain") object ErrorKeychain: DBMigrationResult()
  @Serializable @SerialName("unknown") data class Unknown(val json: String): DBMigrationResult()
}

enum class MigrationConfirmation(val value: String) {
  YesUp("yesUp"),
  YesUpDown ("yesUpDown"),
  Error("error")
}

fun defaultMigrationConfirmation(appPrefs: AppPreferences): MigrationConfirmation =
  if (appPrefs.developerTools.get() && appPrefs.confirmDBUpgrades.get()) MigrationConfirmation.Error else MigrationConfirmation.YesUp

@Serializable
sealed class MigrationError {
  @Serializable @SerialName("upgrade") class Upgrade(val upMigrations: List<UpMigration>): MigrationError()
  @Serializable @SerialName("downgrade") class Downgrade(val downMigrations: List<String>): MigrationError()
  @Serializable @SerialName("migrationError") class Error(val mtrError: MTRError): MigrationError()
}

@Serializable
data class UpMigration(
  val upName: String,
  // val withDown: Boolean
)

fun downMigrationWarnings(downMigrations: List<String>): List<String> {
  val warnings = listOf(
    "20260222_chat_relays" to MR.strings.down_migration_warning_chat_relays
  )
  return warnings.mapNotNull { (key, res) ->
    if (downMigrations.contains(key)) generalGetString(res) else null
  }
}

@Serializable
sealed class MTRError {
  @Serializable @SerialName("noDown") class NoDown(val dbMigrations: List<String>): MTRError()
  @Serializable @SerialName("different") class Different(val appMigration: String, val dbMigration: String): MTRError()
}
