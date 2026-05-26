package chat.simplex.common.views.wallet

import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Singleton OkHttpClient for ALL wallet/swap/blockchain HTTP traffic.
 *
 * - Certificate pinning with dummy pins to extract real hashes
 * - Timeouts tuned for blockchain RPC
 * - HTTPS enforced via network_security_config.xml
 * - Generic User-Agent to avoid fingerprinting
 *
 * HOW TO EXTRACT REAL CERTIFICATE PINS:
 *   1. Build and run the app with ENABLE_CERT_PINNING = true (current state)
 *   2. Open Logcat and filter for "CertPin" or "Certificate pinning failure"
 *   3. OkHttp will log the REAL sha256 hashes for each host
 *   4. Replace the DUMMY_PIN values below with the real hashes
 *   5. Include at least 2 pins per host (leaf + intermediate) for rotation safety
 */
object SecureHttpClient {

    private const val TAG = "SecureHttpClient"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Dummy pin that will intentionally fail — OkHttp prints the real pins in the error
    private const val DUMMY_PIN = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    val client: OkHttpClient by lazy { buildClient() }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(ProductionConfig.RPC_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(ProductionConfig.RPC_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (ProductionConfig.ENABLE_CERT_PINNING) {

            // Leaf + intermediate pins per host for rotation safety.
            // Extracted 2026-03-16 via OkHttp pin-failure logging.
            val PUBLICNODE_LEAF   = "sha256/u4NIAmduP+2xQ2UwL2BMzFsvJD8YxcPzxw6flUy17UI="
            val PUBLICNODE_INTER  = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val PUBLICNODE_ROOT   = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val ANKR_LEAF         = "sha256/LzqRFppp98SE8LNv5ZlVeUHkujEaaEglSYIhEduyZ4A="
            val ANKR_INTER        = "sha256/yDu9og255NN5GEf+Bwa9rTrqFQ0EydZ0r1FCh9TdAW4="
            val ANKR_ROOT         = "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="

            val NEAR_LEAF         = "sha256/2F6ZE2V70jC7wpG2ottblOAz4k8SqccghN0R7zhzBaM="
            val NEAR_INTER        = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val NEAR_ROOT         = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            // ── Batch 2: extracted 2026-03-16 ───────────────────────────
            val ONERPC_LEAF       = "sha256/NmP7vvXXZhrZa4llfbgKsqpyQgzRg3awGSvS8v0MhNI="
            val ONERPC_INTER      = "sha256/AlSQhgtJirc8ahLyekmtX+Iw+v46yPYRLJt9Cq1GlB0="
            val ONERPC_ROOT       = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

            val BINANCE_LEAF      = "sha256/wfeytuLQ3h1LeCjgrU2DSJ6Mi4hjdRBpC7Ye7bEjse8="
            val BINANCE_INTER     = "sha256/vxRon/El5KuI4vx5ey1DgmsYmRY0nDd5Cg4GfJ8S+bg="
            val BINANCE_ROOT      = "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="

            val POLYGON_RPC_LEAF  = "sha256/TwAubb85gTVAS/jtMcCxUDwi5OV1tTstetZf9LfweO4="
            val POLYGON_RPC_INTER = "sha256/yDu9og255NN5GEf+Bwa9rTrqFQ0EydZ0r1FCh9TdAW4="
            val POLYGON_RPC_ROOT  = "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="

            val BASE_ORG_LEAF     = "sha256/NvwNjhaHhYRP4vbVCXW67U0IWNBC+uJk1COQr/iZO2E="
            val BASE_ORG_INTER    = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val BASE_ORG_ROOT     = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val OPTIMISM_LEAF     = "sha256/O19TUDK7eGwG5heWJqcfMTwdNdU9O7R4UdbRdC+HqyM="
            val OPTIMISM_INTER    = "sha256/OdSlmQD9NWJh4EbcOHBxkhygPwNSwA9Q91eounfbcoE="
            val OPTIMISM_ROOT     = "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="

            val AVAX_LEAF         = "sha256/wq5cD0T4z99KGeug8cWwvId4IbVy2WPXm4dk9yHvpwk="
            val AVAX_INTER        = "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU="
            val AVAX_ROOT         = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

            val ARBITRUM_LEAF     = "sha256/ovSN23HL0cCapJd8lk+xPkNXPfZySzukjaBtzehbLkM="
            val ARBITRUM_INTER    = "sha256/iFvwVyJSxnQdyaUvUERIf+8qk7gRze3612JMwoO3zdU="
            val ARBITRUM_ROOT     = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

            val SOLANA_LEAF       = "sha256/M+N3aXR0PrxxH5QnXaDfKw9i6uXnxx7q+YXO1in0t8M="
            val SOLANA_INTER      = "sha256/3fLLVjRIWnCqDqIETU2OcnMP7EzmN/Z3Q/jQ8cIaAoc="
            val SOLANA_ROOT       = "sha256/ICGRfpgmOUXIWcQ/HXPLQTkFPEFPoDyjvH7ohhQpjzs="

            // ── Batch 3: extracted 2026-03-16 ───────────────────────────
            val LLAMARPC_LEAF     = "sha256/tOae2oiUmwqoIKFJQLfwFrIrv6Ksh6fZK4qMbxz3N3I="
            val LLAMARPC_INTER    = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val LLAMARPC_ROOT     = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val TRONGRID_LEAF     = "sha256/15Onho3gSd6oscENQMBN1AifMZr7tUWX8eh8nfIkDiQ="
            val TRONGRID_INTER    = "sha256/18tkPyr2nckv4fgo0dhAkaUtJ2hu2831xlO2SKhq8dg="
            val TRONGRID_ROOT     = "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="

            val DOGECHAIN_LEAF    = "sha256/ymUOsfewk53Je01AZ4sPJ1DD9gz9PffMQjs4fDX5aQQ="
            val DOGECHAIN_INTER   = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val DOGECHAIN_ROOT    = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val XRPL_LEAF         = "sha256/9LMwgwy+QudmPI3cnvknRRxZIlq23PzFStwVdhCyGWc="
            val XRPL_INTER        = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val XRPL_ROOT         = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val LTC_LEAF          = "sha256/JPfG3w2oTy8R9AuL5PLDtNzwJJOjhYjosz4DyRs+NjM="
            val LTC_INTER         = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val LTC_ROOT          = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val COINGECKO_LEAF    = "sha256/1ObC8vlFrplRJ03DXfyaS0+4SzNEbDoCgfGyCFQ2zOM="
            val COINGECKO_INTER   = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val COINGECKO_ROOT    = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            // ── Batch 4: Swap APIs extracted 2026-03-16 ─────────────────
            val CHANGENOW_LEAF    = "sha256/uR4pNgocANPVGtEI24fwD1QcxKPR2a3MipJM6CX/9dg="
            val CHANGENOW_INTER   = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val CHANGENOW_ROOT    = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val THORNODE_LEAF     = "sha256/jN2ST5LaabeiTs7nbA9y1I76vLAM2slYXoFKRTWf43k="
            val THORNODE_INTER    = "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="
            val THORNODE_ROOT     = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

            val KYBERSWAP_LEAF    = "sha256/pXI1Qe9VhxAcGpNcfPsIDiEGtM8DYC0f7Fc6dZVs6nk="
            val KYBERSWAP_INTER   = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val KYBERSWAP_ROOT    = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val DEFUSE_LEAF       = "sha256/8y2Ov2jKzxV8WlShT62KoDHP3Mm9u1sfn9iloRgYsQU="
            val DEFUSE_INTER      = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
            val DEFUSE_ROOT       = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

            val PARASWAP_LEAF     = "sha256/xT2IPoBunOVghWaQvVpSBg0XpKAQgs3tDoEAVYqErKA="
            val PARASWAP_INTER    = "sha256/G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c="
            val PARASWAP_ROOT     = "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="

            val pinner = CertificatePinner.Builder()
                // ══════════════════════════════════════════════════════════
                //  PINNED — real hashes extracted 2026-03-16
                // ══════════════════════════════════════════════════════════

                // ── publicnode.com (all EVM chains share the same cert) ──
                .add("ethereum-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                .add("bsc-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                .add("polygon-bor-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                .add("arbitrum-one-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                .add("optimism-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                .add("avalanche-c-chain-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                .add("base-rpc.publicnode.com", PUBLICNODE_LEAF, PUBLICNODE_INTER, PUBLICNODE_ROOT)
                // ── ankr.com ────────────────────────────────────────────
                .add("rpc.ankr.com", ANKR_LEAF, ANKR_INTER, ANKR_ROOT)
                // ── NEAR RPC ────────────────────────────────────────────
                .add("rpc.mainnet.near.org", NEAR_LEAF, NEAR_INTER, NEAR_ROOT)
                // ── 1rpc.io (backup for 7 EVM chains) ───────────────────
                .add("1rpc.io", ONERPC_LEAF, ONERPC_INTER, ONERPC_ROOT)
                // ── Binance BSC dataseed ────────────────────────────────
                .add("bsc-dataseed1.binance.org", BINANCE_LEAF, BINANCE_INTER, BINANCE_ROOT)
                // ── polygon-rpc.com ─────────────────────────────────────
                .add("polygon-rpc.com", POLYGON_RPC_LEAF, POLYGON_RPC_INTER, POLYGON_RPC_ROOT)
                // ── mainnet.base.org ────────────────────────────────────
                .add("mainnet.base.org", BASE_ORG_LEAF, BASE_ORG_INTER, BASE_ORG_ROOT)
                // ── mainnet.optimism.io ─────────────────────────────────
                .add("mainnet.optimism.io", OPTIMISM_LEAF, OPTIMISM_INTER, OPTIMISM_ROOT)
                // ── api.avax.network ────────────────────────────────────
                .add("api.avax.network", AVAX_LEAF, AVAX_INTER, AVAX_ROOT)
                // ── arb1.arbitrum.io ────────────────────────────────────
                .add("arb1.arbitrum.io", ARBITRUM_LEAF, ARBITRUM_INTER, ARBITRUM_ROOT)
                // ── Solana RPC (CRITICAL — TX broadcast) ────────────────
                .add("api.mainnet-beta.solana.com", SOLANA_LEAF, SOLANA_INTER, SOLANA_ROOT)

                // ── eth.llamarpc.com ─────────────────────────────────────
                .add("eth.llamarpc.com", LLAMARPC_LEAF, LLAMARPC_INTER, LLAMARPC_ROOT)
                // ── Tron RPC (CRITICAL — TX broadcast) ──────────────────
                .add("api.trongrid.io", TRONGRID_LEAF, TRONGRID_INTER, TRONGRID_ROOT)
                // ── XRP, LTC, DOGE ──────────────────────────────────────
                .add("xrplcluster.com", XRPL_LEAF, XRPL_INTER, XRPL_ROOT)
                .add("litecoinspace.org", LTC_LEAF, LTC_INTER, LTC_ROOT)
                .add("dogechain.info", DOGECHAIN_LEAF, DOGECHAIN_INTER, DOGECHAIN_ROOT)
                // ── Price API ───────────────────────────────────────────
                .add("api.coingecko.com", COINGECKO_LEAF, COINGECKO_INTER, COINGECKO_ROOT)

                // ══════════════════════════════════════════════════════════
                //  DUMMY PINS — not yet triggered, need app interaction
                // ══════════════════════════════════════════════════════════

                // ── Swap / DEX APIs (pinned 2026-03-16) ─────────────────
                .add("api.changenow.io", CHANGENOW_LEAF, CHANGENOW_INTER, CHANGENOW_ROOT)
                .add("thornode.ninerealms.com", THORNODE_LEAF, THORNODE_INTER, THORNODE_ROOT)
                .add("apiv5.paraswap.io", PARASWAP_LEAF, PARASWAP_INTER, PARASWAP_ROOT)
                .add("aggregator-api.kyberswap.com", KYBERSWAP_LEAF, KYBERSWAP_INTER, KYBERSWAP_ROOT)
                .add("solver-relay.chaindefuser.com", DEFUSE_LEAF, DEFUSE_INTER, DEFUSE_ROOT)
                // ── Dexgram Subscription API (no pin yet — extract from Logcat, then add real hashes) ──
                // .add("prod-internalapi.dexgram.app", DUMMY_PIN)
                // ── Dexgram Vault DB (cloud backup) — extract real hashes from Logcat ──
                // .add("prod-vaultdb.dexgram.app", DUMMY_PIN)
                // ── Still DUMMY ─────────────────────────────────────────
                .add("api.chainflip.io", DUMMY_PIN)
                .add("api.1inch.dev", DUMMY_PIN)
                // ── Notifications ───────────────────────────────────────
                .add("ntfy.sh", DUMMY_PIN)
                // ── Explorer APIs ───────────────────────────────────────
                .add("api.nearblocks.io", DUMMY_PIN)
                .add("api.etherscan.io", DUMMY_PIN)
                .add("api.bscscan.com", DUMMY_PIN)
                .add("api.polygonscan.com", DUMMY_PIN)
                .add("api.arbiscan.io", DUMMY_PIN)
                .add("api-optimistic.etherscan.io", DUMMY_PIN)
                .add("api.snowtrace.io", DUMMY_PIN)
                .add("api.basescan.org", DUMMY_PIN)
                .add("eth.blockscout.com", DUMMY_PIN)
                .add("polygon.blockscout.com", DUMMY_PIN)
                .add("arbitrum.blockscout.com", DUMMY_PIN)
                .add("optimism.blockscout.com", DUMMY_PIN)
                .add("base.blockscout.com", DUMMY_PIN)
                .add("api.routescan.io", DUMMY_PIN)
                .build()
            builder.certificatePinner(pinner)
        }

        return builder.build()
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET
    // ═══════════════════════════════════════════════════════════════

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
            for ((key, value) in headers) {
                if (value.isNotBlank()) reqBuilder.header(key, value)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
            Log.e(TAG, "CertPin FAILURE for GET $url — copy hashes from this error:\n${e.message}")
            null
        } catch (_: Exception) { null }
    }

    fun getWithTimeout(url: String, timeoutMs: Int): String? {
        return try {
            val tempClient = client.newBuilder()
                .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            tempClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
            Log.e(TAG, "CertPin FAILURE for GET $url — copy hashes from this error:\n${e.message}")
            null
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  POST
    // ═══════════════════════════════════════════════════════════════

    fun postJson(url: String, body: String): String? {
        return postJsonWithHeaders(url, body, emptyMap())
    }

    fun postJsonWithHeaders(url: String, body: String, headers: Map<String, String>): String? {
        return postJsonFull(url, body, headers).first
    }

    /**
     * POST JSON and return both the success body and error body.
     * @return Pair(successBody, errorBody) — exactly one is non-null on completion.
     */
    fun postJsonFull(url: String, body: String, headers: Map<String, String> = emptyMap()): Pair<String?, String?> {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
            for ((key, value) in headers) {
                if (value.isNotBlank()) reqBuilder.header(key, value)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(response.body?.string(), null)
                } else {
                    val errorBody = try { response.body?.string() } catch (_: Exception) { null }
                    Pair(null, errorBody)
                }
            }
        } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
            Log.e(TAG, "CertPin FAILURE for POST $url — copy hashes from this error:\n${e.message}")
            Pair(null, e.message)
        } catch (e: Exception) {
            Pair(null, e.message)
        }
    }
}
