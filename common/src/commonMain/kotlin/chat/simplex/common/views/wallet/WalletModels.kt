package chat.simplex.common.views.wallet

import chat.simplex.res.MR
import chat.simplex.common.views.helpers.generalGetString
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Supported blockchain networks
 */
@Serializable
enum class BlockchainNetwork(
    val displayName: String,
    val symbol: String,
    val coinType: Int,
    val decimals: Int,
    val explorerUrl: String
) {
    BITCOIN("Bitcoin", "BTC", 0, 8, "https://blockchair.com/bitcoin/transaction/"),
    ETHEREUM("Ethereum", "ETH", 60, 18, "https://etherscan.io/tx/"),
    BINANCE_SMART_CHAIN("BNB Smart Chain", "BNB", 60, 18, "https://bscscan.com/tx/"),
    SOLANA("Solana", "SOL", 501, 9, "https://solscan.io/tx/"),
    POLYGON("Polygon", "MATIC", 60, 18, "https://polygonscan.com/tx/"),
    AVALANCHE("Avalanche", "AVAX", 60, 18, "https://snowtrace.io/tx/"),
    TRON("Tron", "TRX", 195, 6, "https://tronscan.org/#/transaction/"),
    LITECOIN("Litecoin", "LTC", 2, 8, "https://blockchair.com/litecoin/transaction/"),
    DOGECOIN("Dogecoin", "DOGE", 3, 8, "https://blockchair.com/dogecoin/transaction/"),
    RIPPLE("Ripple", "XRP", 144, 6, "https://xrpscan.com/tx/"),
    CARDANO("Cardano", "ADA", 1815, 6, "https://cardanoscan.io/transaction/"),
    ARBITRUM("Arbitrum", "ETH", 60, 18, "https://arbiscan.io/tx/"),
    OPTIMISM("Optimism", "ETH", 60, 18, "https://optimistic.etherscan.io/tx/"),
    BASE("Base", "ETH", 60, 18, "https://basescan.org/tx/"),
    NEAR("NEAR Protocol", "NEAR", 397, 24, "https://nearblocks.io/txns/");
    
    val isEvm: Boolean
        get() = this in EVM_NETWORKS

    companion object {
        val EVM_NETWORKS = setOf(
            ETHEREUM, BINANCE_SMART_CHAIN, POLYGON, ARBITRUM, OPTIMISM, AVALANCHE, BASE
        )

        val ALL_SUPPORTED = values().toList()

        fun fromSymbol(symbol: String): BlockchainNetwork? =
            values().find { it.symbol.equals(symbol, ignoreCase = true) }
    }
}

/**
 * Wallet account representing a single cryptocurrency wallet
 */
@Serializable
data class WalletAccount(
    val id: String,
    val name: String,
    val network: BlockchainNetwork,
    val address: String,
    val publicKey: String,
    val isImported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val balance: String = "0",
    val balanceUsd: Double = 0.0,
    val isActive: Boolean = true
)

/**
 * Token on a blockchain (ERC-20, BEP-20, SPL, etc.)
 */
@Serializable
data class WalletToken(
    val contractAddress: String,
    val network: BlockchainNetwork,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val balance: String = "0",
    val balanceUsd: Double = 0.0,
    val iconUrl: String? = null,
    val isEnabled: Boolean = true
)

private val STABLECOIN_SYMBOLS = setOf(
    "USDT", "USDC", "USDC.E", "BUSD", "DAI", "TUSD", "USDP", "FRAX", "LUSD", "GUSD", "PYUSD"
)

fun displayBalance(balance: String, symbol: String): String {
    val value = balance.toDoubleOrNull() ?: return balance
    if (value == 0.0) return "0"
    val maxDecimals = when {
        symbol.uppercase() in STABLECOIN_SYMBOLS -> 2
        symbol.uppercase() in setOf("BTC", "BTCB", "WBTC") -> 8
        symbol.uppercase() in setOf("ETH", "WETH", "BNB", "MATIC", "AVAX") -> 6
        symbol.uppercase() in setOf("SOL", "NEAR", "ADA", "DOT") -> 4
        symbol.uppercase() in setOf("DOGE", "SHIB", "XRP", "TRX", "LTC") -> 4
        value >= 1000 -> 2
        value >= 1 -> 4
        value >= 0.0001 -> 6
        else -> 8
    }
    return String.format("%.${maxDecimals}f", value)
        .trimEnd('0')
        .trimEnd('.')
}

/**
 * Popular tokens with contract/mint addresses for each network.
 * Covers EVM (ERC-20 / BEP-20 / etc.), Tron TRC-20, and Solana SPL tokens.
 */
object PopularTokens {

    // ── Ethereum Mainnet (ERC-20) ───────────────────────────────────
    val ETHEREUM_TOKENS = listOf(
        WalletToken("0xdAC17F958D2ee523a2206206994597C13D831ec7", BlockchainNetwork.ETHEREUM, "USDT", "Tether USD", 6),
        WalletToken("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", BlockchainNetwork.ETHEREUM, "USDC", "USD Coin", 6),
        WalletToken("0x6B175474E89094C44Da98b954EedeAC495271d0F", BlockchainNetwork.ETHEREUM, "DAI", "Dai Stablecoin", 18),
        WalletToken("0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599", BlockchainNetwork.ETHEREUM, "WBTC", "Wrapped Bitcoin", 8),
        WalletToken("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", BlockchainNetwork.ETHEREUM, "WETH", "Wrapped Ether", 18),
        WalletToken("0x514910771AF9Ca656af840dff83E8264EcF986CA", BlockchainNetwork.ETHEREUM, "LINK", "Chainlink", 18),
        WalletToken("0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984", BlockchainNetwork.ETHEREUM, "UNI", "Uniswap", 18),
        WalletToken("0x7Fc66500c84A76Ad7e9c93437bFc5Ac33E2DDaE9", BlockchainNetwork.ETHEREUM, "AAVE", "Aave", 18),
        WalletToken("0x5A98FcBEA516Cf06857215779Fd812CA3beF1B32", BlockchainNetwork.ETHEREUM, "LDO", "Lido DAO", 18),
        WalletToken("0x7D1AfA7B718fb893dB30A3aBc0Cfc608AaCfeBB0", BlockchainNetwork.ETHEREUM, "MATIC", "Polygon", 18),
        WalletToken("0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE", BlockchainNetwork.ETHEREUM, "SHIB", "Shiba Inu", 18),
        WalletToken("0x6982508145454Ce325dDbE47a25d4ec3d2311933", BlockchainNetwork.ETHEREUM, "PEPE", "Pepe", 18),
        WalletToken("0x9f8F72aA9304c8B593d555F12eF6589cC3A579A2", BlockchainNetwork.ETHEREUM, "MKR", "Maker", 18),
        WalletToken("0xD533a949740bb3306d119CC777fa900bA034cd52", BlockchainNetwork.ETHEREUM, "CRV", "Curve DAO", 18),
        WalletToken("0x1776e1F26f98b1A5dF9cD347953a26dd3Cb46671", BlockchainNetwork.ETHEREUM, "NMR", "Numeraire", 18),
        WalletToken("0xC011a73ee8576Fb46F5E1c5751cA3B9Fe0af2a6F", BlockchainNetwork.ETHEREUM, "SNX", "Synthetix", 18),
        WalletToken("0x0F5D2fB29fb7d3CFeE444a200298f468908cC942", BlockchainNetwork.ETHEREUM, "MANA", "Decentraland", 18),
        WalletToken("0x4Fabb145d64652a948d72533023f6E7A623C7C53", BlockchainNetwork.ETHEREUM, "BUSD", "Binance USD", 18),
        WalletToken("0x0000000000085d4780B73119b644AE5ecd22b376", BlockchainNetwork.ETHEREUM, "TUSD", "TrueUSD", 18),
        WalletToken("0x6c3F90f043a72FA612cbac8115EE7e52BDe6E490", BlockchainNetwork.ETHEREUM, "3CRV", "Curve.fi DAI/USDC/USDT", 18),
    )

    // ── BSC / BNB Chain (BEP-20) ────────────────────────────────────
    val BSC_TOKENS = listOf(
        WalletToken("0x55d398326f99059fF775485246999027B3197955", BlockchainNetwork.BINANCE_SMART_CHAIN, "USDT", "Tether USD", 18),
        WalletToken("0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d", BlockchainNetwork.BINANCE_SMART_CHAIN, "USDC", "USD Coin", 18),
        WalletToken("0xe9e7CEA3DedcA5984780Bafc599bD69ADd087D56", BlockchainNetwork.BINANCE_SMART_CHAIN, "BUSD", "Binance USD", 18),
        WalletToken("0x1AF3F329e8BE154074D8769D1FFa4eE058B1DBc3", BlockchainNetwork.BINANCE_SMART_CHAIN, "DAI", "Dai Stablecoin", 18),
        WalletToken("0x2170Ed0880ac9A755fd29B2688956BD959F933F8", BlockchainNetwork.BINANCE_SMART_CHAIN, "ETH", "Ethereum", 18),
        WalletToken("0x7130d2A12B9BCbFAe4f2634d864A1Ee1Ce3Ead9c", BlockchainNetwork.BINANCE_SMART_CHAIN, "BTCB", "Bitcoin BEP2", 18),
        WalletToken("0x0E09FaBB73Bd3Ade0a17ECC321fD13a19e81cE82", BlockchainNetwork.BINANCE_SMART_CHAIN, "CAKE", "PancakeSwap", 18),
        WalletToken("0xF8A0BF9cF54Bb92F17374d9e9A321E6a111a51bD", BlockchainNetwork.BINANCE_SMART_CHAIN, "LINK", "Chainlink", 18),
        WalletToken("0xBf5140A22578168FD562DCcF235E5D43A02ce9B1", BlockchainNetwork.BINANCE_SMART_CHAIN, "UNI", "Uniswap", 18),
        WalletToken("0x2859e4544C4bB03966803b044A93563Bd2D0DD4D", BlockchainNetwork.BINANCE_SMART_CHAIN, "SHIB", "Shiba Inu", 18),
        WalletToken("0xcF6BB5389c92Bdda8a3747Ddb454cB7a64626C63", BlockchainNetwork.BINANCE_SMART_CHAIN, "XVS", "Venus", 18),
        WalletToken("0x3EE2200Efb3400fAbB9AacF31297cBdD1d435D47", BlockchainNetwork.BINANCE_SMART_CHAIN, "ADA", "Cardano", 18),
    )

    // ── Polygon (ERC-20) ────────────────────────────────────────────
    val POLYGON_TOKENS = listOf(
        WalletToken("0xc2132D05D31c914a87C6611C10748AEb04B58e8F", BlockchainNetwork.POLYGON, "USDT", "Tether USD", 6),
        WalletToken("0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174", BlockchainNetwork.POLYGON, "USDC", "USD Coin", 6),
        WalletToken("0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359", BlockchainNetwork.POLYGON, "USDC.e", "USD Coin (Native)", 6),
        WalletToken("0x8f3Cf7ad23Cd3CaDbD9735AFf958023239c6A063", BlockchainNetwork.POLYGON, "DAI", "Dai Stablecoin", 18),
        WalletToken("0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619", BlockchainNetwork.POLYGON, "WETH", "Wrapped Ether", 18),
        WalletToken("0x1BFD67037B42Cf73acF2047067bd4F2C47D9BfD6", BlockchainNetwork.POLYGON, "WBTC", "Wrapped Bitcoin", 8),
        WalletToken("0x53E0bca35eC356BD5ddDFebbD1Fc0fD03FaBad39", BlockchainNetwork.POLYGON, "LINK", "Chainlink", 18),
        WalletToken("0xb33EaAd8d922B1083446DC23f610c2567fB5180f", BlockchainNetwork.POLYGON, "UNI", "Uniswap", 18),
        WalletToken("0xD6DF932A45C0f255f85145f286eA0b292B21C90B", BlockchainNetwork.POLYGON, "AAVE", "Aave", 18),
        WalletToken("0x0d500B1d8E8eF31E21C99d1Db9A6444d3ADf1270", BlockchainNetwork.POLYGON, "WMATIC", "Wrapped Matic", 18),
    )

    // ── Arbitrum (ERC-20) ───────────────────────────────────────────
    val ARBITRUM_TOKENS = listOf(
        WalletToken("0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9", BlockchainNetwork.ARBITRUM, "USDT", "Tether USD", 6),
        WalletToken("0xaf88d065e77c8cC2239327C5EDb3A432268e5831", BlockchainNetwork.ARBITRUM, "USDC", "USD Coin", 6),
        WalletToken("0xFF970A61A04b1cA14834A43f5dE4533eBDDB5CC8", BlockchainNetwork.ARBITRUM, "USDC.e", "USD Coin (Bridged)", 6),
        WalletToken("0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1", BlockchainNetwork.ARBITRUM, "DAI", "Dai Stablecoin", 18),
        WalletToken("0x82aF49447D8a07e3bd95BD0d56f35241523fBab1", BlockchainNetwork.ARBITRUM, "WETH", "Wrapped Ether", 18),
        WalletToken("0x2f2a2543B76A4166549F7aaB2e75Bef0aefC5B0f", BlockchainNetwork.ARBITRUM, "WBTC", "Wrapped Bitcoin", 8),
        WalletToken("0xf97f4df75117a78c1A5a0DBb814Af92458539FB4", BlockchainNetwork.ARBITRUM, "LINK", "Chainlink", 18),
        WalletToken("0xFa7F8980b0f1E64A2062791cc3b0871572f1F7f0", BlockchainNetwork.ARBITRUM, "UNI", "Uniswap", 18),
        WalletToken("0x912CE59144191C1204E64559FE8253a0e49E6548", BlockchainNetwork.ARBITRUM, "ARB", "Arbitrum", 18),
        WalletToken("0x5979D7b546E38E414F7E9822514be443A4800529", BlockchainNetwork.ARBITRUM, "wstETH", "Wrapped Lido Staked ETH", 18),
    )

    // ── Optimism (ERC-20) ───────────────────────────────────────────
    val OPTIMISM_TOKENS = listOf(
        WalletToken("0x94b008aA00579c1307B0EF2c499aD98a8ce58e58", BlockchainNetwork.OPTIMISM, "USDT", "Tether USD", 6),
        WalletToken("0x0b2C639c533813f4Aa9D7837CAf62653d097Ff85", BlockchainNetwork.OPTIMISM, "USDC", "USD Coin", 6),
        WalletToken("0x7F5c764cBc14f9669B88837ca1490cCa17c31607", BlockchainNetwork.OPTIMISM, "USDC.e", "USD Coin (Bridged)", 6),
        WalletToken("0xDA10009cBd5D07dd0CeCc66161FC93D7c9000da1", BlockchainNetwork.OPTIMISM, "DAI", "Dai Stablecoin", 18),
        WalletToken("0x4200000000000000000000000000000000000006", BlockchainNetwork.OPTIMISM, "WETH", "Wrapped Ether", 18),
        WalletToken("0x68f180fcCe6836688e9084f035309E29Bf0A2095", BlockchainNetwork.OPTIMISM, "WBTC", "Wrapped Bitcoin", 8),
        WalletToken("0x350a791Bfc2C21F9Ed5d10980Dad2e2638ffa7f6", BlockchainNetwork.OPTIMISM, "LINK", "Chainlink", 18),
        WalletToken("0x4200000000000000000000000000000000000042", BlockchainNetwork.OPTIMISM, "OP", "Optimism", 18),
        WalletToken("0x1F32b1c2345538c0c6f582fCB022739c4A194Ebb", BlockchainNetwork.OPTIMISM, "wstETH", "Wrapped Lido Staked ETH", 18),
    )

    // ── Avalanche C-Chain (ERC-20) ──────────────────────────────────
    val AVALANCHE_TOKENS = listOf(
        WalletToken("0x9702230A8Ea53601f5cD2dc00fDBc13d4dF4A8c7", BlockchainNetwork.AVALANCHE, "USDT", "Tether USD", 6),
        WalletToken("0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E", BlockchainNetwork.AVALANCHE, "USDC", "USD Coin", 6),
        WalletToken("0xd586E7F844cEa2F87f50152665BCbc2C279D8d70", BlockchainNetwork.AVALANCHE, "DAI.e", "Dai Stablecoin", 18),
        WalletToken("0x49D5c2BdFfac6CE2BFdB6640F4F80f226bc10bAB", BlockchainNetwork.AVALANCHE, "WETH.e", "Wrapped Ether", 18),
        WalletToken("0x50b7545627a5162F82A992c33b87aDc75187B218", BlockchainNetwork.AVALANCHE, "WBTC.e", "Wrapped Bitcoin", 8),
        WalletToken("0x5947BB275c521040051D82396192181b413227A3", BlockchainNetwork.AVALANCHE, "LINK.e", "Chainlink", 18),
        WalletToken("0xB31f66AA3C1e785363F0875A1B74E27b85FD66c7", BlockchainNetwork.AVALANCHE, "WAVAX", "Wrapped AVAX", 18),
        WalletToken("0x2b2C81e08f1Af8835a78Bb2A90AE924ACE0eA4bE", BlockchainNetwork.AVALANCHE, "sAVAX", "Staked AVAX", 18),
    )

    // ── Base (ERC-20) ───────────────────────────────────────────────
    val BASE_TOKENS = listOf(
        WalletToken("0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", BlockchainNetwork.BASE, "USDC", "USD Coin", 6),
        WalletToken("0xfde4C96c8593536E31F229EA8f37b2ADa2699bb2", BlockchainNetwork.BASE, "USDT", "Tether USD", 6),
        WalletToken("0x4200000000000000000000000000000000000006", BlockchainNetwork.BASE, "WETH", "Wrapped Ether", 18),
        WalletToken("0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb", BlockchainNetwork.BASE, "DAI", "Dai Stablecoin", 18),
        WalletToken("0x2Ae3F1Ec7F1F5012CFEab0185bfc7aa3cf0DEc22", BlockchainNetwork.BASE, "cbETH", "Coinbase Wrapped Staked ETH", 18),
        WalletToken("0x940181a94A35A4569E4529A3CDfB74e38FD98631", BlockchainNetwork.BASE, "AERO", "Aerodrome Finance", 18),
    )

    // ── Tron TRC-20 ─────────────────────────────────────────────────
    val TRON_TOKENS = listOf(
        WalletToken("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", BlockchainNetwork.TRON, "USDT", "Tether USD", 6),
        WalletToken("TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8", BlockchainNetwork.TRON, "USDC", "USD Coin", 6),
        WalletToken("TUpMhErZL2fhh4sVNULAbNKLokS4GjC1F4", BlockchainNetwork.TRON, "TUSD", "TrueUSD", 18),
        WalletToken("TMwFHYXLJaRUPeW6421aqXL4ZEzPRFGkGT", BlockchainNetwork.TRON, "USDJ", "USDJ", 18),
        WalletToken("TAFjULxiVgT4qWk6UZwjqwZXTSaGaqnVp4", BlockchainNetwork.TRON, "BTT", "BitTorrent", 18),
        WalletToken("TSSMHYeV2uE9qYH95DqyoCuNCzEL1NvU3S", BlockchainNetwork.TRON, "SUN", "SUN", 18),
        WalletToken("TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7", BlockchainNetwork.TRON, "WIN", "WINkLink", 6),
        WalletToken("TCFLL5dx5ZJdKnWuesXxi1VPwjLVmWZZy9", BlockchainNetwork.TRON, "JST", "JUST", 18),
    )

    // ── NEAR Protocol (NEP-141 fungible tokens) ─────────────────────
    val NEAR_TOKENS = listOf(
        WalletToken("wrap.near", BlockchainNetwork.NEAR, "wNEAR", "Wrapped NEAR", 24),
        WalletToken("17208628f84f5d6ad33f0da3bbbeb27ffcb398eac501a31bd6ad2011e36133a1", BlockchainNetwork.NEAR, "USDC", "USD Coin", 6),
        WalletToken("usdt.tether-token.near", BlockchainNetwork.NEAR, "USDT", "Tether USD", 6),
        WalletToken("dac17f958d2ee523a2206206994597c13d831ec7.factory.bridge.near", BlockchainNetwork.NEAR, "USDTe", "Tether USD (Bridged)", 6),
        WalletToken("aaaaaa20d9e0e2461697782ef11675f668207961.factory.bridge.near", BlockchainNetwork.NEAR, "AURORA", "Aurora", 18),
        WalletToken("token.sweat", BlockchainNetwork.NEAR, "SWEAT", "Sweat Economy", 18),
        WalletToken("meta-pool.near", BlockchainNetwork.NEAR, "stNEAR", "Staked NEAR (Meta Pool)", 24),
        WalletToken("linear-protocol.near", BlockchainNetwork.NEAR, "LiNEAR", "LiNEAR Protocol", 24),
        WalletToken("token.v2.ref-finance.near", BlockchainNetwork.NEAR, "REF", "Ref Finance", 18),
        WalletToken("token.burrow.near", BlockchainNetwork.NEAR, "BRRR", "Burrow", 18),
    )

    // ── Solana SPL Tokens (mint addresses) ──────────────────────────
    val SOLANA_TOKENS = listOf(
        WalletToken("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", BlockchainNetwork.SOLANA, "USDT", "Tether USD", 6),
        WalletToken("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", BlockchainNetwork.SOLANA, "USDC", "USD Coin", 6),
        WalletToken("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", BlockchainNetwork.SOLANA, "BONK", "Bonk", 5),
        WalletToken("JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN", BlockchainNetwork.SOLANA, "JUP", "Jupiter", 6),
        WalletToken("EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm", BlockchainNetwork.SOLANA, "WIF", "dogwifhat", 6),
        WalletToken("HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3", BlockchainNetwork.SOLANA, "PYTH", "Pyth Network", 6),
        WalletToken("4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R", BlockchainNetwork.SOLANA, "RAY", "Raydium", 6),
        WalletToken("orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE", BlockchainNetwork.SOLANA, "ORCA", "Orca", 6),
        WalletToken("mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So", BlockchainNetwork.SOLANA, "mSOL", "Marinade Staked SOL", 9),
        WalletToken("jtojtomepa8beP8AuQc6eXt5FriJwfFMwQx2v2f9mCL", BlockchainNetwork.SOLANA, "JTO", "Jito", 9),
        WalletToken("7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs", BlockchainNetwork.SOLANA, "WETH", "Wrapped Ether (Wormhole)", 8),
        WalletToken("3NZ9JMVBmGAqocybic2c7LQCJScmgsAZ6vQqTDzcqmJh", BlockchainNetwork.SOLANA, "WBTC", "Wrapped Bitcoin (Wormhole)", 8),
    )

    /**
     * Get tokens for a specific network
     */
    fun getTokensForNetwork(network: BlockchainNetwork): List<WalletToken> {
        return when (network) {
            BlockchainNetwork.ETHEREUM -> ETHEREUM_TOKENS
            BlockchainNetwork.BINANCE_SMART_CHAIN -> BSC_TOKENS
            BlockchainNetwork.POLYGON -> POLYGON_TOKENS
            BlockchainNetwork.ARBITRUM -> ARBITRUM_TOKENS
            BlockchainNetwork.OPTIMISM -> OPTIMISM_TOKENS
            BlockchainNetwork.AVALANCHE -> AVALANCHE_TOKENS
            BlockchainNetwork.BASE -> BASE_TOKENS
            BlockchainNetwork.TRON -> TRON_TOKENS
            BlockchainNetwork.SOLANA -> SOLANA_TOKENS
            BlockchainNetwork.NEAR -> NEAR_TOKENS
            else -> emptyList()
        }
    }

    /**
     * Get all supported tokens across all networks
     */
    fun getAllTokens(): List<WalletToken> {
        return ETHEREUM_TOKENS + BSC_TOKENS + POLYGON_TOKENS +
               ARBITRUM_TOKENS + OPTIMISM_TOKENS + AVALANCHE_TOKENS +
               BASE_TOKENS + TRON_TOKENS + SOLANA_TOKENS + NEAR_TOKENS
    }
}

/**
 * Transaction status
 */
@Serializable
enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED
}

/**
 * Transaction type
 */
@Serializable
enum class TransactionType {
    SEND,
    RECEIVE,
    SWAP,
    CONTRACT_CALL,
    TOKEN_TRANSFER,
    NFT_TRANSFER,
    STAKE,
    UNSTAKE
}

/**
 * Wallet transaction record
 */
@Serializable
data class WalletTransaction(
    val id: String,
    val txHash: String,
    val network: BlockchainNetwork,
    val type: TransactionType,
    val status: TransactionStatus,
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val amountUsd: Double = 0.0,
    val fee: String = "0",
    val feeUsd: Double = 0.0,
    val tokenSymbol: String? = null,
    val tokenContractAddress: String? = null,
    val timestamp: Long,
    val blockNumber: Long? = null,
    val confirmations: Int = 0,
    val memo: String? = null,
    val rawData: String? = null
) {
    val isOutgoing: Boolean get() = type == TransactionType.SEND
    val isIncoming: Boolean get() = type == TransactionType.RECEIVE
    val isPending: Boolean get() = status == TransactionStatus.PENDING
    val isConfirmed: Boolean get() = status == TransactionStatus.CONFIRMED
    
    fun explorerUrl(): String = network.explorerUrl + txHash
}

/**
 * Gas/Fee estimation result
 */
@Serializable
data class FeeEstimate(
    val network: BlockchainNetwork,
    val slow: FeeOption,
    val normal: FeeOption,
    val fast: FeeOption,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class FeeOption(
    val gasPrice: String,
    val gasLimit: Long,
    val estimatedFee: String,
    val estimatedFeeUsd: Double,
    val estimatedTime: String // e.g., "~30 min", "~5 min", "~1 min"
)

/**
 * Send transaction request
 */
@Serializable
data class SendTransactionRequest(
    val network: BlockchainNetwork,
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val tokenContractAddress: String? = null,
    val memo: String? = null,
    val feeOption: FeeOption? = null
)

/**
 * Wallet creation/recovery data
 */
data class WalletCreationResult(
    val mnemonic: String,
    val accounts: List<WalletAccount>,
    val success: Boolean,
    val error: String? = null
)

/**
 * Address validation result
 */
data class AddressValidation(
    val isValid: Boolean,
    val network: BlockchainNetwork?,
    val message: String? = null
)

/**
 * A single wallet profile — one mnemonic = one wallet.
 * Users can create/import multiple wallets, each with its own name, mnemonic,
 * derived accounts (one per network), tokens, and transaction history.
 */
@Serializable
data class WalletProfile(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

/**
 * Wallet state for UI
 */
@Serializable
data class WalletState(
    val accounts: List<WalletAccount> = emptyList(),
    val tokens: List<WalletToken> = emptyList(),
    val transactions: List<WalletTransaction> = emptyList(),
    val totalBalanceUsd: Double = 0.0,
    val isInitialized: Boolean = false,
    val isLocked: Boolean = true,
    val selectedNetwork: BlockchainNetwork? = null,
    val activeAccountId: String? = null,
    val wallets: List<WalletProfile> = emptyList(),
    val activeWalletId: String? = null,
    val isUpdatingBalances: Boolean = false,
    val isCachedData: Boolean = false
)

data class BalanceSnapshot(
    val accounts: List<WalletAccount>,
    val tokens: List<WalletToken>
)

/**
 * Wallet error types
 */
sealed class WalletError(val message: String) {
    class InvalidMnemonic(msg: String = generalGetString(MR.strings.wallet_error_invalid_mnemonic)) : WalletError(msg)
    class InvalidAddress(msg: String = generalGetString(MR.strings.wallet_error_invalid_address)) : WalletError(msg)
    class InsufficientBalance(msg: String = generalGetString(MR.strings.wallet_error_insufficient_balance)) : WalletError(msg)
    class TransactionFailed(msg: String = generalGetString(MR.strings.wallet_error_transaction_failed)) : WalletError(msg)
    class NetworkError(msg: String = generalGetString(MR.strings.wallet_error_network)) : WalletError(msg)
    class WalletLocked(msg: String = generalGetString(MR.strings.wallet_error_wallet_locked)) : WalletError(msg)
    class UnknownError(msg: String = generalGetString(MR.strings.wallet_error_unknown)) : WalletError(msg)
}

// ═══════════════════════════════════════════════════════════════════
//  In-chat payment invoice
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class PaymentInvoiceStatus {
    PENDING, PAID, EXPIRED, CANCELLED
}

object InvoiceHmac {
    @Volatile
    private var cachedKey: ByteArray? = null

    /**
     * Caches the HMAC signing key derived from the mnemonic.
     * Must be called once while the wallet is unlocked (e.g. on wallet load/unlock).
     */
    fun cacheKey(mnemonic: String) {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        cachedKey = md.digest("invoice-signing:$mnemonic".toByteArray(Charsets.UTF_8))
    }

    /** Wipe the cached key — called when wallet locks. */
    fun clearKey() {
        cachedKey?.let { java.util.Arrays.fill(it, 0) }
        cachedKey = null
    }

    private fun getHmacKey(): ByteArray {
        return cachedKey ?: throw SecurityException(generalGetString(MR.strings.wallet_error_hmac_key_unavailable))
    }

    fun sign(invoice: PaymentInvoice): String {
        val payload = "${invoice.invoiceId}|${invoice.network.name}|${invoice.toAddress}|${invoice.amount}|${invoice.tokenSymbol}|${invoice.createdAt}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(getHmacKey(), "HmacSHA256"))
        val hmacBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(invoice: PaymentInvoice): Boolean {
        val expected = invoice.hmac ?: return false
        return try {
            sign(invoice) == expected
        } catch (_: SecurityException) {
            false
        }
    }
}

@Serializable
data class PaymentInvoice(
    val invoiceId: String,
    val network: BlockchainNetwork,
    val toAddress: String,
    val amount: String,
    val tokenSymbol: String,
    val tokenContractAddress: String? = null,
    val memo: String? = null,
    val status: PaymentInvoiceStatus = PaymentInvoiceStatus.PENDING,
    val txHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val hmac: String? = null
) {
    companion object {
        const val MSG_PREFIX = "__PAY_INV__"
        const val CONFIRM_PREFIX = "__PAY_CONF__"

        fun encode(invoice: PaymentInvoice): String {
            val signed = invoice.copy(hmac = InvoiceHmac.sign(invoice))
            return MSG_PREFIX + kotlinx.serialization.json.Json.encodeToString(serializer(), signed)
        }

        fun verifyHmac(invoice: PaymentInvoice): Boolean = InvoiceHmac.verify(invoice)

        fun decode(text: String): PaymentInvoice? = try {
            if (text.startsWith(MSG_PREFIX))
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(serializer(), text.removePrefix(MSG_PREFIX))
            else null
        } catch (_: Exception) { null }

        fun encodeConfirmation(confirm: PaymentConfirmation): String =
            CONFIRM_PREFIX + kotlinx.serialization.json.Json.encodeToString(PaymentConfirmation.serializer(), confirm)

        fun decodeConfirmation(text: String): PaymentConfirmation? = try {
            if (text.startsWith(CONFIRM_PREFIX))
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString(PaymentConfirmation.serializer(), text.removePrefix(CONFIRM_PREFIX))
            else null
        } catch (_: Exception) { null }

        fun isInvoiceMessage(text: String): Boolean = text.startsWith(MSG_PREFIX)
        fun isConfirmationMessage(text: String): Boolean = text.startsWith(CONFIRM_PREFIX)
        fun isPaymentMessage(text: String): Boolean = isInvoiceMessage(text) || isConfirmationMessage(text)

        fun humanReadableText(raw: String): String {
            if (isInvoiceMessage(raw)) {
                val inv = decode(raw) ?: return generalGetString(MR.strings.wallet_payment_request)
                return String.format(generalGetString(MR.strings.wallet_payment_request_detail), inv.amount, inv.tokenSymbol, inv.network.displayName)
            }
            if (isConfirmationMessage(raw)) {
                val conf = decodeConfirmation(raw) ?: return generalGetString(MR.strings.wallet_payment_sent)
                return String.format(generalGetString(MR.strings.wallet_payment_sent_detail), conf.txHash.take(10))
            }
            return raw
        }
    }
}

@Serializable
data class PaymentConfirmation(
    val invoiceId: String,
    val txHash: String,
    val paidAt: Long = System.currentTimeMillis()
)

