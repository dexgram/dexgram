package chat.simplex.common.views.wallet

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Multi-chain BIP44/BIP84 key derivation from a single BIP39 seed.
 * One mnemonic -> unique address on every supported blockchain.
 *
 * SECURITY: All sensitive byte arrays are zeroed on [clear]. The seed never
 * leaves this object; only addresses and the Web3j [Credentials] wrapper are
 * exposed. Private-key hex is generated on-demand and callers should zero
 * the returned CharArray / String reference as soon as possible.
 */
object ChainKeyDeriver {
    @Volatile private var masterSeed: ByteArray? = null

    private var cachedEvmCredentials: Credentials? = null
    private val cachedAddresses = mutableMapOf<BlockchainNetwork, String>()
    private val cachedKeyPairs = mutableMapOf<Int, Bip32ECKeyPair>()
    private var cachedSolanaKeys: Pair<ByteArray, ByteArray>? = null // (privateKey, publicKey)
    private var cachedNearKeys: Pair<ByteArray, ByteArray>? = null   // (privateKey, publicKey)

    fun initialize(mnemonic: String) {
        clear()
        val mnemonicCode = Mnemonics.MnemonicCode(mnemonic)
        masterSeed = mnemonicCode.toSeed()
    }

    fun isInitialized(): Boolean = masterSeed != null

    /**
     * Securely wipe all key material from memory.
     * Byte arrays are overwritten with zeros before being released.
     */
    fun clear() {
        masterSeed?.let { java.util.Arrays.fill(it, 0.toByte()) }
        masterSeed = null

        cachedSolanaKeys?.let { (priv, pub) ->
            java.util.Arrays.fill(priv, 0.toByte())
            java.util.Arrays.fill(pub, 0.toByte())
        }
        cachedSolanaKeys = null

        cachedNearKeys?.let { (priv, pub) ->
            java.util.Arrays.fill(priv, 0.toByte())
            java.util.Arrays.fill(pub, 0.toByte())
        }
        cachedNearKeys = null

        cachedEvmCredentials = null
        cachedAddresses.clear()
        cachedKeyPairs.clear()
    }

    fun getEvmCredentials(): Credentials {
        cachedEvmCredentials?.let { return it }
        val seed = requireSeed()
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(44 or H, 60 or H, 0 or H, 0, 0)
        val derived = Bip32ECKeyPair.deriveKeyPair(master, path)
        val creds = Credentials.create(derived)
        cachedEvmCredentials = creds
        return creds
    }

    fun getAddress(network: BlockchainNetwork): String {
        cachedAddresses[network]?.let { return it }
        val address = when (network) {
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.BINANCE_SMART_CHAIN,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.ARBITRUM,
            BlockchainNetwork.OPTIMISM,
            BlockchainNetwork.AVALANCHE,
            BlockchainNetwork.BASE -> Keys.toChecksumAddress(getEvmCredentials().address)

            BlockchainNetwork.BITCOIN -> deriveBitcoinAddress()
            BlockchainNetwork.LITECOIN -> deriveLitecoinAddress()
            BlockchainNetwork.DOGECOIN -> deriveDogecoinAddress()
            BlockchainNetwork.TRON -> deriveTronAddress()
            BlockchainNetwork.SOLANA -> deriveSolanaAddress()
            BlockchainNetwork.RIPPLE -> deriveXrpAddress()
            BlockchainNetwork.CARDANO -> deriveCardanoPlaceholder()
            BlockchainNetwork.NEAR -> deriveNearAddress()
        }
        cachedAddresses[network] = address
        return address
    }

    /**
     * Get private key as BigInteger for secp256k1-based chains (EVM, Tron, XRP, etc.).
     * Callers should null-out their reference after signing.
     */
    fun getPrivateKeyBigInt(network: BlockchainNetwork): BigInteger? {
        return when {
            network.isEvm -> getEvmCredentials().ecKeyPair.privateKey
            network == BlockchainNetwork.BITCOIN -> deriveSecp256k1Key(84, 0).privateKey
            network == BlockchainNetwork.LITECOIN -> deriveSecp256k1Key(84, 2).privateKey
            network == BlockchainNetwork.DOGECOIN -> deriveSecp256k1Key(44, 3).privateKey
            network == BlockchainNetwork.TRON -> deriveSecp256k1Key(44, 195).privateKey
            network == BlockchainNetwork.RIPPLE -> deriveSecp256k1Key(44, 144).privateKey
            else -> null
        }
    }

    /**
     * Get hex-encoded private key for a network (for transaction signing).
     * Prefer [getPrivateKeyBigInt] for secp256k1 chains to avoid an immutable String.
     */
    fun getPrivateKeyHex(network: BlockchainNetwork): String? {
        return when {
            network.isEvm -> Numeric.toHexStringNoPrefixZeroPadded(getEvmCredentials().ecKeyPair.privateKey, 64)
            network == BlockchainNetwork.BITCOIN -> keyPairHex(84, 0)
            network == BlockchainNetwork.LITECOIN -> keyPairHex(84, 2)
            network == BlockchainNetwork.DOGECOIN -> keyPairHex(44, 3)
            network == BlockchainNetwork.TRON -> keyPairHex(44, 195)
            network == BlockchainNetwork.RIPPLE -> keyPairHex(44, 144)
            network == BlockchainNetwork.SOLANA -> {
                val (privKey, _) = getSolanaKeypair()
                Numeric.toHexStringNoPrefix(privKey)
            }
            network == BlockchainNetwork.NEAR -> {
                val (privKey, _) = getNearKeypair()
                Numeric.toHexStringNoPrefix(privKey)
            }
            else -> null
        }
    }

    // ── Secp256k1 derivation (BTC, LTC, DOGE, TRX, XRP) ────────────

    private fun deriveSecp256k1Key(purpose: Int, coinType: Int): Bip32ECKeyPair {
        val cacheKey = purpose * 10000 + coinType
        cachedKeyPairs[cacheKey]?.let { return it }
        val seed = requireSeed()
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(purpose or H, coinType or H, 0 or H, 0, 0)
        val derived = Bip32ECKeyPair.deriveKeyPair(master, path)
        cachedKeyPairs[cacheKey] = derived
        return derived
    }

    private fun keyPairHex(purpose: Int, coinType: Int): String {
        val kp = deriveSecp256k1Key(purpose, coinType)
        return Numeric.toHexStringNoPrefixZeroPadded(kp.privateKey, 64)
    }

    // ── Bitcoin: BIP84 m/84'/0'/0'/0/0 -> P2WPKH bech32 ────────────

    private fun deriveBitcoinAddress(): String {
        val keyPair = deriveSecp256k1Key(84, 0)
        val compressed = getCompressedPublicKey(keyPair)
        val pubKeyHash = hash160(compressed)
        return Bech32Encoder.encode("bc", 0, pubKeyHash)
    }

    // ── Litecoin: BIP84 m/84'/2'/0'/0/0 -> bech32 (ltc1...) ────────

    private fun deriveLitecoinAddress(): String {
        val keyPair = deriveSecp256k1Key(84, 2)
        val compressed = getCompressedPublicKey(keyPair)
        val pubKeyHash = hash160(compressed)
        return Bech32Encoder.encode("ltc", 0, pubKeyHash)
    }

    // ── Dogecoin: BIP44 m/44'/3'/0'/0/0 -> P2PKH (D...) ────────────

    private fun deriveDogecoinAddress(): String {
        val keyPair = deriveSecp256k1Key(44, 3)
        val compressed = getCompressedPublicKey(keyPair)
        val pubKeyHash = hash160(compressed)
        return Base58Encoder.encodeChecked(0x1E, pubKeyHash)
    }

    // ── Tron: BIP44 m/44'/195'/0'/0/0 -> Base58Check (T...) ────────

    private fun deriveTronAddress(): String {
        val keyPair = deriveSecp256k1Key(44, 195)
        val pubKeyBytes = Numeric.toBytesPadded(keyPair.publicKey, 64)
        val keccakHash = Hash.sha3(pubKeyBytes)
        val addressBytes = keccakHash.copyOfRange(12, 32)
        return Base58Encoder.encodeChecked(0x41, addressBytes)
    }

    // ── XRP: BIP44 m/44'/144'/0'/0/0 -> Base58Check with XRP alphabet

    private fun deriveXrpAddress(): String {
        val keyPair = deriveSecp256k1Key(44, 144)
        val compressed = getCompressedPublicKey(keyPair)
        val accountId = hash160(compressed)
        return Base58Encoder.encodeChecked(0x00, accountId, Base58Encoder.XRP_ALPHABET)
    }

    // ── Solana: SLIP-0010 Ed25519 m/44'/501'/0'/0' ──────────────────

    fun getSolanaKeypair(): Pair<ByteArray, ByteArray> {
        cachedSolanaKeys?.let { return it }
        val seed = requireSeed()
        val keys = deriveSolanaEd25519(seed)
        cachedSolanaKeys = keys
        return keys
    }

    private fun deriveSolanaAddress(): String {
        val (_, publicKey) = getSolanaKeypair()
        return Base58Encoder.encode(publicKey)
    }

    private fun deriveSolanaEd25519(seed: ByteArray): Pair<ByteArray, ByteArray> {
        var mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("ed25519 seed".toByteArray(Charsets.UTF_8), "HmacSHA512"))
        var result = mac.doFinal(seed)
        var il = result.copyOfRange(0, 32)
        var ir = result.copyOfRange(32, 64)

        val pathIndices = intArrayOf(
            44 or 0x80000000.toInt(),
            501 or 0x80000000.toInt(),
            0 or 0x80000000.toInt(),
            0 or 0x80000000.toInt()
        )
        for (index in pathIndices) {
            val data = ByteArray(37)
            data[0] = 0x00
            System.arraycopy(il, 0, data, 1, 32)
            data[33] = ((index ushr 24) and 0xFF).toByte()
            data[34] = ((index ushr 16) and 0xFF).toByte()
            data[35] = ((index ushr 8) and 0xFF).toByte()
            data[36] = (index and 0xFF).toByte()

            val prevIl = il
            val prevIr = ir
            mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(ir, "HmacSHA512"))
            result = mac.doFinal(data)
            il = result.copyOfRange(0, 32)
            ir = result.copyOfRange(32, 64)

            // Wipe intermediate key material
            java.util.Arrays.fill(data, 0.toByte())
            java.util.Arrays.fill(prevIl, 0.toByte())
            java.util.Arrays.fill(prevIr, 0.toByte())
            java.util.Arrays.fill(result, 0.toByte())
        }

        val privateKeyParams = Ed25519PrivateKeyParameters(il, 0)
        val publicKey = privateKeyParams.generatePublicKey().encoded
        return Pair(il, publicKey)
    }

    // ── NEAR: SLIP-0010 Ed25519 m/44'/397'/0'/0'/0' → implicit account (hex pubkey)

    fun getNearKeypair(): Pair<ByteArray, ByteArray> {
        cachedNearKeys?.let { return it }
        val seed = requireSeed()
        val keys = deriveNearEd25519(seed)
        cachedNearKeys = keys
        return keys
    }

    private fun deriveNearAddress(): String {
        val (_, publicKey) = getNearKeypair()
        return Numeric.toHexStringNoPrefix(publicKey)
    }

    private fun deriveNearEd25519(seed: ByteArray): Pair<ByteArray, ByteArray> {
        var mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("ed25519 seed".toByteArray(Charsets.UTF_8), "HmacSHA512"))
        var result = mac.doFinal(seed)
        var il = result.copyOfRange(0, 32)
        var ir = result.copyOfRange(32, 64)

        val pathIndices = intArrayOf(
            44 or 0x80000000.toInt(),
            397 or 0x80000000.toInt(),
            0 or 0x80000000.toInt(),
            0 or 0x80000000.toInt(),
            0 or 0x80000000.toInt()
        )
        for (index in pathIndices) {
            val data = ByteArray(37)
            data[0] = 0x00
            System.arraycopy(il, 0, data, 1, 32)
            data[33] = ((index ushr 24) and 0xFF).toByte()
            data[34] = ((index ushr 16) and 0xFF).toByte()
            data[35] = ((index ushr 8) and 0xFF).toByte()
            data[36] = (index and 0xFF).toByte()

            val prevIl = il
            val prevIr = ir
            mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(ir, "HmacSHA512"))
            result = mac.doFinal(data)
            il = result.copyOfRange(0, 32)
            ir = result.copyOfRange(32, 64)

            java.util.Arrays.fill(data, 0.toByte())
            java.util.Arrays.fill(prevIl, 0.toByte())
            java.util.Arrays.fill(prevIr, 0.toByte())
            java.util.Arrays.fill(result, 0.toByte())
        }

        val privateKeyParams = Ed25519PrivateKeyParameters(il, 0)
        val publicKey = privateKeyParams.generatePublicKey().encoded
        return Pair(il, publicKey)
    }

    // ── Cardano: placeholder (complex Ed25519-BIP32 derivation) ─────

    private fun deriveCardanoPlaceholder(): String = ""

    // ═══════════════════════════════════════════════════════════════════
    //  Crypto utilities
    // ═══════════════════════════════════════════════════════════════════

    private fun getCompressedPublicKey(keyPair: Bip32ECKeyPair): ByteArray {
        val pubKeyBytes = Numeric.toBytesPadded(keyPair.publicKey, 64)
        val x = pubKeyBytes.copyOfRange(0, 32)
        val y = pubKeyBytes.copyOfRange(32, 64)
        val prefix = if (y[31].toInt() and 1 == 0) 0x02.toByte() else 0x03.toByte()
        return byteArrayOf(prefix) + x
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun ripemd160(data: ByteArray): ByteArray {
        val digest = RIPEMD160Digest()
        digest.update(data, 0, data.size)
        val out = ByteArray(20)
        digest.doFinal(out, 0)
        return out
    }

    private fun hash160(data: ByteArray): ByteArray = ripemd160(sha256(data))

    private fun requireSeed(): ByteArray =
        masterSeed ?: throw IllegalStateException("ChainKeyDeriver not initialized")

    private const val H = Bip32ECKeyPair.HARDENED_BIT
}

// ═══════════════════════════════════════════════════════════════════════
//  Base58 encoding (Bitcoin & XRP alphabets)
// ═══════════════════════════════════════════════════════════════════════

internal object Base58Encoder {
    const val BITCOIN_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    const val XRP_ALPHABET = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"

    fun encode(data: ByteArray, alphabet: String = BITCOIN_ALPHABET): String {
        if (data.isEmpty()) return ""
        var leadingZeros = 0
        for (b in data) { if (b.toInt() == 0) leadingZeros++ else break }

        val sb = StringBuilder()
        var num = BigInteger(1, data)
        val base = BigInteger.valueOf(58)
        while (num > BigInteger.ZERO) {
            val (div, rem) = num.divideAndRemainder(base)
            sb.append(alphabet[rem.toInt()])
            num = div
        }
        repeat(leadingZeros) { sb.append(alphabet[0]) }
        return sb.reverse().toString()
    }

    fun encodeChecked(version: Int, payload: ByteArray, alphabet: String = BITCOIN_ALPHABET): String {
        val versioned = ByteArray(1 + payload.size)
        versioned[0] = version.toByte()
        System.arraycopy(payload, 0, versioned, 1, payload.size)
        val checksum = doubleSha256(versioned)
        val full = ByteArray(versioned.size + 4)
        System.arraycopy(versioned, 0, full, 0, versioned.size)
        System.arraycopy(checksum, 0, full, versioned.size, 4)
        return encode(full, alphabet)
    }

    private fun doubleSha256(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Bech32 encoding (BIP173 — Bitcoin native segwit, Litecoin)
// ═══════════════════════════════════════════════════════════════════════

internal object Bech32Encoder {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = longArrayOf(0x3b6a57b2L, 0x26508e6dL, 0x1ea119faL, 0x3d4233ddL, 0x2a1462b3L)

    fun encode(hrp: String, witnessVersion: Int, witnessProgram: ByteArray): String {
        val converted = convertBits(witnessProgram, 8, 5, true)
        val data = ByteArray(1 + converted.size)
        data[0] = witnessVersion.toByte()
        System.arraycopy(converted, 0, data, 1, converted.size)
        val checksum = createChecksum(hrp, data)
        val sb = StringBuilder(hrp).append('1')
        for (b in data) sb.append(CHARSET[b.toInt() and 0xFF])
        for (b in checksum) sb.append(CHARSET[b.toInt() and 0xFF])
        return sb.toString()
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxV = (1 shl toBits) - 1
        for (b in data) {
            acc = (acc shl fromBits) or (b.toInt() and 0xFF)
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxV).toByte())
            }
        }
        if (pad && bits > 0) {
            result.add(((acc shl (toBits - bits)) and maxV).toByte())
        }
        return result.toByteArray()
    }

    private fun polymod(values: ByteArray): Long {
        var chk = 1L
        for (v in values) {
            val b = chk shr 25
            chk = ((chk and 0x1FFFFFFL) shl 5) xor (v.toLong() and 0xFF)
            for (i in 0..4) {
                if (((b shr i) and 1L) != 0L) chk = chk xor GEN[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val result = ByteArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = (hrp[i].code shr 5).toByte()
            result[hrp.length + 1 + i] = (hrp[i].code and 31).toByte()
        }
        result[hrp.length] = 0
        return result
    }

    private fun createChecksum(hrp: String, data: ByteArray): ByteArray {
        val expanded = hrpExpand(hrp)
        val values = ByteArray(expanded.size + data.size + 6)
        System.arraycopy(expanded, 0, values, 0, expanded.size)
        System.arraycopy(data, 0, values, expanded.size, data.size)
        val pm = polymod(values) xor 1L
        val result = ByteArray(6)
        for (i in 0..5) result[i] = ((pm shr (5 * (5 - i))) and 31).toByte()
        return result
    }
}
