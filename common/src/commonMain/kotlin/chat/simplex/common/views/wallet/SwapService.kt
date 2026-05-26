package chat.simplex.common.views.wallet

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Production swap service supporting:
 *
 * ── Same-chain EVM ───────────────────────────────────────────────
 *   On-chain DEX routers (Uniswap V2-compatible getAmountsOut)
 *   DEX aggregator REST APIs (1inch, 0x, Paraswap, OpenOcean, KyberSwap)
 *
 * ── Non-EVM ──────────────────────────────────────────────────────
 *   Solana  → Jupiter aggregator API
 *   Tron    → SunSwap V2 API
 *
 * ── Cross-chain ──────────────────────────────────────────────────
 *   THORChain  – decentralised cross-chain liquidity
 *   Chainflip  – BTC↔ETH/USDC cross-chain
 *   deBridge   – SOL/EVM/TRON cross-chain (no API key)
 *   Rango      – multi-chain aggregator
 *   Symbiosis  – 20+ chains, no API key, returns calldata
 *   Relay      – EVM cross-chain bridge, free
 *   Squid      – 100+ chains via Axelar (needs Integrator ID)
 *   ChangeNOW  – universal exchange (1000+ assets, all chains)
 */
object SwapService {
    private val json = Json { ignoreUnknownKeys = true }

    // ═══════════════════════════════════════════════════════════════
    //  THORChain configuration
    // ═══════════════════════════════════════════════════════════════

    private const val THORNODE_BASE = "https://thornode.ninerealms.com"
    private const val THORCHAIN_DECIMALS = 8

    private val thorchainAssets = mapOf(
        "BTC" to "BTC.BTC",
        "ETH" to "ETH.ETH",
        "SOL" to "SOL.SOL",
        "SOL_SOLANA" to "SOL.SOL",
        "BNB" to "BSC.BNB",
        "BNB_BINANCE_SMART_CHAIN" to "BSC.BNB",
        "ETH_ETHEREUM" to "ETH.ETH",
        "ETH_ARBITRUM" to "ETH.ETH",
        "ETH_OPTIMISM" to "ETH.ETH",
        "ETH_BASE" to "ETH.ETH",
        "AVAX" to "AVAX.AVAX",
        "AVAX_AVALANCHE" to "AVAX.AVAX",
        "DOGE" to "DOGE.DOGE",
        "DOGE_DOGECOIN" to "DOGE.DOGE",
        "LTC" to "LTC.LTC",
        "LTC_LITECOIN" to "LTC.LTC",
        "BTC_BITCOIN" to "BTC.BTC",
        "ATOM" to "GAIA.ATOM",
        "RUNE" to "THOR.RUNE",
        "USDT_ETHEREUM" to "ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7",
        "USDT_ETH" to "ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7",
        "USDC_ETHEREUM" to "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
        "USDC_ETH" to "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
        "USDT_BINANCE_SMART_CHAIN" to "BSC.USDT-0X55D398326F99059FF775485246999027B3197955",
        "USDT_BSC" to "BSC.USDT-0X55D398326F99059FF775485246999027B3197955",
        "USDC_AVALANCHE" to "AVAX.USDC-0XB97EF9EF8734C71904D8002F8B6BC66DD9C48A6E",
        "USDC_AVAX" to "AVAX.USDC-0XB97EF9EF8734C71904D8002F8B6BC66DD9C48A6E",
        "DAI_ETHEREUM" to "ETH.DAI-0X6B175474E89094C44DA98B954EEDEAC495271D0F",
        "DAI_ETH" to "ETH.DAI-0X6B175474E89094C44DA98B954EEDEAC495271D0F",
        "WBTC_ETHEREUM" to "ETH.WBTC-0X2260FAC5E5542A773AA44FBCFEDF7C193BC2C599",
        "WBTC_ETH" to "ETH.WBTC-0X2260FAC5E5542A773AA44FBCFEDF7C193BC2C599",
        "NEAR" to "NEAR.NEAR",
        "NEAR_NEAR" to "NEAR.NEAR"
    )

    private fun thorchainAssetId(symbol: String, network: BlockchainNetwork): String? {
        val key = "${symbol.uppercase()}_${network.name}"
        return thorchainAssets[key] ?: thorchainAssets[symbol.uppercase()]
    }

    // ═══════════════════════════════════════════════════════════════
    //  Chainflip configuration (cross-chain)
    // ═══════════════════════════════════════════════════════════════

    private const val CHAINFLIP_API = "https://api.chainflip.io/v1"

    private val chainflipAssets = mapOf(
        "BTC" to "btc.btc",
        "BTC_BITCOIN" to "btc.btc",
        "ETH" to "eth.eth",
        "ETH_ETHEREUM" to "eth.eth",
        "ETH_ARBITRUM" to "arb.eth",
        "USDC_ETHEREUM" to "eth.usdc",
        "USDC_ETH" to "eth.usdc",
        "USDC_ARBITRUM" to "arb.usdc",
        "USDT_ETHEREUM" to "eth.usdt",
        "USDT_ETH" to "eth.usdt",
        "DOT" to "dot.dot",
        "FLIP" to "eth.flip",
        "SOL" to "sol.sol",
        "SOL_SOLANA" to "sol.sol"
    )

    private fun chainflipAssetId(symbol: String, network: BlockchainNetwork): String? {
        val key = "${symbol.uppercase()}_${network.name}"
        return chainflipAssets[key] ?: chainflipAssets[symbol.uppercase()]
    }

    // ═══════════════════════════════════════════════════════════════
    //  deBridge DLN (decentralized cross-chain, no API key, SOL+EVM+TRON)
    //  Free, permissionless DEX aggregator: https://docs.debridge.com
    // ═══════════════════════════════════════════════════════════════

    private const val DEBRIDGE_API = "https://dln.debridge.finance"

    private val debridgeChainIds = mapOf(
        BlockchainNetwork.ETHEREUM to "1",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "56",
        BlockchainNetwork.POLYGON to "137",
        BlockchainNetwork.ARBITRUM to "42161",
        BlockchainNetwork.OPTIMISM to "10",
        BlockchainNetwork.AVALANCHE to "43114",
        BlockchainNetwork.BASE to "8453",
        BlockchainNetwork.SOLANA to "7565164",
        BlockchainNetwork.TRON to "100000026",
        BlockchainNetwork.NEAR to "100000018"
    )

    private fun getDebridgeNativeAddress(network: BlockchainNetwork): String = when (network) {
        BlockchainNetwork.SOLANA -> "11111111111111111111111111111111"
        BlockchainNetwork.TRON -> "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        else -> "0x0000000000000000000000000000000000000000"
    }

    private fun resolveDebridgeTokenAddress(network: BlockchainNetwork, symbol: String): String? {
        val addr = resolveTokenAddress(network, symbol) ?: return null
        if (addr == NATIVE_TOKEN_ADDRESS || addr == "NATIVE") return getDebridgeNativeAddress(network)
        return addr
    }

    private fun getDebridgeQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String,
        fromAddress: String? = null
    ): SwapQuote? {
        val srcChainId = debridgeChainIds[srcNetwork] ?: return null
        val dstChainId = debridgeChainIds[dstNetwork] ?: return null
        val fromAddr = resolveDebridgeTokenAddress(srcNetwork, fromToken) ?: return null
        val toAddr = resolveDebridgeTokenAddress(dstNetwork, toToken) ?: return null
        val fromDecimals = getTokenDecimals(fromToken, srcNetwork)
        val amountRaw = convertToWei(amount, fromDecimals)
        if (amountRaw == "0") return null

        val isCrossChain = srcNetwork != dstNetwork

        val recipientParam = if (!fromAddress.isNullOrBlank()) "&dstChainTokenOutRecipient=$fromAddress" else ""
        val url = if (isCrossChain) {
            "$DEBRIDGE_API/v1.0/dln/order/create-tx" +
                    "?srcChainId=$srcChainId&srcChainTokenIn=$fromAddr" +
                    "&srcChainTokenInAmount=$amountRaw" +
                    "&dstChainId=$dstChainId&dstChainTokenOut=$toAddr" +
                    "&dstChainTokenOutAmount=auto" +
                    "&prependOperatingExpenses=true" +
                    recipientParam
        } else {
            "$DEBRIDGE_API/v1.0/chain/estimation" +
                    "?chainId=$srcChainId&tokenIn=$fromAddr" +
                    "&tokenInAmount=$amountRaw&tokenOut=$toAddr" +
                    "&slippage=auto"
        }

        val response = fetchUrlWithTimeout(url, 10_000) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject

            val toDecimals = getTokenDecimals(toToken, dstNetwork)
            val fromAmt = amount.toDoubleOrNull() ?: 1.0

            if (isCrossChain) {
                val estimation = root["estimation"]?.jsonObject ?: return null
                val dstOut = estimation["dstChainTokenOut"]?.jsonObject ?: return null
                val outAmountRaw = dstOut["recommendedAmount"]?.jsonPrimitive?.content
                    ?: dstOut["amount"]?.jsonPrimitive?.content ?: return null
                val outBd = parseWeiToHumanReadable(outAmountRaw, toDecimals)
                val outAmount = outBd.toDouble()

                val tx = root["tx"]?.jsonObject
                val txTo = tx?.get("to")?.jsonPrimitive?.contentOrNull
                val txData = tx?.get("data")?.jsonPrimitive?.contentOrNull
                val txValue = tx?.get("value")?.jsonPrimitive?.contentOrNull

                SwapQuote(
                    fromToken = fromToken, toToken = toToken, fromAmount = amount,
                    toAmount = outBd.stripTrailingZeros().toPlainString(),
                    exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                    priceImpact = 0.3, estimatedGas = "0",
                    network = srcNetwork, provider = "deBridge",
                    expiresAt = System.currentTimeMillis() + 30_000,
                    destNetwork = dstNetwork,
                    txTo = txTo, txData = txData, txValue = txValue
                )
            } else {
                val estimation = root["estimation"]?.jsonObject ?: return null
                val tokenOut = estimation["tokenOut"]?.jsonObject ?: return null
                val outAmountRaw = tokenOut["amount"]?.jsonPrimitive?.content ?: return null
                val outBd = parseWeiToHumanReadable(outAmountRaw, toDecimals)
                val outAmount = outBd.toDouble()
                SwapQuote(
                    fromToken = fromToken, toToken = toToken, fromAmount = amount,
                    toAmount = outBd.stripTrailingZeros().toPlainString(),
                    exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                    priceImpact = estimation["slippage"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.3,
                    estimatedGas = "0",
                    network = srcNetwork, provider = "deBridge",
                    expiresAt = System.currentTimeMillis() + 30_000,
                    destNetwork = null
                )
            }
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ChangeNOW (universal cross-chain exchange, 1000+ assets)
    //  Covers ALL chain pairs including ADA, XRP, and exotic routes
    // ═══════════════════════════════════════════════════════════════

    private const val CHANGENOW_API = "https://api.changenow.io/v2"

    // Loaded from BuildConfig — set CHANGENOW_API_KEY in local.properties (not committed to VCS)
    private val CHANGENOW_API_KEY: String by lazy {
        try {
            val clazz = Class.forName("chat.simplex.app.BuildConfig")
            clazz.getField("CHANGENOW_API_KEY").get(null) as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private val changeNowTickers = mapOf(
        "BTC" to "btc", "ETH" to "eth", "BNB" to "bnb", "SOL" to "sol",
        "TRX" to "trx", "LTC" to "ltc", "DOGE" to "doge", "XRP" to "xrp",
        "ADA" to "ada", "AVAX" to "avax", "MATIC" to "matic", "POL" to "matic",
        "USDT" to "usdt", "USDC" to "usdc", "DAI" to "dai",
        "ATOM" to "atom", "DOT" to "dot", "LINK" to "link", "UNI" to "uni",
        "AAVE" to "aave", "ARB" to "arb", "OP" to "op",
        "SHIB" to "shib", "PEPE" to "pepe", "WBTC" to "wbtc",
        "RUNE" to "rune", "NEAR" to "near", "FIL" to "fil",
        "INJ" to "inj", "MKR" to "mkr", "WETH" to "weth",
        "BUSD" to "busd", "TUSD" to "tusd", "FLOKI" to "floki",
        "APE" to "ape", "CRV" to "crv", "GRT" to "grt",
        "SNX" to "snx", "COMP" to "comp", "LDO" to "ldo",
        "FTM" to "ftm", "MANA" to "mana", "SAND" to "sand"
    )

    private val changeNowNetworks = mapOf(
        BlockchainNetwork.BITCOIN to "btc",
        BlockchainNetwork.ETHEREUM to "eth",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "bsc",
        BlockchainNetwork.SOLANA to "sol",
        BlockchainNetwork.TRON to "trx",
        BlockchainNetwork.POLYGON to "matic",
        BlockchainNetwork.AVALANCHE to "cchain",
        BlockchainNetwork.ARBITRUM to "arbitrum",
        BlockchainNetwork.OPTIMISM to "op",
        BlockchainNetwork.BASE to "base",
        BlockchainNetwork.LITECOIN to "ltc",
        BlockchainNetwork.DOGECOIN to "doge",
        BlockchainNetwork.RIPPLE to "xrp",
        BlockchainNetwork.CARDANO to "ada",
        BlockchainNetwork.NEAR to "near"
    )

    private fun getChangeNowQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String
    ): SwapQuote? {
        if (CHANGENOW_API_KEY.isBlank()) return null
        val fromTicker = changeNowTickers[fromToken.uppercase()] ?: fromToken.lowercase()
        val toTicker = changeNowTickers[toToken.uppercase()] ?: toToken.lowercase()
        val fromNet = changeNowNetworks[srcNetwork] ?: return null
        val toNet = changeNowNetworks[dstNetwork] ?: return null

        val url = "$CHANGENOW_API/exchange/estimated-amount" +
                "?fromCurrency=$fromTicker&toCurrency=$toTicker" +
                "&fromAmount=$amount&fromNetwork=$fromNet&toNetwork=$toNet" +
                "&flow=standard&type=direct"

        val response = fetchUrlWithHeaders(url, mapOf(
            "x-changenow-api-key" to CHANGENOW_API_KEY
        )) ?: return null
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val estimatedAmount = obj["toAmount"]?.jsonPrimitive?.content
                ?: obj["estimatedAmount"]?.jsonPrimitive?.content ?: return null
            val outBd = java.math.BigDecimal(estimatedAmount)
            val outAmount = outBd.toDouble()
            val fromAmt = java.math.BigDecimal(amount).toDouble()
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = "0",
                network = srcNetwork, provider = "ChangeNOW",
                expiresAt = System.currentTimeMillis() + 120_000,
                destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null
            )
        } catch (_: Exception) { null }
    }

    data class ExchangeTransaction(
        val exchangeId: String,
        val depositAddress: String,
        val depositExtraId: String? = null,
        val expectedFromAmount: String,
        val expectedToAmount: String,
        val status: String
    )

    fun createChangeNowExchange(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String,
        recipientAddress: String, refundAddress: String? = null
    ): Result<ExchangeTransaction> {
        if (CHANGENOW_API_KEY.isBlank()) return Result.failure(Exception("ChangeNOW API key not configured"))
        val fromTicker = changeNowTickers[fromToken.uppercase()] ?: fromToken.lowercase()
        val toTicker = changeNowTickers[toToken.uppercase()] ?: toToken.lowercase()
        val fromNet = changeNowNetworks[srcNetwork]
            ?: return Result.failure(Exception("${srcNetwork.displayName} not supported by ChangeNOW"))
        val toNet = changeNowNetworks[dstNetwork]
            ?: return Result.failure(Exception("${dstNetwork.displayName} not supported by ChangeNOW"))

        val body = buildJsonObject {
            put("fromCurrency", fromTicker)
            put("toCurrency", toTicker)
            put("fromAmount", amount)
            put("fromNetwork", fromNet)
            put("toNetwork", toNet)
            put("address", recipientAddress)
            put("flow", "standard")
            put("type", "direct")
            if (refundAddress != null) put("refundAddress", refundAddress)
        }.toString()

        val response = httpPostWithErrorBody(
            "$CHANGENOW_API/exchange",
            body,
            mapOf("x-changenow-api-key" to CHANGENOW_API_KEY)
        )

        if (response.first == null) {
            val errorBody = response.second
            if (errorBody != null) {
                val reason = try {
                    val errObj = json.parseToJsonElement(errorBody).jsonObject
                    errObj["message"]?.jsonPrimitive?.contentOrNull
                        ?: errObj["error"]?.jsonPrimitive?.contentOrNull
                        ?: errorBody
                } catch (_: Exception) { errorBody }
                return Result.failure(Exception(reason))
            }
            return Result.failure(Exception("Network error — could not reach ChangeNOW"))
        }

        return try {
            val obj = json.parseToJsonElement(response.first!!).jsonObject
            if (obj.containsKey("error") || obj.containsKey("message")) {
                val msg = obj["message"]?.jsonPrimitive?.contentOrNull
                    ?: obj["error"]?.jsonPrimitive?.contentOrNull
                    ?: "Unknown exchange error"
                return Result.failure(Exception(msg))
            }
            val id = obj["id"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Exchange created but no ID returned"))
            val payinAddr = obj["payinAddress"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Exchange created but no deposit address returned"))
            Result.success(ExchangeTransaction(
                exchangeId = id,
                depositAddress = payinAddr,
                depositExtraId = obj["payinExtraId"]?.jsonPrimitive?.contentOrNull,
                expectedFromAmount = obj["fromAmount"]?.jsonPrimitive?.content ?: amount,
                expectedToAmount = obj["toAmount"]?.jsonPrimitive?.content ?: "0",
                status = obj["status"]?.jsonPrimitive?.content ?: "new"
            ))
        } catch (e: Exception) { Result.failure(Exception("Failed to parse exchange response: ${e.message}")) }
    }

    data class ExchangeStatus(
        val status: String,
        val amountFrom: String? = null,
        val amountTo: String? = null,
        val payinHash: String? = null,
        val payoutHash: String? = null,
        val depositAddress: String? = null,
        val message: String? = null
    ) {
        val isTerminal: Boolean get() = status in listOf("finished", "failed", "refunded", "expired")
        val isSuccess: Boolean get() = status == "finished"
        val failureReason: String? get() = when {
            status == "failed" -> message ?: "Exchange failed"
            status == "refunded" -> message ?: "Exchange refunded"
            status == "expired" -> "Exchange expired — deposit not received in time"
            else -> null
        }
        val displayStatus: String get() = when (status) {
            "new" -> "Awaiting Deposit"
            "waiting" -> "Awaiting Deposit"
            "confirming" -> "Confirming Deposit"
            "exchanging" -> "Exchanging"
            "sending" -> "Sending to You"
            "finished" -> "Completed"
            "failed" -> "Failed"
            "refunded" -> "Refunded"
            "expired" -> "Expired"
            "verifying" -> "Verifying"
            else -> status.replaceFirstChar { it.uppercase() }
        }
    }

    fun getChangeNowExchangeStatus(exchangeId: String): ExchangeStatus? {
        if (CHANGENOW_API_KEY.isBlank()) return null
        val url = "$CHANGENOW_API/exchange/by-id?id=$exchangeId"
        val response = fetchUrlWithHeaders(url, mapOf(
            "x-changenow-api-key" to CHANGENOW_API_KEY
        )) ?: return null
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            ExchangeStatus(
                status = obj["status"]?.jsonPrimitive?.content ?: "unknown",
                amountFrom = obj["amountFrom"]?.jsonPrimitive?.contentOrNull
                    ?: obj["expectedAmountFrom"]?.jsonPrimitive?.contentOrNull,
                amountTo = obj["amountTo"]?.jsonPrimitive?.contentOrNull
                    ?: obj["expectedAmountTo"]?.jsonPrimitive?.contentOrNull,
                payinHash = obj["payinHash"]?.jsonPrimitive?.contentOrNull,
                payoutHash = obj["payoutHash"]?.jsonPrimitive?.contentOrNull,
                depositAddress = obj["payinAddress"]?.jsonPrimitive?.contentOrNull,
                message = obj["message"]?.jsonPrimitive?.contentOrNull
                    ?: obj["error"]?.jsonPrimitive?.contentOrNull
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Rango Exchange (DEX aggregator, free tier without key)
    // ═══════════════════════════════════════════════════════════════

    private const val RANGO_API = "https://api.rango.exchange/basic"

    private val rangoChainIds = mapOf(
        BlockchainNetwork.ETHEREUM to "ETH",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "BSC",
        BlockchainNetwork.POLYGON to "POLYGON",
        BlockchainNetwork.ARBITRUM to "ARBITRUM",
        BlockchainNetwork.OPTIMISM to "OPTIMISM",
        BlockchainNetwork.AVALANCHE to "AVAX_CCHAIN",
        BlockchainNetwork.BASE to "BASE",
        BlockchainNetwork.SOLANA to "SOLANA",
        BlockchainNetwork.TRON to "TRON",
        BlockchainNetwork.BITCOIN to "BTC",
        BlockchainNetwork.LITECOIN to "LTC",
        BlockchainNetwork.DOGECOIN to "DOGE",
        BlockchainNetwork.NEAR to "NEAR"
    )

    // ═══════════════════════════════════════════════════════════════
    //  DEX aggregator API endpoints
    // ═══════════════════════════════════════════════════════════════

    private val chainIds = mapOf(
        BlockchainNetwork.ETHEREUM to 1,
        BlockchainNetwork.BINANCE_SMART_CHAIN to 56,
        BlockchainNetwork.POLYGON to 137,
        BlockchainNetwork.ARBITRUM to 42161,
        BlockchainNetwork.OPTIMISM to 10,
        BlockchainNetwork.AVALANCHE to 43114,
        BlockchainNetwork.BASE to 8453
    )

    // ═══════════════════════════════════════════════════════════════
    //  On-chain DEX routers (Uniswap V2 compatible)
    // ═══════════════════════════════════════════════════════════════

    private const val NATIVE_TOKEN_ADDRESS = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"

    private fun rpcUrl(network: BlockchainNetwork): String? =
        BlockchainService.getRpcUrl(network)

    private data class DexRouter(val name: String, val address: String)

    private val dexRouters = mapOf(
        BlockchainNetwork.ETHEREUM to listOf(
            DexRouter("Uniswap V2", "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"),
            DexRouter("SushiSwap", "0xd9e1cE17f2641f24aE83637ab66a2cca9C378B9F"),
            DexRouter("Shibaswap", "0x03f7724180AA6b939894B5Ca4314783B0b36b329"),
        ),
        BlockchainNetwork.BINANCE_SMART_CHAIN to listOf(
            DexRouter("PancakeSwap", "0x10ED43C718714eb63d5aA57B78B54704E256024E"),
            DexRouter("BiSwap", "0x3a6d8cA21D1CF76F653A67577FA0D27453350dD8"),
            DexRouter("ApeSwap", "0xcF0feBd3f17CEf5b47b0cD257aCf6025c5BFf3b7"),
            DexRouter("MDEX", "0x7DAe51BD3E3376B8c7c4900E9107f12Be3AF1bA8"),
            DexRouter("BabySwap", "0x325E343f1dE602396E256B67eFd1F61C3A6B38Bd"),
        ),
        BlockchainNetwork.POLYGON to listOf(
            DexRouter("QuickSwap", "0xa5E0829CaCEd8fFDD4De3c43696c57F7D7A678ff"),
            DexRouter("SushiSwap", "0x1b02dA8Cb0d097eB8D57A175b88c7D8b47997506"),
            DexRouter("ApeSwap", "0xC0788A3aD43d79aa53B09c2EaCc313A787d1d607"),
        ),
        BlockchainNetwork.ARBITRUM to listOf(
            DexRouter("SushiSwap", "0x1b02dA8Cb0d097eB8D57A175b88c7D8b47997506"),
            DexRouter("Camelot", "0xc873fEcbd354f5A56E00E710B90EF4201db2448d"),
            DexRouter("Zyberswap", "0x16e71B13fE6079B4312063F7E81F76d165Ad32Ad"),
        ),
        BlockchainNetwork.OPTIMISM to listOf(
            DexRouter("Velodrome", "0x9c12939390052919af3155f41bf4160fd3666a6f"),
            DexRouter("Beethoven X", "0xBA12222222228d8Ba445958a75a0704d566BF2C8"),
        ),
        BlockchainNetwork.AVALANCHE to listOf(
            DexRouter("TraderJoe", "0x60aE616a2155Ee3d9A68541Ba4544862310933d4"),
            DexRouter("Pangolin", "0xE54Ca86531e17Ef3616d22Ca28b0D458b6C89106"),
            DexRouter("KyberSwap Classic", "0x5649B4DD00780e99Bab7Abb4A3d581Ea1aEB23D0"),
        ),
        BlockchainNetwork.BASE to listOf(
            DexRouter("BaseSwap", "0x8cFe327CEc66d1C090Dd72bd0FF11d690C33a2Eb"),
            DexRouter("Aerodrome", "0xcF77a3Ba9A5CA399B7c97c74d54e5b1Beb874E43"),
            DexRouter("SwapBased", "0xaaa3b1F1bd7BCc97fD1917c18ADE665C5D31F066"),
        ),
    )

    private val wrappedNativeAddresses = mapOf(
        BlockchainNetwork.ETHEREUM to "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c",
        BlockchainNetwork.POLYGON to "0x0d500B1d8E8eF31E21C99d1Db9A6444d3ADf1270",
        BlockchainNetwork.ARBITRUM to "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1",
        BlockchainNetwork.OPTIMISM to "0x4200000000000000000000000000000000000006",
        BlockchainNetwork.AVALANCHE to "0xB31f66AA3C1e785363F0875A1B74E27b85FD66c7",
        BlockchainNetwork.BASE to "0x4200000000000000000000000000000000000006"
    )

    // ═══════════════════════════════════════════════════════════════
    //  Expanded token addresses per chain
    // ═══════════════════════════════════════════════════════════════

    private val tokenAddresses = mapOf(
        BlockchainNetwork.ETHEREUM to mapOf(
            "ETH" to NATIVE_TOKEN_ADDRESS,
            "WETH" to "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
            "USDT" to "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            "USDC" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            "DAI" to "0x6B175474E89094C44Da98b954EedeAC495271d0F",
            "WBTC" to "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599",
            "UNI" to "0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984",
            "LINK" to "0x514910771AF9Ca656af840dff83E8264EcF986CA",
            "AAVE" to "0x7Fc66500c84A76Ad7e9c93437bFc5Ac33E2DDaE9",
            "MKR" to "0x9f8F72aA9304c8B593d555F12eF6589cC3A579A2",
            "SHIB" to "0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE",
            "PEPE" to "0x6982508145454Ce325dDbE47a25d4ec3d2311933",
            "LDO" to "0x5A98FcBEA516Cf06857215779Fd812CA3beF1B32",
            "CRV" to "0xD533a949740bb3306d119CC777fa900bA034cd52",
            "COMP" to "0xc00e94Cb662C3520282E6f5717214004A7f26888",
            "SNX" to "0xC011a73ee8576Fb46F5E1c5751cA3B9Fe0af2a6F",
            "APE" to "0x4d224452801ACEd8B2F0aebE155379bb5D594381",
            "FET" to "0xaea46A60368A7bD060eec7DF8CBa43b7EF41Ad85",
            "GRT" to "0xc944E90C64B2c07662A292be6244BDf05Cda44a7",
            "RNDR" to "0x6De037ef9aD2725EB40118Bb1702EBb27e4Aeb24",
            "DYDX" to "0x92D6C1e31e14520e676a687F0a93788B716BEff5",
            "ENS" to "0xC18360217D8F7Ab5e7c516566761Ea12Ce7F9D72",
            "1INCH" to "0x111111111117dC0aa78b770fA6A738034120C302",
            "SUSHI" to "0x6B3595068778DD592e39A122f4f5a5cF09C90fE2",
            "FRAX" to "0x853d955aCEf822Db058eb8505911ED77F175b99e",
            "RPL" to "0xD33526068D116cE69F19A9ee46F0bd304F21A51f",
            "IMX" to "0xF57e7e7C23978C3cAEC3C3548E3D615c346e79fF",
            "BLUR" to "0x5283D291DBCF85356A21bA090E6db59121208b44",
            "STX" to "0xfc98e825A2264D890F5F583e61F9c4F4015b9E98",
            "FLOKI" to "0xcf0C122c6b73ff809C693DB761e7BaeBe62b6a2E",
            "SAND" to "0x3845badAde8e6dFF049820680d1F14bD3903a5d0",
            "MANA" to "0x0F5D2fB29fb7d3CFeE444a200298f468908cC942",
            "AXS" to "0xBB0E17EF65F82Ab018d8EDd776e8DD940327B28b",
            "BAL" to "0xba100000625a3754423978a60c9317c58a424e3D",
            "YFI" to "0x0bc529c00C6401aEF6D220BE8C6Ea1667F6Ad93e",
            "TUSD" to "0x0000000000085d4780B73119b644AE5ecd22b376",
            "stETH" to "0xae7ab96520DE3A18E5e111B5EaAb095312D7fE84",
            "rETH" to "0xae78736Cd615f374D3085123A210448E74Fc6393",
            "cbETH" to "0xBe9895146f7AF43049ca1c1AE358B0541Ea49704",
            "PENDLE" to "0x808507121B80c02388fAd14726482e061B8da827",
            "WLD" to "0x163f8C2467924be0ae7B5347228CABF260318753",
            "INJ" to "0xe28b3B32B6c345A34Ff64674606124Dd5Aceca30",
            "ENA" to "0x57e114B691Db790C35207b2e685D4A43181e6061",
            "ONDO" to "0xfAbA6f8e4a5E8Ab82F62fe7C39859FA577269BE3",
            "ETHFI" to "0xFe0c30065B384F05761f15d0CC899D4F9F9Cc0eB"
        ),
        BlockchainNetwork.BINANCE_SMART_CHAIN to mapOf(
            "BNB" to NATIVE_TOKEN_ADDRESS,
            "WBNB" to "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c",
            "USDT" to "0x55d398326f99059fF775485246999027B3197955",
            "USDC" to "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d",
            "BUSD" to "0xe9e7CEA3DedcA5984780Bafc599bD69ADd087D56",
            "CAKE" to "0x0E09FaBB73Bd3Ade0a17ECC321fD13a19e81cE82",
            "DAI" to "0x1AF3F329e8BE154074D8769D1FFa4eE058B1DBc3",
            "ETH" to "0x2170Ed0880ac9A755fd29B2688956BD959F933F8",
            "BTCB" to "0x7130d2A12B9BCbFAe4f2634d864A1Ee1Ce3Ead9c",
            "XRP" to "0x1D2F0da169ceB9fC7B3144628dB156f3F6c60dBE",
            "ADA" to "0x3EE2200Efb3400fAbB9AacF31297cBdD1d435D47",
            "DOGE" to "0xbA2aE424d960c26247Dd6c32edC70B295c744C43",
            "DOT" to "0x7083609fce4d1d8Dc0C979AAb8c869Ea2C873402",
            "LTC" to "0x4338665CBB7B2485A8855A139b75D5e34AB0DB94",
            "TWT" to "0x4B0F1812e5Df2A09796481Ff14017e6005508003",
            "FLOKI" to "0xfb5B838b6cfEEdC2873aB27866079AC55363D37E",
            "LINK" to "0xF8A0BF9cF54Bb92F17374d9e9A321E6a111a51bD",
            "UNI" to "0xBf5140A22578168FD562DCcF235E5D43A02ce9B1",
            "AAVE" to "0xfb6115445Bff7b52FeB98650C87f44907E58f802",
            "AVAX" to "0x1CE0c2827e2eF14D5C4f29a091d735A204794041",
            "MATIC" to "0xCC42724C6683B7E57334c4E856f4c9965ED682bD",
            "ATOM" to "0x0Eb3a705fc54725037CC9e008bDede697f62F335",
            "NEAR" to "0x1Fa4a73a3F0133f0025378af00236f3aBDEE84D0",
            "FIL" to "0x0D8Ce2A99Bb6e3B7Db580eD848240e4a0F9aE153",
            "INJ" to "0xa2B726B1145A4773F68CBA5Cd2c328e19885d109",
            "SHIB" to "0x2859e4544C4bB03966803b044A93563Bd2D0DD4D",
            "PEPE" to "0x25d887Ce7a35172C62FeBFD67a1856F20FaEbB00",
            "1INCH" to "0x111111111117dC0aa78b770fA6A738034120C302",
            "SUSHI" to "0x947950BcC74888a40Ffa2593C5798F11Fc9124C4",
            "TUSD" to "0x14016E85a25aeb13065688cAFB43044C2ef86784",
            "SFP" to "0xD41FDb03Ba84762dD66a0af1a6C8540FF1ba5dfb",
            "BAKE" to "0xE02dF9e3e622DeBdD69fb838bB799E3F168902c5"
        ),
        BlockchainNetwork.POLYGON to mapOf(
            "MATIC" to NATIVE_TOKEN_ADDRESS,
            "POL" to NATIVE_TOKEN_ADDRESS,
            "WMATIC" to "0x0d500B1d8E8eF31E21C99d1Db9A6444d3ADf1270",
            "USDT" to "0xc2132D05D31c914a87C6611C10748AEb04B58e8F",
            "USDC" to "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
            "WETH" to "0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619",
            "WBTC" to "0x1BFD67037B42Cf73acF2047067bd4F2C47D9BfD6",
            "DAI" to "0x8f3Cf7ad23Cd3CaDbD9735AFf958023239c6A063",
            "AAVE" to "0xD6DF932A45C0f255f85145f286eA0b292B21C90B",
            "LINK" to "0x53E0bca35eC356BD5ddDFebbD1Fc0fD03FaBad39",
            "CRV" to "0x172370d5Cd63279eFa6d502DAB29171933a610AF",
            "BAL" to "0x9a71012B13CA4d3D0Cdc72A177DF3ef03b0E76A3",
            "QUICK" to "0xB5C064F955D8e7F38fE0460C556a72987494eE17",
            "GRT" to "0x5fe2B58c013d7601147DcdD68C143A77499f5531",
            "UNI" to "0xb33EaAd8d922B1083446DC23f610c2567fB5180f",
            "SUSHI" to "0x0b3F868E0BE5597D5DB7fEB59E1CADBb0fdDa50a",
            "SAND" to "0xBbba073C31bF03b8ACf7c28EF0738DeCF3695683",
            "MANA" to "0xA1c57f48F0Deb89f569dFbE6E2B7f46D33606fD4",
            "COMP" to "0x8505b9d2254A7Ae468c0E9dd10Ccea3A837aef5c",
            "SNX" to "0x50B728D8D964fd00C2d0AAD81718b71311feAD3E",
            "1INCH" to "0x9c2C5fd7b07E95EE044DDeba0E97a665F142394f",
            "GHST" to "0x385Eeac5cB85A38A9a07A70c73e0a3271CfB54A7",
            "stMATIC" to "0x3A58a54C066FdC0f2D55FC9C89F0415C92eBf3C4"
        ),
        BlockchainNetwork.ARBITRUM to mapOf(
            "ETH" to NATIVE_TOKEN_ADDRESS,
            "WETH" to "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1",
            "USDT" to "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9",
            "USDC" to "0xaf88d065e77c8cC2239327C5EDb3A432268e5831",
            "ARB" to "0x912CE59144191C1204E64559FE8253a0e49E6548",
            "WBTC" to "0x2f2a2543B76A4166549F7aaB2e75Bef0aefC5B0f",
            "DAI" to "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1",
            "LINK" to "0xf97f4df75117a78c1A5a0DBb814Af92458539FB4",
            "UNI" to "0xFa7F8980b0f1E64A2062791cc3b0871572f1F7f0",
            "GMX" to "0xfc5A1A6EB076a2C7aD06eD22C90d7E710E35ad0a",
            "MAGIC" to "0x539bdE0d7Dbd336b79148AA742883198BBF60342",
            "GRT" to "0x9623063377AD1B27544C965cCd7342f7EA7e88C7",
            "PENDLE" to "0x0c880f6761F1af8d9Aa9C466984b80DAb9a8c9e8",
            "CRV" to "0x11cDb42B0EB46D95f990BeDD4695A6e3fA034978",
            "SUSHI" to "0xd4d42F0b6DEF4CE0383636770eF773390d85c61A",
            "COMP" to "0x354A6dA3fcde098F8389cad84b0182725c6C91dE",
            "BAL" to "0x040d1EdC9569d4Bab2D15287Dc5A4F10F56a56B8",
            "RDNT" to "0x3082CC23568eA640225c2467653dB90e9250AAA0",
            "STG" to "0x6694340fc020c5E6B96567843da2df01b2CE1eb6",
            "FRAX" to "0x17FC002b466eEc40DaE837Fc4bE5c67993ddBd6F",
            "LDO" to "0x13Ad51ed4F1B7e9Dc168d8a00cB3f4dDD85EFA60"
        ),
        BlockchainNetwork.OPTIMISM to mapOf(
            "ETH" to NATIVE_TOKEN_ADDRESS,
            "WETH" to "0x4200000000000000000000000000000000000006",
            "USDT" to "0x94b008aA00579c1307B0EF2c499aD98a8ce58e58",
            "USDC" to "0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85",
            "OP" to "0x4200000000000000000000000000000000000042",
            "DAI" to "0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1",
            "WBTC" to "0x68f180fcCe6836688e9084f035309E29Bf0A2095",
            "LINK" to "0x350a791Bfc2C21F9Ed5d10980Dad2e2638ffa7f6",
            "SNX" to "0x8700dAec35aF8Ff88c16BdF0418774CB3D7599B4",
            "VELO" to "0x9560e827aF36c94D2Ac33a39bCE1Fe78631088Db",
            "UNI" to "0x6fd9d7AD17242c41f7131d257212c54A0e816691",
            "AAVE" to "0x76FB31fb4af56892A25e32cFC43De717950c9278",
            "CRV" to "0x0994206dfE8De6Ec6920FF4D779B0d950605Fb53",
            "SUSHI" to "0x3eaEb77b03dBc0F7bFad46cF3b678C2AAce5a44e",
            "LDO" to "0xFdb794692724153d1488CcdBE0C56c252596735F",
            "FRAX" to "0x2E3D870790dC77A83DD1d18184Acc7439A53f475",
            "rETH" to "0x9Bcef72be871e61ED4fBbc7630889beE758eb81D",
            "WLD" to "0xdC6fF44d5d932Cbd77B52E5612Ba0529DC6226F1",
            "STG" to "0x296F55F8Fb28E498B858d0BcDA06D955B2Cb3f97"
        ),
        BlockchainNetwork.AVALANCHE to mapOf(
            "AVAX" to NATIVE_TOKEN_ADDRESS,
            "WAVAX" to "0xB31f66AA3C1e785363F0875A1B74E27b85FD66c7",
            "USDT" to "0x9702230A8Ea53601f5cD2dc00fDBc13d4dF4A8c7",
            "USDC" to "0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E",
            "DAI" to "0xd586E7F844cEa2F87f50152665BCbc2C279D8d70",
            "WBTC" to "0x50b7545627a5162F82A992c33b87aDc75187B218",
            "WETH" to "0x49D5c2BdFfac6CE2BFdB6640F4F80f226bc10bAB",
            "JOE" to "0x6e84a6216eA6dACC71eE8E6b0a5B7322EEbC0fDd",
            "PNG" to "0x60781C2586D68229fde47564546784ab3fACA982",
            "LINK" to "0x5947BB275c521040051D82396192181b413227A3",
            "AAVE" to "0x63a72806098Bd3D9520cC43356dD78afe5D386D9",
            "SUSHI" to "0x37B608519F91f70F2EeB0e5Ed9AF4061722e4F76",
            "BTC.b" to "0x152b9d0FdC40C096DE345fFcC9B86F0d5a9F8735",
            "GMX" to "0x62edc0692BD897D2295872a9FFCac5425011c661",
            "STG" to "0x2F6F07CDcf3588944Bf4C42aC74ff24bF56e7590"
        ),
        BlockchainNetwork.BASE to mapOf(
            "ETH" to NATIVE_TOKEN_ADDRESS,
            "WETH" to "0x4200000000000000000000000000000000000006",
            "USDC" to "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            "DAI" to "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb",
            "AERO" to "0x940181a94A35A4569E4529A3CDfB74e38FD98631",
            "cbETH" to "0x2Ae3F1Ec7F1F5012CFEab0185bfc7aa3cf0DEc22",
            "BRETT" to "0x532f27101965dd16442E59d40670FaF5eBB142E4",
            "DEGEN" to "0x4ed4E862860beD51a9570b96d89aF5E1B0Efefed",
            "USDbC" to "0xd9aAEc86B65D86f6A7B5B1b0c42FFA531710b6CA",
            "WELL" to "0xA88594D404727625A9437C3f886C7643872296AE",
            "TOSHI" to "0xAC1Bd2486aAf3B5C0fc3Fd868558b082a531B2B4",
            "rETH" to "0xB6fe221Fe9EeF5aBa221c348bA20A1Bf5e73624c",
            "STG" to "0xE3B53AF74a4BF62Ae5511055290838050bf764Df"
        ),
        BlockchainNetwork.SOLANA to mapOf(
            "SOL" to "So11111111111111111111111111111111111111112",
            "USDC" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            "USDT" to "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
            "RAY" to "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R",
            "JTO" to "jtojtomepa8beP8AuQc6eXt5FriJwfFMwQx2v2f9mCL",
            "JUP" to "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",
            "BONK" to "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
            "WIF" to "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm",
            "ORCA" to "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE",
            "MNGO" to "MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac",
            "PYTH" to "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3",
            "W" to "85VBFQZC9TZkfaptBWjvUw7YbZjy52A6mjtPGjstQAmQ",
            "RENDER" to "rndrizKT3MK1iimdxRdWabcF7Zg7AR5T4nud4EkHBof",
            "TENSOR" to "TNSRxcUxoT9xBG3de7PiJyTDYu7kskLqcpddxnEJAS6",
            "MSOL" to "mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So",
            "JITOSOL" to "J1toso1uCk3RLmjorhTtrVwY9HJ7X8V9yYac6Y7kGCPn",
            "INF" to "5oVNBeEEQvYi1cX3ir8Dx5n1P7pdxydbGF2X4TxVusJm",
            "HNT" to "hntyVP6YFm1Hg25TN9WGLqM12b8TQmcknKrdu1oxWux",
            "MOBILE" to "mb1eu7TzEc71KxDpsmsKoucSSuuo6KWC499aWH7RSFjz",
            "MEW" to "MEW1gQWJ3nEXg2qgERiKu7FAFj79PHvQVREQUzScPP5",
            "POPCAT" to "7GCihgDB8fe6KNjn2MYtkzZcRjQy3t9GHdC8uHYmW2hr"
        ),
        BlockchainNetwork.TRON to mapOf(
            "TRX" to "NATIVE",
            "USDT" to "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            "USDC" to "TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8",
            "WTRX" to "TNUC9Qb1rRpS5CbWLmNMxXBjyFoydXjWFR",
            "SUN" to "TSSMHYeV2uE9qYH95DqyoCuWn7k5uBFZdw",
            "JST" to "TCFLL5dx5ZJdKnWuesXxi1VPwjLVmWZZy9",
            "BTT" to "TAFjULxiVgT4qWk6UZwjqwZXTSaGaqnVp4",
            "WIN" to "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7",
            "NFT" to "TFczxzPhnThNSqr5by8tvxsdCFRRz6cPNq",
            "TUSD" to "TUpMhErZL2fhh4sVNULAbNKLokS4GjC1F4",
            "USDJ" to "TMwFHYXLJaRUPeW6421aqXL4ZEzPRFGkGT"
        ),
        BlockchainNetwork.BITCOIN to mapOf(
            "BTC" to "NATIVE"
        ),
        BlockchainNetwork.LITECOIN to mapOf(
            "LTC" to "NATIVE"
        ),
        BlockchainNetwork.DOGECOIN to mapOf(
            "DOGE" to "NATIVE"
        ),
        BlockchainNetwork.RIPPLE to mapOf(
            "XRP" to "NATIVE"
        ),
        BlockchainNetwork.CARDANO to mapOf(
            "ADA" to "NATIVE"
        ),
        BlockchainNetwork.NEAR to mapOf(
            "NEAR" to "NATIVE",
            "wNEAR" to "wrap.near",
            "USDC" to "17208628f84f5d6ad33f0da3bbbeb27ffcb398eac501a31bd6ad2011e36133a1",
            "USDT" to "usdt.tether-token.near",
            "AURORA" to "aaaaaa20d9e0e2461697782ef11675f668207961.factory.bridge.near",
            "SWEAT" to "token.sweat",
            "stNEAR" to "meta-pool.near",
            "LiNEAR" to "linear-protocol.near",
            "REF" to "token.v2.ref-finance.near",
            "BRRR" to "token.burrow.near"
        )
    )

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    suspend fun getQuote(
        network: BlockchainNetwork,
        fromToken: String,
        toToken: String,
        amount: String,
        fromAddress: String
    ): SwapQuote? {
        val all = getAllQuotes(network, fromToken, toToken, amount, fromAddress)
        return all.maxByOrNull { it.exchangeRate }
    }

    /**
     * Query ALL available quote sources in parallel:
     *   - THORChain (cross-chain: BTC, ETH, BNB, SOL, AVAX, DOGE, LTC, ATOM)
     *   - Chainflip (cross-chain: BTC, ETH, SOL, DOT, USDC)
     *   - deBridge DLN (cross-chain: SOL, EVM, TRON — no API key)
     *   - Rango (multi-chain DEX aggregator)
     *   - Li.Fi (EVM + Solana, same-chain and cross-chain)
     *   - Defuse (NEAR-based cross-chain)
     *   - Symbiosis (cross-chain: 20+ chains, no API key)
     *   - Relay (EVM cross-chain bridge, free)
     *   - Squid Router (100+ chains via Axelar, needs Integrator ID)
     *   - DEX aggregator REST APIs (1inch, 0x, Paraswap, OpenOcean, KyberSwap)
     *   - On-chain DEX routers (Uniswap V2 compatible)
     *   - Jupiter (Solana)
     *   - SunSwap (Tron)
     *   - Ref Finance (NEAR)
     *   - ChangeNOW (universal exchange, 1000+ assets, all chains)
     *
     * @param destNetwork  Optional destination network for cross-chain swaps.
     *                     If null, same-chain swap is assumed.
     */
    suspend fun getAllQuotes(
        network: BlockchainNetwork,
        fromToken: String,
        toToken: String,
        amount: String,
        fromAddress: String,
        destNetwork: BlockchainNetwork? = null,
        destinationAddress: String? = null
    ): List<SwapQuote> = withContext(Dispatchers.IO) {
        val isCrossChain = destNetwork != null && destNetwork != network
        val targetNetwork = destNetwork ?: network
        val destAddr = destinationAddress ?: fromAddress

        listOf(
            async { getTokenDecimalsOnChain(fromToken, network) },
            async { getTokenDecimalsOnChain(toToken, targetNetwork) }
        ).awaitAll()

        val results = mutableListOf<Deferred<SwapQuote?>>()

        // ── THORChain (BTC, ETH, SOL, BNB, AVAX, DOGE, LTC, ATOM + tokens) ──
        results.add(async {
            try { getThorChainQuote(network, fromToken, toToken, amount, destAddr, destNetwork) } catch (_: Exception) { null }
        })

        // ── Chainflip (BTC, ETH, SOL, DOT, USDC, USDT) ──
        if (isCrossChain) {
            results.add(async {
                try { getChainflipQuote(network, targetNetwork, fromToken, toToken, amount, destAddr) } catch (_: Exception) { null }
            })
        }

        // ── deBridge DLN (decentralized, SOL + EVM + TRON, no API key) ──
        if (debridgeChainIds.containsKey(network) && debridgeChainIds.containsKey(targetNetwork)) {
            results.add(async {
                try { getDebridgeQuote(network, targetNetwork, fromToken, toToken, amount, destAddr) } catch (_: Exception) { null }
            })
        }

        // ── Rango (DEX aggregator, supports BTC, SOL, TRON, EVM) ──
        results.add(async {
            try {
                val dst = if (isCrossChain) targetNetwork else network
                getRangoQuote(network, dst, fromToken, toToken, amount, fromAddress)
            } catch (_: Exception) { null }
        })

        // ── Li.Fi (EVM chains + Solana, same-chain and cross-chain) ──
        if (lifiChainIds.containsKey(network) && lifiChainIds.containsKey(targetNetwork)) {
            results.add(async {
                try { getLifiQuote(network, fromToken, toToken, amount, fromAddress, destNetwork) } catch (_: Exception) { null }
            })
        }

        // ── Defuse (NEAR-based cross-chain DEX aggregator) ────────
        if (defuseChainIds.containsKey(network) && defuseChainIds.containsKey(targetNetwork)) {
            results.add(async {
                try { getDefuseQuote(network, targetNetwork, fromToken, toToken, amount, destAddr) } catch (_: Exception) { null }
            })
        }

        // ── Same-chain NEAR (Ref Finance) ─────────────────────────
        if (!isCrossChain && network == BlockchainNetwork.NEAR) {
            results.add(async {
                try { getRefFinanceQuote(fromToken, toToken, amount) } catch (_: Exception) { null }
            })
        }

        // ── Same-chain EVM DEX aggregators ───────────────────────
        if (!isCrossChain && network.isEvm) {
            val chainId = chainIds[network]
            if (chainId != null) {
                results.add(async {
                    try { get1inchQuote(chainId, fromToken, toToken, amount, network, fromAddress) } catch (_: Exception) { null }
                })
                results.add(async {
                    try { get0xQuote(chainId, fromToken, toToken, amount, network) } catch (_: Exception) { null }
                })
                results.add(async {
                    try { getParaswapQuote(chainId, fromToken, toToken, amount, network) } catch (_: Exception) { null }
                })
                results.add(async {
                    try { getOpenOceanQuote(chainId, fromToken, toToken, amount, network) } catch (_: Exception) { null }
                })
                results.add(async {
                    try { getKyberSwapQuote(chainId, fromToken, toToken, amount, network) } catch (_: Exception) { null }
                })
            }

            val routers = dexRouters[network] ?: emptyList()
            for (router in routers) {
                results.add(async {
                    try { getDexRouterQuote(network, fromToken, toToken, amount, router) } catch (_: Exception) { null }
                })
            }
        }

        // ── Same-chain Solana (Jupiter) ──────────────────────────
        if (!isCrossChain && network == BlockchainNetwork.SOLANA) {
            results.add(async {
                try { getJupiterQuote(fromToken, toToken, amount) } catch (_: Exception) { null }
            })
        }

        // ── Same-chain Tron (SunSwap) ────────────────────────────
        if (!isCrossChain && network == BlockchainNetwork.TRON) {
            results.add(async {
                try { getSunSwapQuote(fromToken, toToken, amount) } catch (_: Exception) { null }
            })
        }

        // ── Symbiosis (cross-chain, 20+ chains, no API key) ─────────
        if (symbiosisChainIds.containsKey(network) && symbiosisChainIds.containsKey(targetNetwork)) {
            results.add(async {
                try { getSymbiosisQuote(network, targetNetwork, fromToken, toToken, amount, fromAddress, destAddr) } catch (_: Exception) { null }
            })
        }

        // ── Relay (EVM cross-chain bridge, free) ─────────────────────
        if (relayChainIds.containsKey(network) && relayChainIds.containsKey(targetNetwork)) {
            results.add(async {
                try { getRelayQuote(network, targetNetwork, fromToken, toToken, amount, fromAddress) } catch (_: Exception) { null }
            })
        }

        // ── Squid Router (100+ chains, Axelar-powered) ──────────────
        if (SQUID_INTEGRATOR_ID.isNotBlank() && squidChainIds.containsKey(network) && squidChainIds.containsKey(targetNetwork)) {
            results.add(async {
                try { getSquidQuote(network, targetNetwork, fromToken, toToken, amount, destAddr) } catch (_: Exception) { null }
            })
        }

        // ── ChangeNOW (universal exchange, 1000+ assets, all chains) ──
        if (changeNowNetworks.containsKey(network) && changeNowNetworks.containsKey(targetNetwork)) {
            results.add(async {
                try { getChangeNowQuote(network, targetNetwork, fromToken, toToken, amount) } catch (_: Exception) { null }
            })
        }

        val quotes = results.awaitAll().filterNotNull()

        if (quotes.isEmpty()) {
            val fallback = getPriceBasedEstimate(network, targetNetwork, fromToken, toToken, amount)
            if (fallback != null) return@withContext listOf(fallback)
        }

        quotes
    }

    private fun getPriceBasedEstimate(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String
    ): SwapQuote? {
        val fromPrice = WalletPriceService.getPrice(fromToken.uppercase())
        val toPrice = WalletPriceService.getPrice(toToken.uppercase())
        if (fromPrice <= 0 || toPrice <= 0) return null
        val fromAmt = amount.toDoubleOrNull() ?: return null
        if (fromAmt <= 0) return null

        val usdValue = fromAmt * fromPrice
        val toAmt = usdValue / toPrice
        val fee = toAmt * 0.01
        val estimatedOut = toAmt - fee

        return SwapQuote(
            fromToken = fromToken, toToken = toToken, fromAmount = amount,
            toAmount = formatAmount(estimatedOut, getTokenDecimals(toToken, dstNetwork)),
            exchangeRate = if (fromAmt > 0) estimatedOut / fromAmt else 0.0,
            priceImpact = 1.0, estimatedGas = "0",
            network = srcNetwork, provider = "Market Rate (est.)",
            expiresAt = System.currentTimeMillis() + 60_000,
            destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null
        )
    }

    suspend fun executeSwap(
        quote: SwapQuote,
        fromAddress: String,
        slippagePercent: Double = 0.5
    ): Result<SwapResult> = withContext(Dispatchers.IO) {
        try {
            if (quote.provider == "THORChain" && quote.thorchainMemo != null) {
                return@withContext executeThorChainSwap(quote, fromAddress)
            }

            if (quote.txTo != null && quote.txData != null && quote.network.isEvm) {
                val tokenAddr = resolveTokenAddress(quote.network, quote.fromToken)
                val isNative = tokenAddr == null || tokenAddr == NATIVE_TOKEN_ADDRESS
                if (!isNative && tokenAddr != null) {
                    val decimals = getTokenDecimals(quote.fromToken, quote.network)
                    val amountWei = convertToWei(quote.fromAmount, decimals)
                    val approveResult = approveTokenIfNeeded(quote.network, tokenAddr, quote.txTo, amountWei)
                    if (approveResult.isFailure) {
                        return@withContext Result.failure(Exception("Token approval failed: ${approveResult.exceptionOrNull()?.message}"))
                    }
                }

                val txValue = quote.txValue ?: "0"
                val txResult = PlatformWallet.sendRawTransaction(
                    network = quote.network,
                    to = quote.txTo,
                    data = quote.txData,
                    value = txValue,
                    gasLimit = "500000"
                )

                return@withContext txResult.fold(
                    onSuccess = { txHash ->
                        Result.success(SwapResult(
                            txHash = txHash,
                            fromToken = quote.fromToken, toToken = quote.toToken,
                            fromAmount = quote.fromAmount, toAmount = quote.toAmount,
                            network = quote.network, status = SwapStatus.PENDING,
                            crossChainProvider = quote.provider
                        ))
                    },
                    onFailure = { Result.failure(Exception("${quote.provider} transaction failed: ${it.message}")) }
                )
            }

            if (quote.provider.startsWith("Market Rate")) {
                return@withContext Result.failure(Exception("Market Rate estimates cannot be executed directly. Select a real provider quote."))
            }

            if (!quote.network.isEvm) {
                return@withContext Result.failure(Exception("Direct execution only on EVM chains. Use ${quote.provider} dApp for this swap."))
            }

            executeDexSwap(quote, fromAddress, slippagePercent)
        } catch (e: Exception) {
            Result.failure(Exception("Swap error: ${e.message}"))
        }
    }

    fun getSupportedTokens(network: BlockchainNetwork): List<SwapToken> {
        val addresses = tokenAddresses[network] ?: emptyMap()
        return addresses.map { (symbol, address) ->
            SwapToken(
                symbol = symbol,
                name = getTokenName(symbol),
                address = address,
                decimals = getTokenDecimals(symbol, network),
                network = network,
                logoUrl = null
            )
        }
    }

    fun isSwapSupported(network: BlockchainNetwork, fromToken: String, toToken: String): Boolean {
        if (thorchainAssetId(fromToken, network) != null && thorchainAssetId(toToken, network) != null) return true
        val addresses = tokenAddresses[network] ?: return false
        return addresses.containsKey(fromToken.uppercase()) && addresses.containsKey(toToken.uppercase())
    }

    fun isCrossChainSupported(fromNetwork: BlockchainNetwork, toNetwork: BlockchainNetwork, fromToken: String, toToken: String): Boolean {
        if (thorchainAssetId(fromToken, fromNetwork) != null && thorchainAssetId(toToken, toNetwork) != null) return true
        if (chainflipAssetId(fromToken, fromNetwork) != null && chainflipAssetId(toToken, toNetwork) != null) return true
        if (debridgeChainIds.containsKey(fromNetwork) && debridgeChainIds.containsKey(toNetwork)) return true
        if (rangoChainIds.containsKey(fromNetwork) && rangoChainIds.containsKey(toNetwork)) return true
        if (lifiChainIds.containsKey(fromNetwork) && lifiChainIds.containsKey(toNetwork)) return true
        if (defuseChainIds.containsKey(fromNetwork) && defuseChainIds.containsKey(toNetwork)) return true
        if (symbiosisChainIds.containsKey(fromNetwork) && symbiosisChainIds.containsKey(toNetwork)) return true
        if (relayChainIds.containsKey(fromNetwork) && relayChainIds.containsKey(toNetwork)) return true
        if (squidChainIds.containsKey(fromNetwork) && squidChainIds.containsKey(toNetwork)) return true
        if (changeNowNetworks.containsKey(fromNetwork) && changeNowNetworks.containsKey(toNetwork)) return true
        return false
    }

    // ═══════════════════════════════════════════════════════════════
    //  1inch aggregator
    // ═══════════════════════════════════════════════════════════════

    private fun parseWeiToHumanReadable(weiString: String, decimals: Int): java.math.BigDecimal {
        val wei = java.math.BigDecimal(java.math.BigInteger(weiString))
        val divisor = java.math.BigDecimal.TEN.pow(decimals)
        return wei.divide(divisor, decimals.coerceAtMost(18), java.math.RoundingMode.HALF_UP)
    }

    private fun get1inchQuote(
        chainId: Int, fromToken: String, toToken: String,
        amount: String, network: BlockchainNetwork, fromAddress: String
    ): SwapQuote? {
        val fromAddr = resolveTokenAddress(network, fromToken) ?: return null
        val toAddr = resolveTokenAddress(network, toToken) ?: return null
        val decimals = getTokenDecimals(fromToken, network)
        val amountWei = convertToWei(amount, decimals)

        val url = "https://api.1inch.dev/swap/v6.0/$chainId/quote" +
                "?src=${if (fromAddr == NATIVE_TOKEN_ADDRESS) NATIVE_TOKEN_ADDRESS else fromAddr}" +
                "&dst=${if (toAddr == NATIVE_TOKEN_ADDRESS) NATIVE_TOKEN_ADDRESS else toAddr}" +
                "&amount=$amountWei"

        val response = fetchUrl(url) ?: return null
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val toAmountRaw = obj["toAmount"]?.jsonPrimitive?.content
                ?: obj["dstAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, network)
            val outBd = parseWeiToHumanReadable(toAmountRaw, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = obj["gas"]?.jsonPrimitive?.content ?: "250000",
                network = network, provider = "1inch",
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  0x (Matcha) aggregator
    // ═══════════════════════════════════════════════════════════════

    private val zeroXHosts = mapOf(
        1 to "api.0x.org", 56 to "bsc.api.0x.org", 137 to "polygon.api.0x.org",
        42161 to "arbitrum.api.0x.org", 10 to "optimism.api.0x.org",
        43114 to "avalanche.api.0x.org", 8453 to "base.api.0x.org"
    )

    private fun get0xQuote(
        chainId: Int, fromToken: String, toToken: String,
        amount: String, network: BlockchainNetwork
    ): SwapQuote? {
        val host = zeroXHosts[chainId] ?: return null
        val fromAddr = resolveTokenAddress(network, fromToken) ?: return null
        val toAddr = resolveTokenAddress(network, toToken) ?: return null
        val decimals = getTokenDecimals(fromToken, network)
        val amountWei = convertToWei(amount, decimals)

        val sell = if (fromAddr == NATIVE_TOKEN_ADDRESS) NATIVE_TOKEN_ADDRESS else fromAddr
        val buy = if (toAddr == NATIVE_TOKEN_ADDRESS) NATIVE_TOKEN_ADDRESS else toAddr
        val url = "https://$host/swap/v1/quote?sellToken=$sell&buyToken=$buy&sellAmount=$amountWei"

        val response = fetchUrl(url) ?: return null
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val buyAmt = obj["buyAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, network)
            val outBd = parseWeiToHumanReadable(buyAmt, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = (obj["estimatedPriceImpact"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.3),
                estimatedGas = obj["gas"]?.jsonPrimitive?.content ?: "250000",
                network = network, provider = "0x (Matcha)",
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Paraswap aggregator
    // ═══════════════════════════════════════════════════════════════

    private fun getParaswapQuote(
        chainId: Int, fromToken: String, toToken: String,
        amount: String, network: BlockchainNetwork
    ): SwapQuote? {
        val fromAddr = resolveTokenAddress(network, fromToken) ?: return null
        val toAddr = resolveTokenAddress(network, toToken) ?: return null
        val decimals = getTokenDecimals(fromToken, network)
        val amountWei = convertToWei(amount, decimals)

        val src = if (fromAddr == NATIVE_TOKEN_ADDRESS) "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE" else fromAddr
        val dst = if (toAddr == NATIVE_TOKEN_ADDRESS) "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE" else toAddr
        val url = "https://apiv5.paraswap.io/prices?srcToken=$src&destToken=$dst" +
                "&amount=$amountWei&srcDecimals=$decimals&destDecimals=${getTokenDecimals(toToken, network)}" +
                "&side=SELL&network=$chainId"

        val response = fetchUrl(url) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val priceRoute = root["priceRoute"]?.jsonObject ?: return null
            val destAmt = priceRoute["destAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, network)
            val outBd = parseWeiToHumanReadable(destAmt, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            val gasCost = priceRoute["gasCost"]?.jsonPrimitive?.content ?: "250000"
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = gasCost,
                network = network, provider = "Paraswap",
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OpenOcean aggregator
    // ═══════════════════════════════════════════════════════════════

    private val openOceanChains = mapOf(
        1 to "eth", 56 to "bsc", 137 to "polygon",
        42161 to "arbitrum", 10 to "optimism", 43114 to "avax", 8453 to "base"
    )

    private fun getOpenOceanQuote(
        chainId: Int, fromToken: String, toToken: String,
        amount: String, network: BlockchainNetwork
    ): SwapQuote? {
        val chain = openOceanChains[chainId] ?: return null
        val fromAddr = resolveTokenAddress(network, fromToken) ?: return null
        val toAddr = resolveTokenAddress(network, toToken) ?: return null

        val src = if (fromAddr == NATIVE_TOKEN_ADDRESS) "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE" else fromAddr
        val dst = if (toAddr == NATIVE_TOKEN_ADDRESS) "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE" else toAddr
        val url = "https://open-api.openocean.finance/v3/$chain/quote" +
                "?inTokenAddress=$src&outTokenAddress=$dst&amount=$amount&gasPrice=5"

        val response = fetchUrl(url) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject ?: return null
            val outAmt = data["outAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, network)
            val outBd = parseWeiToHumanReadable(outAmt, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = "250000",
                network = network, provider = "OpenOcean",
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  KyberSwap aggregator
    // ═══════════════════════════════════════════════════════════════

    private val kyberChains = mapOf(
        1 to "ethereum", 56 to "bsc", 137 to "polygon",
        42161 to "arbitrum", 10 to "optimism", 43114 to "avalanche", 8453 to "base"
    )

    private fun getKyberSwapQuote(
        chainId: Int, fromToken: String, toToken: String,
        amount: String, network: BlockchainNetwork
    ): SwapQuote? {
        val chain = kyberChains[chainId] ?: return null
        val fromAddr = resolveTokenAddress(network, fromToken) ?: return null
        val toAddr = resolveTokenAddress(network, toToken) ?: return null
        val decimals = getTokenDecimals(fromToken, network)
        val amountWei = convertToWei(amount, decimals)

        val src = if (fromAddr == NATIVE_TOKEN_ADDRESS) NATIVE_TOKEN_ADDRESS else fromAddr
        val dst = if (toAddr == NATIVE_TOKEN_ADDRESS) NATIVE_TOKEN_ADDRESS else toAddr
        val url = "https://aggregator-api.kyberswap.com/$chain/api/v1/routes" +
                "?tokenIn=$src&tokenOut=$dst&amountIn=$amountWei"

        val response = fetchUrl(url) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject ?: return null
            val routeSummary = data["routeSummary"]?.jsonObject ?: return null
            val amountOut = routeSummary["amountOut"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, network)
            val outBd = parseWeiToHumanReadable(amountOut, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            val gas = routeSummary["gas"]?.jsonPrimitive?.content ?: "250000"
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = gas,
                network = network, provider = "KyberSwap",
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Jupiter (Solana) aggregator
    // ═══════════════════════════════════════════════════════════════

    private fun getJupiterQuote(fromToken: String, toToken: String, amount: String): SwapQuote? {
        val fromMint = tokenAddresses[BlockchainNetwork.SOLANA]?.get(fromToken.uppercase()) ?: return null
        val toMint = tokenAddresses[BlockchainNetwork.SOLANA]?.get(toToken.uppercase()) ?: return null
        val decimals = getTokenDecimals(fromToken, BlockchainNetwork.SOLANA)
        val amountSmallest = convertToWei(amount, decimals)

        val url = "https://quote-api.jup.ag/v6/quote?inputMint=$fromMint&outputMint=$toMint" +
                "&amount=$amountSmallest&slippageBps=50"

        val response = fetchUrl(url) ?: return null
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val outAmt = obj["outAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, BlockchainNetwork.SOLANA)
            val outBd = parseWeiToHumanReadable(outAmt, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            val priceImpact = obj["priceImpactPct"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = priceImpact, estimatedGas = "5000",
                network = BlockchainNetwork.SOLANA, provider = "Jupiter",
                expiresAt = System.currentTimeMillis() + 30_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SunSwap (Tron) DEX
    // ═══════════════════════════════════════════════════════════════

    private fun getSunSwapQuote(fromToken: String, toToken: String, amount: String): SwapQuote? {
        val fromAddr = tokenAddresses[BlockchainNetwork.TRON]?.get(fromToken.uppercase()) ?: return null
        val toAddr = tokenAddresses[BlockchainNetwork.TRON]?.get(toToken.uppercase()) ?: return null
        val decimals = getTokenDecimals(fromToken, BlockchainNetwork.TRON)
        val amountSun = convertToWei(amount, decimals)

        val src = if (fromAddr == "NATIVE") "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb" else fromAddr
        val dst = if (toAddr == "NATIVE") "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb" else toAddr
        val url = "https://rot.endjgfsv.link/swap/router/v2/quote" +
                "?fromToken=$src&toToken=$dst&amountIn=$amountSun&typeList=PSM,CURVE,WTRX,SUNSWAP_V2,SUNSWAP_V3,SUNSWAP_V1"

        val response = fetchUrl(url) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject ?: return null
            val outAmt = data["amountOut"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, BlockchainNetwork.TRON)
            val outBd = parseWeiToHumanReadable(outAmt, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = "15000000",
                network = BlockchainNetwork.TRON, provider = "SunSwap",
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Chainflip (cross-chain)
    // ═══════════════════════════════════════════════════════════════

    private fun getChainflipQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String, destAddress: String
    ): SwapQuote? {
        val srcAsset = chainflipAssetId(fromToken, srcNetwork) ?: return null
        val dstAsset = chainflipAssetId(toToken, dstNetwork) ?: return null
        val decimals = getTokenDecimals(fromToken, srcNetwork)
        val amountBase = convertToWei(amount, decimals)

        val url = "$CHAINFLIP_API/quote?srcAsset=$srcAsset&destAsset=$dstAsset&amount=$amountBase"
        val response = fetchUrl(url) ?: return null
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val egressAmt = obj["egressAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, dstNetwork)
            val outBd = parseWeiToHumanReadable(egressAmt, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.5, estimatedGas = "0",
                network = srcNetwork, provider = "Chainflip",
                expiresAt = System.currentTimeMillis() + 120_000,
                destNetwork = dstNetwork
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Rango Exchange (multi-chain aggregator)
    // ═══════════════════════════════════════════════════════════════

    private fun getRangoQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String, fromAddress: String
    ): SwapQuote? {
        val srcChain = rangoChainIds[srcNetwork] ?: return null
        val dstChain = rangoChainIds[dstNetwork] ?: return null

        val url = "$RANGO_API/quote" +
                "?from=$srcChain.$fromToken&to=$dstChain.$toToken" +
                "&amount=$amount&fromAddress=$fromAddress"

        val response = fetchUrl(url) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val route = root["route"]?.jsonObject ?: return null
            val outputAmt = route["outputAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, dstNetwork)
            val outAmount = outputAmt.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            val feeUsd = route["feeUsd"]?.jsonPrimitive?.content ?: "0"
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = formatAmount(outAmount, toDecimals),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.5, estimatedGas = feeUsd,
                network = srcNetwork, provider = "Rango",
                expiresAt = System.currentTimeMillis() + 120_000,
                destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Li.Fi aggregator (free, no API key, multi-chain)
    // ═══════════════════════════════════════════════════════════════

    private val lifiChainIds = mapOf(
        BlockchainNetwork.ETHEREUM to 1L,
        BlockchainNetwork.BINANCE_SMART_CHAIN to 56L,
        BlockchainNetwork.POLYGON to 137L,
        BlockchainNetwork.ARBITRUM to 42161L,
        BlockchainNetwork.OPTIMISM to 10L,
        BlockchainNetwork.AVALANCHE to 43114L,
        BlockchainNetwork.BASE to 8453L,
        BlockchainNetwork.SOLANA to 1151111081099710L
    )

    private fun resolveLifiTokenAddress(network: BlockchainNetwork, symbol: String): String? {
        val addr = resolveTokenAddress(network, symbol) ?: return null
        if (addr == NATIVE_TOKEN_ADDRESS) return "0x0000000000000000000000000000000000000000"
        if (addr == "NATIVE") return "0x0000000000000000000000000000000000000000"
        return addr
    }

    private fun getLifiQuote(
        network: BlockchainNetwork, fromToken: String, toToken: String,
        amount: String, fromAddress: String,
        destNetwork: BlockchainNetwork? = null
    ): SwapQuote? {
        val srcChainId = lifiChainIds[network] ?: return null
        val dstChainId = lifiChainIds[destNetwork ?: network] ?: return null
        val fromAddr = resolveLifiTokenAddress(network, fromToken) ?: return null
        val toAddr = resolveLifiTokenAddress(destNetwork ?: network, toToken) ?: return null
        val decimals = getTokenDecimals(fromToken, network)
        val amountWei = convertToWei(amount, decimals)

        val url = "https://li.quest/v1/quote" +
                "?fromChain=$srcChainId&toChain=$dstChainId" +
                "&fromToken=$fromAddr&toToken=$toAddr" +
                "&fromAmount=$amountWei&fromAddress=$fromAddress"

        val response = fetchUrl(url) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val estimate = root["estimate"]?.jsonObject ?: return null
            val toAmountRaw = estimate["toAmount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, destNetwork ?: network)
            val outBd = parseWeiToHumanReadable(toAmountRaw, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0
            val gasCostUsd = estimate["gasCosts"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("amountUSD")?.jsonPrimitive?.content ?: "0"
            val toolName = root["toolDetails"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Li.Fi"

            val txRequest = root["transactionRequest"]?.jsonObject
            val txTo = txRequest?.get("to")?.jsonPrimitive?.contentOrNull
            val txData = txRequest?.get("data")?.jsonPrimitive?.contentOrNull
            val txValue = txRequest?.get("value")?.jsonPrimitive?.contentOrNull

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = estimate["slippage"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?.times(100) ?: 0.3,
                estimatedGas = gasCostUsd,
                network = network, provider = "Li.Fi ($toolName)",
                expiresAt = System.currentTimeMillis() + 60_000,
                destNetwork = if (network != (destNetwork ?: network)) destNetwork else null,
                txTo = txTo, txData = txData, txValue = txValue
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Defuse (NEAR-based cross-chain DEX aggregator)
    //  Intent-based: BTC, ETH, SOL, BSC, NEAR, Polygon, Arb, etc.
    //  No API key needed — decentralised solver competition
    // ═══════════════════════════════════════════════════════════════

    private const val DEFUSE_API = "https://solver-relay.chaindefuser.com/rpc"

    private val defuseChainIds = mapOf(
        BlockchainNetwork.NEAR to "near",
        BlockchainNetwork.ETHEREUM to "eth",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "bsc",
        BlockchainNetwork.POLYGON to "polygon",
        BlockchainNetwork.ARBITRUM to "arbitrum",
        BlockchainNetwork.OPTIMISM to "optimism",
        BlockchainNetwork.AVALANCHE to "avalanche",
        BlockchainNetwork.BASE to "base",
        BlockchainNetwork.SOLANA to "solana",
        BlockchainNetwork.BITCOIN to "bitcoin",
        BlockchainNetwork.TRON to "tron",
        BlockchainNetwork.DOGECOIN to "dogecoin",
        BlockchainNetwork.LITECOIN to "litecoin"
    )

    private fun resolveDefuseTokenId(network: BlockchainNetwork, symbol: String): String? {
        val chain = defuseChainIds[network] ?: return null
        val addr = resolveTokenAddress(network, symbol) ?: return null
        if (addr == "NATIVE" || addr == NATIVE_TOKEN_ADDRESS) return "$chain:native"
        return "$chain:$addr"
    }

    private fun getDefuseQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String,
        fromAddress: String? = null
    ): SwapQuote? {
        val fromTokenId = resolveDefuseTokenId(srcNetwork, fromToken) ?: return null
        val toTokenId = resolveDefuseTokenId(dstNetwork, toToken) ?: return null
        val fromDecimals = getTokenDecimals(fromToken, srcNetwork)
        val amountRaw = convertToWei(amount, fromDecimals)
        if (amountRaw == "0") return null

        val requestBody = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "quote")
            put("params", buildJsonArray {
                add(buildJsonObject {
                    put("defuse_asset_identifier_in", fromTokenId)
                    put("defuse_asset_identifier_out", toTokenId)
                    put("exact_amount_in", amountRaw)
                })
            })
        }.toString()

        val response = httpPost(DEFUSE_API, requestBody) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val result = root["result"]?.jsonObject ?: return null
            val outAmountRaw = result["amount_out"]?.jsonPrimitive?.content
                ?: result["estimated_amount_out"]?.jsonPrimitive?.content ?: return null

            val toDecimals = getTokenDecimals(toToken, dstNetwork)
            val outBd = parseWeiToHumanReadable(outAmountRaw, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3,
                estimatedGas = "0",
                network = srcNetwork, provider = "Defuse",
                expiresAt = System.currentTimeMillis() + 30_000,
                destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Ref Finance (NEAR native DEX, same-chain only)
    // ═══════════════════════════════════════════════════════════════

    private const val REF_FINANCE_API = "https://indexer.ref.finance"

    private fun getRefFinanceQuote(
        fromToken: String, toToken: String, amount: String
    ): SwapQuote? {
        val fromAddr = resolveTokenAddress(BlockchainNetwork.NEAR, fromToken) ?: return null
        val toAddr = resolveTokenAddress(BlockchainNetwork.NEAR, toToken) ?: return null

        val fromId = if (fromAddr == "NATIVE") "wrap.near" else fromAddr
        val toId = if (toAddr == "NATIVE") "wrap.near" else toAddr
        if (fromId == toId) return null

        val fromDecimals = getTokenDecimals(fromToken, BlockchainNetwork.NEAR)
        val amountRaw = convertToWei(amount, fromDecimals)
        if (amountRaw == "0") return null

        val url = "$REF_FINANCE_API/get-token-price?token_id=$fromId"
        val fromPriceResp = fetchUrlWithTimeout(url, 5_000)
        val toPriceUrl = "$REF_FINANCE_API/get-token-price?token_id=$toId"
        val toPriceResp = fetchUrlWithTimeout(toPriceUrl, 5_000)

        if (fromPriceResp == null || toPriceResp == null) return null

        return try {
            val fromPrice = json.parseToJsonElement(fromPriceResp).jsonObject["price"]
                ?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val toPrice = json.parseToJsonElement(toPriceResp).jsonObject["price"]
                ?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            if (toPrice <= 0) return null

            val fromAmt = amount.toDoubleOrNull() ?: return null
            val usdValue = fromAmt * fromPrice
            val toAmt = usdValue / toPrice
            val fee = toAmt * 0.003
            val estimatedOut = toAmt - fee

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = formatAmount(estimatedOut, getTokenDecimals(toToken, BlockchainNetwork.NEAR)),
                exchangeRate = if (fromAmt > 0) estimatedOut / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = "0",
                network = BlockchainNetwork.NEAR, provider = "Ref Finance",
                expiresAt = System.currentTimeMillis() + 30_000
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  THORChain quotes
    // ═══════════════════════════════════════════════════════════════

    private fun getThorChainQuote(
        network: BlockchainNetwork, fromToken: String, toToken: String,
        amount: String, destinationAddress: String,
        destNetwork: BlockchainNetwork? = null
    ): SwapQuote? {
        val fromAsset = thorchainAssetId(fromToken, network) ?: return null
        val toAsset = thorchainAssetId(toToken, destNetwork ?: network) ?: return null
        if (fromAsset == toAsset) return null

        val baseAmount = convertToWei(amount, THORCHAIN_DECIMALS)
        if (baseAmount == "0") return null

        val url = "$THORNODE_BASE/thorchain/quote/swap" +
                "?from_asset=$fromAsset&to_asset=$toAsset" +
                "&amount=$baseAmount&destination=$destinationAddress"

        val response = fetchUrl(url) ?: return null
        return parseThorChainQuote(response, fromToken, toToken, amount, network, destNetwork)
    }

    private fun parseThorChainQuote(
        response: String, fromToken: String, toToken: String,
        fromAmount: String, network: BlockchainNetwork,
        destNetwork: BlockchainNetwork? = null
    ): SwapQuote? {
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            if (obj.containsKey("error")) return null

            val expectedOut = obj["expected_amount_out"]?.jsonPrimitive?.content ?: return null
            val outBd = parseWeiToHumanReadable(expectedOut, THORCHAIN_DECIMALS)
            val toAmount = outBd.toDouble()
            val toAmountStr = outBd.stripTrailingZeros().toPlainString()

            val fromAmountDouble = fromAmount.toDoubleOrNull() ?: 1.0
            val exchangeRate = if (fromAmountDouble > 0) toAmount / fromAmountDouble else 0.0

            val memo = obj["memo"]?.jsonPrimitive?.content
            val inboundAddress = obj["inbound_address"]?.jsonPrimitive?.content
            val expiry = obj["expiry"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() / 1000 + 600)

            val fees = obj["fees"]?.jsonObject
            val slippageBps = fees?.get("slippage_bps")?.jsonPrimitive?.intOrNull ?: 0
            val priceImpact = slippageBps / 100.0

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = fromAmount,
                toAmount = toAmountStr, exchangeRate = exchangeRate,
                priceImpact = priceImpact, estimatedGas = "0",
                network = network, provider = "THORChain",
                expiresAt = expiry * 1000,
                thorchainMemo = memo, thorchainInboundAddress = inboundAddress,
                destNetwork = destNetwork
            )
        } catch (_: Exception) { null }
    }

    private suspend fun executeThorChainSwap(quote: SwapQuote, fromAddress: String): Result<SwapResult> {
        val inbound = quote.thorchainInboundAddress
            ?: return Result.failure(Exception("No THORChain inbound address"))
        val memo = quote.thorchainMemo
            ?: return Result.failure(Exception("No THORChain memo"))

        if (quote.network.isEvm) {
            val tokenAddr = resolveTokenAddress(quote.network, quote.fromToken)
            val isNative = tokenAddr == null || tokenAddr == NATIVE_TOKEN_ADDRESS
            val decimals = getTokenDecimals(quote.fromToken, quote.network)
            val amountWei = convertToWei(quote.fromAmount, decimals)

            val txResult = if (isNative) {
                val memoHex = memo.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
                PlatformWallet.sendRawTransaction(
                    network = quote.network,
                    to = inbound,
                    data = "0x$memoHex",
                    value = amountWei,
                    gasLimit = "80000"
                )
            } else {
                val approveResult = approveTokenIfNeeded(quote.network, tokenAddr, inbound, amountWei)
                if (approveResult.isFailure) return Result.failure(Exception("Token approval failed: ${approveResult.exceptionOrNull()?.message}"))

                val amountHex = java.math.BigInteger(amountWei).toString(16).padStart(64, '0')
                val addrHex = inbound.removePrefix("0x").lowercase().padStart(64, '0')
                val transferData = "0xa9059cbb$addrHex$amountHex"
                PlatformWallet.sendRawTransaction(
                    network = quote.network,
                    to = tokenAddr,
                    data = transferData,
                    value = "0",
                    gasLimit = "100000"
                )
            }

            return txResult.fold(
                onSuccess = { txHash ->
                    Result.success(SwapResult(
                        txHash = txHash, fromToken = quote.fromToken, toToken = quote.toToken,
                        fromAmount = quote.fromAmount, toAmount = quote.toAmount,
                        network = quote.network, status = SwapStatus.PENDING,
                        thorchainInboundAddress = inbound, thorchainMemo = memo,
                        crossChainProvider = "THORChain"
                    ))
                },
                onFailure = { Result.failure(Exception("THORChain send failed: ${it.message}")) }
            )
        } else {
            val request = SendTransactionRequest(
                network = quote.network,
                fromAddress = fromAddress,
                toAddress = inbound,
                amount = quote.fromAmount,
                memo = memo
            )
            return WalletCoreService.sendTransaction(request).fold(
                onSuccess = { tx ->
                    Result.success(SwapResult(
                        txHash = tx.txHash, fromToken = quote.fromToken, toToken = quote.toToken,
                        fromAmount = quote.fromAmount, toAmount = quote.toAmount,
                        network = quote.network, status = SwapStatus.PENDING,
                        thorchainInboundAddress = inbound, thorchainMemo = memo,
                        crossChainProvider = "THORChain"
                    ))
                },
                onFailure = { Result.failure(Exception("THORChain send failed: ${it.message}")) }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  On-chain DEX router quotes (Uniswap V2 compatible)
    // ═══════════════════════════════════════════════════════════════

    private fun getDexRouterQuote(
        network: BlockchainNetwork, fromToken: String, toToken: String,
        amount: String, router: DexRouter
    ): SwapQuote? {
        val rpcUrl = rpcUrl(network) ?: return null
        val fromAddress = resolveTokenAddress(network, fromToken) ?: return null
        val toAddress = resolveTokenAddress(network, toToken) ?: return null
        val actualFromAddress = if (fromAddress == NATIVE_TOKEN_ADDRESS) wrappedNativeAddresses[network] ?: return null else fromAddress
        val actualToAddress = if (toAddress == NATIVE_TOKEN_ADDRESS) wrappedNativeAddresses[network] ?: return null else toAddress

        val fromDecimals = getTokenDecimals(fromToken, network)
        val toDecimals = getTokenDecimals(toToken, network)
        val amountWeiStr = convertToWei(amount, fromDecimals)
        val amountWei = java.math.BigInteger(amountWeiStr)
        if (amountWei <= java.math.BigInteger.ZERO) return null

        val amountHex = amountWei.toString(16).padStart(64, '0')
        val pathOffset = "0000000000000000000000000000000000000000000000000000000000000040"
        val pathLength = "0000000000000000000000000000000000000000000000000000000000000002"
        val addr1 = actualFromAddress.removePrefix("0x").lowercase().padStart(64, '0')
        val addr2 = actualToAddress.removePrefix("0x").lowercase().padStart(64, '0')
        val callData = "0xd06ca61f$amountHex$pathOffset$pathLength$addr1$addr2"

        val rpcPayload = """{"jsonrpc":"2.0","id":1,"method":"eth_call","params":[{"to":"${router.address}","data":"$callData"},"latest"]}"""

        val response = httpPost(rpcUrl, rpcPayload) ?: return null
        return parseDexQuoteResponse(response, fromToken, toToken, amount, toDecimals, network, router.name)
    }

    private fun parseDexQuoteResponse(
        response: String, fromToken: String, toToken: String,
        fromAmount: String, toDecimals: Int, network: BlockchainNetwork, dexName: String
    ): SwapQuote? {
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val resultHex = obj["result"]?.jsonPrimitive?.content ?: return null
            if (resultHex == "0x" || resultHex.length < 258) return null
            val amountsOutHex = resultHex.removePrefix("0x")
            if (amountsOutHex.length < 256) return null
            val outAmountHex = amountsOutHex.substring(192, 256).trimStart('0')
            if (outAmountHex.isEmpty()) return null
            val outAmountWei = java.math.BigInteger(outAmountHex, 16)
            val outBd = java.math.BigDecimal(outAmountWei).divide(
                java.math.BigDecimal.TEN.pow(toDecimals),
                toDecimals.coerceAtMost(18),
                java.math.RoundingMode.HALF_UP
            )
            val outAmount = outBd.toDouble()
            val fromAmountDouble = fromAmount.toDoubleOrNull() ?: 1.0
            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = fromAmount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmountDouble > 0) outAmount / fromAmountDouble else 0.0,
                priceImpact = 0.3, estimatedGas = "250000",
                network = network, provider = dexName,
                expiresAt = System.currentTimeMillis() + 60_000
            )
        } catch (_: Exception) { null }
    }

    private fun executeDexSwap(
        quote: SwapQuote, fromAddress: String, slippagePercent: Double
    ): Result<SwapResult> {
        val routerAddress = dexRouters[quote.network]
            ?.firstOrNull { it.name == quote.provider }?.address
            ?: dexRouters[quote.network]?.firstOrNull()?.address
            ?: return Result.failure(Exception("DEX not available on ${quote.network.displayName}"))

        val fromTokenAddress = resolveTokenAddress(quote.network, quote.fromToken)
            ?: return Result.failure(Exception("Unknown token: ${quote.fromToken}"))
        val toTokenAddress = resolveTokenAddress(quote.network, quote.toToken)
            ?: return Result.failure(Exception("Unknown token: ${quote.toToken}"))
        val isFromNative = fromTokenAddress == NATIVE_TOKEN_ADDRESS
        val isToNative = toTokenAddress == NATIVE_TOKEN_ADDRESS

        val amountIn = convertToWei(quote.fromAmount, getTokenDecimals(quote.fromToken, quote.network))
        val minAmountOut = calculateMinAmountOut(quote.toAmount, slippagePercent, getTokenDecimals(quote.toToken, quote.network))

        val swapData = buildSwapCallData(
            fromToken = if (isFromNative) wrappedNativeAddresses[quote.network]!! else fromTokenAddress,
            toToken = if (isToNative) wrappedNativeAddresses[quote.network]!! else toTokenAddress,
            amountIn = amountIn, minAmountOut = minAmountOut,
            recipient = fromAddress, isFromNative = isFromNative, isToNative = isToNative
        )

        if (!isFromNative) {
            val approvalResult = runBlocking {
                approveTokenIfNeeded(quote.network, fromTokenAddress, routerAddress, amountIn)
            }
            if (approvalResult.isFailure) {
                return Result.failure(Exception("Token approval failed: ${approvalResult.exceptionOrNull()?.message}"))
            }
        }

        val txResult = runBlocking {
            PlatformWallet.sendRawTransaction(
                network = quote.network, to = routerAddress,
                data = swapData, value = if (isFromNative) amountIn else "0",
                gasLimit = "300000"
            )
        }

        return txResult.fold(
            onSuccess = { txHash ->
                Result.success(SwapResult(
                    txHash = txHash, fromToken = quote.fromToken, toToken = quote.toToken,
                    fromAmount = quote.fromAmount, toAmount = quote.toAmount,
                    network = quote.network, status = SwapStatus.PENDING
                ))
            },
            onFailure = { Result.failure(Exception("Swap failed: ${it.message}")) }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Symbiosis Finance (cross-chain, no API key, 20+ chains)
    //  https://docs.symbiosis.finance/developer-tools/symbiosis-api
    // ═══════════════════════════════════════════════════════════════

    private const val SYMBIOSIS_API = "https://api-v2.symbiosis.finance/crosschain/v1"

    private val symbiosisChainIds = mapOf(
        BlockchainNetwork.ETHEREUM to 1L,
        BlockchainNetwork.BINANCE_SMART_CHAIN to 56L,
        BlockchainNetwork.POLYGON to 137L,
        BlockchainNetwork.ARBITRUM to 42161L,
        BlockchainNetwork.OPTIMISM to 10L,
        BlockchainNetwork.AVALANCHE to 43114L,
        BlockchainNetwork.BASE to 8453L,
        BlockchainNetwork.SOLANA to 1399811149L,
        BlockchainNetwork.TRON to 728126428L,
        BlockchainNetwork.BITCOIN to 8253038L
    )

    private fun resolveSymbiosisTokenAddress(network: BlockchainNetwork, symbol: String): String? {
        val addr = resolveTokenAddress(network, symbol) ?: return null
        if (addr == NATIVE_TOKEN_ADDRESS || addr == "NATIVE") return when (network) {
            BlockchainNetwork.SOLANA -> "11111111111111111111111111111111"
            BlockchainNetwork.TRON -> ""
            else -> ""
        }
        return addr
    }

    private fun getSymbiosisQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String,
        fromAddress: String, toAddress: String = fromAddress
    ): SwapQuote? {
        val srcChainId = symbiosisChainIds[srcNetwork] ?: return null
        val dstChainId = symbiosisChainIds[dstNetwork] ?: return null
        val fromAddr = resolveSymbiosisTokenAddress(srcNetwork, fromToken) ?: return null
        val toAddr = resolveSymbiosisTokenAddress(dstNetwork, toToken) ?: return null
        val fromDecimals = getTokenDecimals(fromToken, srcNetwork)
        val amountRaw = convertToWei(amount, fromDecimals)
        if (amountRaw == "0") return null

        val body = buildJsonObject {
            put("tokenAmountIn", buildJsonObject {
                put("address", fromAddr)
                put("chainId", srcChainId)
                put("decimals", fromDecimals)
                put("symbol", fromToken.uppercase())
                put("amount", amountRaw)
            })
            put("tokenOut", buildJsonObject {
                put("address", toAddr)
                put("chainId", dstChainId)
                put("decimals", getTokenDecimals(toToken, dstNetwork))
                put("symbol", toToken.uppercase())
            })
            put("from", fromAddress)
            put("to", toAddress)
            put("slippage", 50)
        }.toString()

        val response = httpPost("$SYMBIOSIS_API/swap", body) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val tokenAmountOut = root["tokenAmountOut"]?.jsonObject ?: return null
            val outAmountRaw = tokenAmountOut["amount"]?.jsonPrimitive?.content ?: return null
            val toDecimals = getTokenDecimals(toToken, dstNetwork)
            val outBd = parseWeiToHumanReadable(outAmountRaw, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0

            val tx = root["tx"]?.jsonObject
            val txTo = tx?.get("to")?.jsonPrimitive?.contentOrNull
            val txData = tx?.get("data")?.jsonPrimitive?.contentOrNull
            val txValue = tx?.get("value")?.jsonPrimitive?.contentOrNull

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.3, estimatedGas = "0",
                network = srcNetwork, provider = "Symbiosis",
                expiresAt = System.currentTimeMillis() + 30_000,
                destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null,
                txTo = txTo, txData = txData, txValue = txValue
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Relay (cross-chain bridge, free, no API key)
    //  https://docs.relay.link/references/api/get-quote-v2
    // ═══════════════════════════════════════════════════════════════

    private val relayChainIds = mapOf(
        BlockchainNetwork.ETHEREUM to 1L,
        BlockchainNetwork.BINANCE_SMART_CHAIN to 56L,
        BlockchainNetwork.POLYGON to 137L,
        BlockchainNetwork.ARBITRUM to 42161L,
        BlockchainNetwork.OPTIMISM to 10L,
        BlockchainNetwork.AVALANCHE to 43114L,
        BlockchainNetwork.BASE to 8453L
    )

    private fun resolveRelayTokenAddress(network: BlockchainNetwork, symbol: String): String {
        val addr = resolveTokenAddress(network, symbol) ?: return "0x0000000000000000000000000000000000000000"
        if (addr == NATIVE_TOKEN_ADDRESS) return "0x0000000000000000000000000000000000000000"
        return addr
    }

    private fun getRelayQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String,
        fromAddress: String
    ): SwapQuote? {
        val srcChainId = relayChainIds[srcNetwork] ?: return null
        val dstChainId = relayChainIds[dstNetwork] ?: return null
        val fromAddr = resolveRelayTokenAddress(srcNetwork, fromToken)
        val toAddr = resolveRelayTokenAddress(dstNetwork, toToken)
        val fromDecimals = getTokenDecimals(fromToken, srcNetwork)
        val amountRaw = convertToWei(amount, fromDecimals)
        if (amountRaw == "0") return null

        val body = buildJsonObject {
            put("user", fromAddress)
            put("originChainId", srcChainId)
            put("destinationChainId", dstChainId)
            put("originCurrency", fromAddr)
            put("destinationCurrency", toAddr)
            put("amount", amountRaw)
            put("tradeType", "EXACT_INPUT")
        }.toString()

        val response = httpPost("https://api.relay.link/quote", body) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val details = root["details"]?.jsonObject ?: return null
            val toDecimals = getTokenDecimals(toToken, dstNetwork)

            val outAmountRaw = details["currencyOut"]?.jsonObject
                ?.get("amount")?.jsonPrimitive?.content ?: return null
            val outBd = parseWeiToHumanReadable(outAmountRaw, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0

            val steps = root["steps"]?.jsonArray
            val txTo = steps?.firstOrNull()?.jsonObject
                ?.get("items")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("data")?.jsonObject?.get("to")?.jsonPrimitive?.contentOrNull
            val txData = steps?.firstOrNull()?.jsonObject
                ?.get("items")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("data")?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
            val txValue = steps?.firstOrNull()?.jsonObject
                ?.get("items")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("data")?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = 0.2, estimatedGas = "0",
                network = srcNetwork, provider = "Relay",
                expiresAt = System.currentTimeMillis() + 30_000,
                destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null,
                txTo = txTo, txData = txData, txValue = txValue
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Squid Router (Axelar-powered, 100+ chains)
    //  https://docs.squidrouter.com/api-and-sdk-integration/api
    //  Requires free Integrator ID — register at squidrouter.com
    // ═══════════════════════════════════════════════════════════════

    private const val SQUID_API = "https://v2.api.squidrouter.com/v2"

    private val SQUID_INTEGRATOR_ID: String by lazy {
        try {
            val clazz = Class.forName("chat.simplex.app.BuildConfig")
            clazz.getField("SQUID_INTEGRATOR_ID").get(null) as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private val squidChainIds = mapOf(
        BlockchainNetwork.ETHEREUM to "1",
        BlockchainNetwork.BINANCE_SMART_CHAIN to "56",
        BlockchainNetwork.POLYGON to "137",
        BlockchainNetwork.ARBITRUM to "42161",
        BlockchainNetwork.OPTIMISM to "10",
        BlockchainNetwork.AVALANCHE to "43114",
        BlockchainNetwork.BASE to "8453",
        BlockchainNetwork.SOLANA to "solana",
        BlockchainNetwork.NEAR to "near"
    )

    private fun resolveSquidTokenAddress(network: BlockchainNetwork, symbol: String): String {
        val addr = resolveTokenAddress(network, symbol) ?: return "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
        if (addr == NATIVE_TOKEN_ADDRESS) return "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
        if (addr == "NATIVE") return when (network) {
            BlockchainNetwork.SOLANA -> "So11111111111111111111111111111111111111112"
            else -> "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
        }
        return addr
    }

    private fun getSquidQuote(
        srcNetwork: BlockchainNetwork, dstNetwork: BlockchainNetwork,
        fromToken: String, toToken: String, amount: String,
        destAddress: String
    ): SwapQuote? {
        if (SQUID_INTEGRATOR_ID.isBlank()) return null
        val srcChainId = squidChainIds[srcNetwork] ?: return null
        val dstChainId = squidChainIds[dstNetwork] ?: return null
        val fromAddr = resolveSquidTokenAddress(srcNetwork, fromToken)
        val toAddr = resolveSquidTokenAddress(dstNetwork, toToken)
        val fromDecimals = getTokenDecimals(fromToken, srcNetwork)
        val amountRaw = convertToWei(amount, fromDecimals)
        if (amountRaw == "0") return null

        val body = buildJsonObject {
            put("fromAddress", destAddress)
            put("fromChain", srcChainId)
            put("fromToken", fromAddr)
            put("fromAmount", amountRaw)
            put("toChain", dstChainId)
            put("toToken", toAddr)
            put("toAddress", destAddress)
            put("slippageConfig", buildJsonObject {
                put("autoMode", 1)
            })
        }.toString()

        val response = httpPostWithHeaders("$SQUID_API/route", body, mapOf(
            "x-integrator-id" to SQUID_INTEGRATOR_ID
        )) ?: return null
        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val route = root["route"]?.jsonObject ?: return null
            val estimate = route["estimate"]?.jsonObject ?: return null
            val toDecimals = getTokenDecimals(toToken, dstNetwork)

            val outAmountRaw = estimate["toAmount"]?.jsonPrimitive?.content ?: return null
            val outBd = parseWeiToHumanReadable(outAmountRaw, toDecimals)
            val outAmount = outBd.toDouble()
            val fromAmt = amount.toDoubleOrNull() ?: 1.0

            val txReq = route["transactionRequest"]?.jsonObject
            val txTo = txReq?.get("target")?.jsonPrimitive?.contentOrNull
                ?: txReq?.get("to")?.jsonPrimitive?.contentOrNull
            val txData = txReq?.get("data")?.jsonPrimitive?.contentOrNull
            val txValue = txReq?.get("value")?.jsonPrimitive?.contentOrNull

            SwapQuote(
                fromToken = fromToken, toToken = toToken, fromAmount = amount,
                toAmount = outBd.stripTrailingZeros().toPlainString(),
                exchangeRate = if (fromAmt > 0) outAmount / fromAmt else 0.0,
                priceImpact = estimate["aggregatePriceImpact"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.3,
                estimatedGas = estimate["gasCosts"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("amount")?.jsonPrimitive?.contentOrNull ?: "0",
                network = srcNetwork, provider = "Squid",
                expiresAt = System.currentTimeMillis() + 30_000,
                destNetwork = if (srcNetwork != dstNetwork) dstNetwork else null,
                txTo = txTo, txData = txData, txValue = txValue
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun resolveTokenAddress(network: BlockchainNetwork, symbol: String): String? =
        tokenAddresses[network]?.get(symbol.uppercase())

    fun resolveTokenAddressPublic(network: BlockchainNetwork, symbol: String): String? =
        resolveTokenAddress(network, symbol)

    private fun formatAmount(value: Double, decimals: Int): String {
        val scale = decimals.coerceAtMost(8)
        return java.math.BigDecimal.valueOf(value)
            .setScale(scale, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    }

    private fun convertToWei(amount: String, decimals: Int): String {
        return try {
            val bd = java.math.BigDecimal(amount)
            val multiplier = java.math.BigDecimal.TEN.pow(decimals)
            bd.multiply(multiplier).toBigInteger().toString()
        } catch (_: Exception) { "0" }
    }

    fun convertToWeiPublic(amount: String, decimals: Int): String = convertToWei(amount, decimals)

    private fun calculateMinAmountOut(toAmount: String, slippagePercent: Double, decimals: Int): String {
        return try {
            val bd = java.math.BigDecimal(toAmount)
            val factor = java.math.BigDecimal.ONE.subtract(
                java.math.BigDecimal.valueOf(slippagePercent).divide(java.math.BigDecimal("100"))
            )
            val min = bd.multiply(factor)
            val multiplier = java.math.BigDecimal.TEN.pow(decimals)
            min.multiply(multiplier).toBigInteger().toString()
        } catch (_: Exception) { "0" }
    }

    private fun buildSwapCallData(
        fromToken: String, toToken: String, amountIn: String,
        minAmountOut: String, recipient: String,
        isFromNative: Boolean, isToNative: Boolean
    ): String {
        val deadline = ((System.currentTimeMillis() / 1000) + 1200).toString(16).padStart(64, '0')
        val fromAddr = fromToken.removePrefix("0x").lowercase().padStart(64, '0')
        val toAddr = toToken.removePrefix("0x").lowercase().padStart(64, '0')
        val recipientAddr = recipient.removePrefix("0x").lowercase().padStart(64, '0')
        val amountInHex = try { java.math.BigInteger(amountIn).toString(16).padStart(64, '0') } catch (_: Exception) { "0".padStart(64, '0') }
        val minOutHex = try { java.math.BigInteger(minAmountOut).toString(16).padStart(64, '0') } catch (_: Exception) { "0".padStart(64, '0') }

        return when {
            isFromNative -> {
                val pathOffset = "0000000000000000000000000000000000000000000000000000000000000080"
                val pathLength = "0000000000000000000000000000000000000000000000000000000000000002"
                "0x7ff36ab5$minOutHex$pathOffset$recipientAddr$deadline$pathLength$fromAddr$toAddr"
            }
            isToNative -> {
                val pathOffset = "00000000000000000000000000000000000000000000000000000000000000a0"
                val pathLength = "0000000000000000000000000000000000000000000000000000000000000002"
                "0x18cbafe5$amountInHex$minOutHex$pathOffset$recipientAddr$deadline$pathLength$fromAddr$toAddr"
            }
            else -> {
                val pathOffset = "00000000000000000000000000000000000000000000000000000000000000a0"
                val pathLength = "0000000000000000000000000000000000000000000000000000000000000002"
                "0x38ed1739$amountInHex$minOutHex$pathOffset$recipientAddr$deadline$pathLength$fromAddr$toAddr"
            }
        }
    }

    private suspend fun approveTokenIfNeeded(
        network: BlockchainNetwork, tokenAddress: String,
        spenderAddress: String, amount: String
    ): Result<Unit> {
        return try {
            val requiredAmount = try { java.math.BigInteger(amount) } catch (_: Exception) { java.math.BigInteger.ZERO }
            if (requiredAmount == java.math.BigInteger.ZERO) return Result.success(Unit)

            val ownerAddress = WalletCoreService.getAccount(network)?.address
                ?: return Result.success(Unit)
            val allowanceData = TokenApprovalService.buildAllowanceCallData(
                ownerAddress = ownerAddress,
                spenderAddress = spenderAddress
            )
            val currentAllowance = try {
                val resp = BlockchainService.rpcCall(
                    network, "eth_call",
                    listOf(
                        buildJsonObject { put("to", tokenAddress); put("data", allowanceData) }.toString(),
                        "latest"
                    )
                )
                TokenApprovalService.parseAllowanceResponse(resp?.jsonPrimitive?.contentOrNull ?: "0x0")
            } catch (_: Exception) { java.math.BigInteger.ZERO }

            if (currentAllowance >= requiredAmount) return Result.success(Unit)

            val approveAmount = requiredAmount.multiply(java.math.BigInteger.valueOf(2))
            val approveData = TokenApprovalService.buildApproveCallData(spenderAddress, approveAmount)
            val result = PlatformWallet.sendRawTransaction(
                network = network, to = tokenAddress, data = approveData,
                value = "0", gasLimit = "60000"
            )
            result.fold(
                onSuccess = { delay(5000); Result.success(Unit) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) { Result.failure(e) }
    }

    private val decimalsCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun getTokenDecimals(symbol: String, network: BlockchainNetwork? = null): Int {
        val cacheKey = "${network?.name ?: "ANY"}:${symbol.uppercase()}"
        decimalsCache[cacheKey]?.let { return it }

        val result = getTokenDecimalsFallback(symbol, network)
        decimalsCache[cacheKey] = result
        return result
    }

    suspend fun fetchOnChainDecimals(tokenAddress: String, network: BlockchainNetwork): Int? {
        if (!network.isEvm) return null
        val nativeAddrs = setOf(
            "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE",
            NATIVE_TOKEN_ADDRESS, "NATIVE", ""
        )
        if (tokenAddress in nativeAddrs) return 18

        return try {
            val resp = BlockchainService.rpcCall(
                network, "eth_call",
                listOf(
                    buildJsonObject {
                        put("to", tokenAddress)
                        put("data", "0x313ce567") // decimals()
                    }.toString(),
                    "latest"
                )
            )
            val hex = resp?.jsonPrimitive?.contentOrNull?.removePrefix("0x") ?: return null
            if (hex.isBlank() || hex == "0") return null
            val d = hex.trimStart('0').let { if (it.isEmpty()) 0 else it.toInt(16) }
            if (d in 0..36) d else null
        } catch (_: Exception) { null }
    }

    suspend fun getTokenDecimalsOnChain(symbol: String, network: BlockchainNetwork): Int {
        val cacheKey = "${network.name}:${symbol.uppercase()}"
        decimalsCache[cacheKey]?.let { return it }

        val tokenAddr = resolveTokenAddress(network, symbol)
        if (tokenAddr != null && network.isEvm) {
            val onChain = fetchOnChainDecimals(tokenAddr, network)
            if (onChain != null) {
                decimalsCache[cacheKey] = onChain
                return onChain
            }
        }
        val fallback = getTokenDecimalsFallback(symbol, network)
        decimalsCache[cacheKey] = fallback
        return fallback
    }

    private fun getTokenDecimalsFallback(symbol: String, network: BlockchainNetwork?): Int {
        if (network == BlockchainNetwork.BINANCE_SMART_CHAIN) {
            return when (symbol.uppercase()) {
                "USDT", "USDC", "BUSD", "DAI", "TUSD", "FRAX" -> 18
                "BTCB", "BTC.B" -> 18
                else -> 18
            }
        }
        if (network == BlockchainNetwork.POLYGON) {
            return when (symbol.uppercase()) {
                "USDT", "USDC" -> 6
                "DAI" -> 18
                "WBTC" -> 8
                else -> 18
            }
        }
        if (network == BlockchainNetwork.TRON) {
            return when (symbol.uppercase()) {
                "USDT", "USDC", "USDJ", "TUSD" -> 6
                "TRX" -> 6
                "SUN", "JST", "WIN", "BTT" -> 18
                else -> 6
            }
        }
        if (network == BlockchainNetwork.SOLANA) {
            return when (symbol.uppercase()) {
                "SOL", "MSOL", "JITOSOL" -> 9
                "USDT", "USDC" -> 6
                "BONK" -> 5
                else -> 9
            }
        }
        if (network == BlockchainNetwork.NEAR) {
            return when (symbol.uppercase()) {
                "NEAR", "WNEAR", "STNEAR", "LINEAR" -> 24
                "USDT", "USDC", "USDTE" -> 6
                "AURORA", "SWEAT", "REF", "BRRR" -> 18
                else -> 24
            }
        }
        return when (symbol.uppercase()) {
            "USDT", "USDC", "TUSD", "USDJ" -> 6
            "TRX" -> 6
            "XRP" -> 6
            "ADA" -> 6
            "WBTC", "BTCB", "BTC.B" -> 8
            "BTC", "LTC", "DOGE" -> 8
            "SOL", "MSOL", "JITOSOL" -> 9
            "BONK" -> 5
            "SUN", "JST", "WIN", "BTT" -> 18
            else -> 18
        }
    }

    private fun getTokenName(symbol: String): String = when (symbol.uppercase()) {
        "ETH" -> "Ethereum"; "WETH" -> "Wrapped Ethereum"
        "BNB" -> "BNB"; "WBNB" -> "Wrapped BNB"
        "MATIC" -> "Polygon"; "POL" -> "Polygon"; "WMATIC" -> "Wrapped Matic"
        "AVAX" -> "Avalanche"; "WAVAX" -> "Wrapped AVAX"
        "USDT" -> "Tether USD"; "USDC" -> "USD Coin"
        "DAI" -> "Dai"; "BUSD" -> "Binance USD"; "FRAX" -> "Frax"; "TUSD" -> "TrueUSD"
        "WBTC" -> "Wrapped Bitcoin"; "BTCB" -> "Binance BTC"; "BTC.B" -> "Bridged BTC"
        "UNI" -> "Uniswap"; "LINK" -> "Chainlink"; "AAVE" -> "Aave"
        "MKR" -> "Maker"; "ARB" -> "Arbitrum"; "OP" -> "Optimism"
        "CAKE" -> "PancakeSwap"; "SHIB" -> "Shiba Inu"; "PEPE" -> "Pepe"
        "NEAR" -> "NEAR Protocol"; "WNEAR" -> "Wrapped NEAR"; "STNEAR" -> "Staked NEAR"
        "LINEAR" -> "LiNEAR"; "REF" -> "Ref Finance"; "BRRR" -> "Burrow"
        "SWEAT" -> "Sweat Economy"; "AURORA" -> "Aurora"
        "BTC" -> "Bitcoin"; "SOL" -> "Solana"; "TRX" -> "Tron"
        "LTC" -> "Litecoin"; "DOGE" -> "Dogecoin"; "XRP" -> "Ripple"
        "ADA" -> "Cardano"; "DOT" -> "Polkadot"; "ATOM" -> "Cosmos"
        "GRT" -> "The Graph"; "SNX" -> "Synthetix"; "CRV" -> "Curve"
        "COMP" -> "Compound"; "APE" -> "ApeCoin"; "LDO" -> "Lido DAO"
        "FET" -> "Fetch.ai"; "RNDR" -> "Render"; "DYDX" -> "dYdX"
        "ENS" -> "ENS"; "1INCH" -> "1inch"; "SUSHI" -> "SushiSwap"
        "RPL" -> "Rocket Pool"; "GMX" -> "GMX"; "PENDLE" -> "Pendle"
        "MAGIC" -> "Magic"; "RDNT" -> "Radiant"
        "QUICK" -> "QuickSwap"; "BAL" -> "Balancer"
        "VELO" -> "Velodrome"; "AERO" -> "Aerodrome"
        "JOE" -> "Trader Joe"; "PNG" -> "Pangolin"
        "JUP" -> "Jupiter"; "JTO" -> "Jito"; "RAY" -> "Raydium"
        "BONK" -> "Bonk"; "WIF" -> "dogwifhat"; "ORCA" -> "Orca"
        "PYTH" -> "Pyth Network"; "W" -> "Wormhole"
        "RENDER" -> "Render"; "TENSOR" -> "Tensor"
        "MNGO" -> "Mango"; "DEGEN" -> "Degen"; "BRETT" -> "Brett"
        "cbETH" -> "Coinbase ETH"; "RETH" -> "Rocket Pool ETH"
        "STETH" -> "Lido Staked ETH"; "STMATIC" -> "Staked Matic"
        "SUN" -> "Sun Token"; "JST" -> "JUST"; "BTT" -> "BitTorrent"
        "WIN" -> "WINkLink"; "NFT" -> "APENFT"; "FLOKI" -> "Floki"
        "TWT" -> "Trust Wallet"; "FLIP" -> "Chainflip"
        "RUNE" -> "THORChain"; "STG" -> "Stargate"
        "IMX" -> "Immutable X"; "BLUR" -> "Blur"; "SAND" -> "Sandbox"
        "MANA" -> "Decentraland"; "AXS" -> "Axie Infinity"; "YFI" -> "Yearn"
        "INJ" -> "Injective"; "WLD" -> "Worldcoin"; "ENA" -> "Ethena"
        "ONDO" -> "Ondo"; "ETHFI" -> "Ether.fi"
        "SFP" -> "SafePal"; "BAKE" -> "BakerySwap"; "FIL" -> "Filecoin"
        "NEAR" -> "NEAR Protocol"; "GHST" -> "Aavegotchi"
        "WELL" -> "Moonwell"; "TOSHI" -> "Toshi"
        "USDBC" -> "USD Base Coin"; "USDJ" -> "USDJ"
        "MSOL" -> "Marinade SOL"; "JITOSOL" -> "Jito SOL"
        "INF" -> "Infinity"; "HNT" -> "Helium"; "MOBILE" -> "Helium Mobile"
        "MEW" -> "cat in a dogs world"; "POPCAT" -> "Popcat"
        else -> symbol
    }

    private fun fetchUrl(urlString: String): String? =
        SecureHttp.get(urlString, emptyMap())

    private fun fetchUrlWithTimeout(urlString: String, timeoutMs: Int): String? =
        SecureHttp.getWithTimeout(urlString, timeoutMs)

    private fun fetchUrlWithHeaders(urlString: String, headers: Map<String, String>): String? =
        SecureHttp.get(urlString, headers)

    private fun httpPost(url: String, body: String): String? =
        SecureHttp.postJson(url, body)

    private fun httpPostWithHeaders(url: String, body: String, headers: Map<String, String>): String? =
        SecureHttp.postJsonWithHeaders(url, body, headers)

    private fun httpPostWithErrorBody(url: String, body: String, headers: Map<String, String>): Pair<String?, String?> =
        SecureHttp.postJsonFull(url, body, headers)
}

// ═══════════════════════════════════════════════════════════════════
//  Data classes
// ═══════════════════════════════════════════════════════════════════

data class SwapQuote(
    val fromToken: String,
    val toToken: String,
    val fromAmount: String,
    val toAmount: String,
    val exchangeRate: Double,
    val priceImpact: Double,
    val estimatedGas: String,
    val network: BlockchainNetwork,
    val provider: String,
    val expiresAt: Long,
    val thorchainMemo: String? = null,
    val thorchainInboundAddress: String? = null,
    val destNetwork: BlockchainNetwork? = null,
    val txTo: String? = null,
    val txData: String? = null,
    val txValue: String? = null
)

data class SwapToken(
    val symbol: String,
    val name: String,
    val address: String,
    val decimals: Int,
    val network: BlockchainNetwork,
    val logoUrl: String?
)

data class SwapResult(
    val txHash: String,
    val fromToken: String,
    val toToken: String,
    val fromAmount: String,
    val toAmount: String,
    val network: BlockchainNetwork,
    val status: SwapStatus,
    val thorchainInboundAddress: String? = null,
    val thorchainMemo: String? = null,
    val crossChainProvider: String? = null
)

enum class SwapStatus {
    PENDING,
    CONFIRMED,
    FAILED
}

private data class SwapTransactionData(
    val to: String,
    val data: String,
    val value: String,
    val gas: String
)
