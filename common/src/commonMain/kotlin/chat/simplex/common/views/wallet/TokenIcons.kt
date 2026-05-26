package chat.simplex.common.views.wallet

import chat.simplex.res.MR
import dev.icerock.moko.resources.ImageResource

/**
 * Centralized token icon mapping utility
 * Used by both MarketView and WalletView to avoid duplication
 */
object TokenIcons {
    
    /**
     * Get icon resource for a token by CoinGecko ID (preferred) or symbol
     */
    fun getIcon(coinId: String? = null, symbol: String): ImageResource? {
        // Prefer CoinGecko id mapping first (more reliable than symbol)
        coinId?.let { id ->
            getIconById(id)?.let { return it }
        }
        
        // Fallback to symbol mapping
        return getIconBySymbol(symbol)
    }
    
    /**
     * Get icon by CoinGecko ID
     */
    private fun getIconById(id: String): ImageResource? {
        return when (id) {
            "bitcoin" -> MR.images.ic_btc_logo
            "ethereum" -> MR.images.ic_eth_logo
            "tether" -> MR.images.ic_usdt_logo
            "usd-coin" -> MR.images.ic_usdc_logo
            "binancecoin" -> MR.images.ic_bnb_logo
            "solana" -> MR.images.ic_sol_logo
            "ripple" -> MR.images.ic_xrp_logo
            "cardano" -> MR.images.ic_ada_logo
            "dogecoin" -> MR.images.ic_doge_logo
            "polkadot" -> MR.images.ic_dot_logo
            "avalanche-2" -> MR.images.ic_avax_logo
            "tron" -> MR.images.ic_trx_logo
            "bitcoin-cash" -> MR.images.ic_bch_logo
            "litecoin" -> MR.images.ic_ltc_logo
            "chainlink" -> MR.images.ic_link_logo
            "stellar" -> MR.images.ic_xlm_logo
            "monero" -> MR.images.ic_xmr_logo
            "filecoin" -> MR.images.ic_fil_logo
            "maker" -> MR.images.ic_mkr_logo
            "shiba-inu" -> MR.images.ic_shib_logo
            "pepe" -> MR.images.ic_pepe_logo
            "bonk" -> MR.images.ic_bonk_logo
            "floki" -> MR.images.ic_floki_logo
            "dash" -> MR.images.ic_dash_logo
            "neo" -> MR.images.ic_neo_logo
            "iota" -> MR.images.ic_iota_logo
            "eos" -> MR.images.ic_eos_logo
            "tezos" -> MR.images.ic_xtz_logo
            "theta-token" -> MR.images.ic_theta_logo
            "fantom" -> MR.images.ic_ftm_logo
            "algorand" -> MR.images.ic_algo_logo
            "vechain" -> MR.images.ic_vet_logo
            "hedera-hashgraph" -> MR.images.ic_hbar_logo
            "aave" -> MR.images.ic_aave_logo
            "uniswap" -> MR.images.ic_uni_logo
            "optimism" -> MR.images.ic_op_logo
            "arbitrum" -> MR.images.ic_arb_logo
            "aptos" -> MR.images.ic_apt_logo
            "near" -> MR.images.ic_near_logo
            "cosmos" -> MR.images.ic_atom_logo
            "polygon-ecosystem-token", "matic-network" -> MR.images.ic_matic_logo
            "zcash" -> MR.images.ic_zcash_logo
            "dai" -> MR.images.ic_usdc_logo // DAI uses similar icon
            "wrapped-bitcoin" -> MR.images.ic_btc_logo
            "weth" -> MR.images.ic_eth_logo
            "wrapped-avax" -> MR.images.ic_avax_logo
            "pancakeswap-token" -> MR.images.ic_bnb_logo
            else -> null
        }
    }
    
    /**
     * Get icon by token symbol
     */
    private fun getIconBySymbol(symbol: String): ImageResource? {
        return when (symbol.uppercase()) {
            "BTC", "WBTC", "BTCB" -> MR.images.ic_btc_logo
            "ETH", "WETH" -> MR.images.ic_eth_logo
            "USDT" -> MR.images.ic_usdt_logo
            "USDC" -> MR.images.ic_usdc_logo
            "BNB", "WBNB" -> MR.images.ic_bnb_logo
            "SOL" -> MR.images.ic_sol_logo
            "XRP" -> MR.images.ic_xrp_logo
            "ADA" -> MR.images.ic_ada_logo
            "DOGE" -> MR.images.ic_doge_logo
            "DOT" -> MR.images.ic_dot_logo
            "AVAX", "WAVAX" -> MR.images.ic_avax_logo
            "TRX" -> MR.images.ic_trx_logo
            "BCH" -> MR.images.ic_bch_logo
            "LTC" -> MR.images.ic_ltc_logo
            "LINK" -> MR.images.ic_link_logo
            "XLM" -> MR.images.ic_xlm_logo
            "XMR" -> MR.images.ic_xmr_logo
            "SHIB" -> MR.images.ic_shib_logo
            "MATIC", "WMATIC", "POL" -> MR.images.ic_matic_logo
            "ATOM" -> MR.images.ic_atom_logo
            "UNI" -> MR.images.ic_uni_logo
            "OP" -> MR.images.ic_op_logo
            "ARB" -> MR.images.ic_arb_logo
            "APT" -> MR.images.ic_apt_logo
            "NEAR", "WNEAR", "STNEAR", "LINEAR" -> MR.images.ic_near_logo
            "FIL" -> MR.images.ic_fil_logo
            "MKR" -> MR.images.ic_mkr_logo
            "XTZ" -> MR.images.ic_xtz_logo
            "AAVE" -> MR.images.ic_aave_logo
            "DAI" -> MR.images.ic_usdc_logo
            "PEPE" -> MR.images.ic_pepe_logo
            "BONK" -> MR.images.ic_bonk_logo
            "FLOKI" -> MR.images.ic_floki_logo
            "CAKE" -> MR.images.ic_bnb_logo
            "BUSD", "TUSD" -> MR.images.ic_usdt_logo
            "LDO" -> MR.images.ic_eth_logo
            "ETC" -> MR.images.ic_logo
            else -> null
        }
    }
    
    /**
     * Get icon for a blockchain network
     */
    fun getNetworkIcon(network: BlockchainNetwork): ImageResource? {
        return when (network) {
            BlockchainNetwork.ETHEREUM -> MR.images.ic_eth_logo
            BlockchainNetwork.BINANCE_SMART_CHAIN -> MR.images.ic_bnb_logo
            BlockchainNetwork.POLYGON -> MR.images.ic_matic_logo
            BlockchainNetwork.ARBITRUM -> MR.images.ic_arb_logo
            BlockchainNetwork.OPTIMISM -> MR.images.ic_op_logo
            BlockchainNetwork.AVALANCHE -> MR.images.ic_avax_logo
            BlockchainNetwork.BASE -> MR.images.ic_eth_logo
            BlockchainNetwork.BITCOIN -> MR.images.ic_btc_logo
            BlockchainNetwork.SOLANA -> MR.images.ic_sol_logo
            BlockchainNetwork.CARDANO -> MR.images.ic_ada_logo
            BlockchainNetwork.DOGECOIN -> MR.images.ic_doge_logo
            BlockchainNetwork.LITECOIN -> MR.images.ic_ltc_logo
            BlockchainNetwork.RIPPLE -> MR.images.ic_xrp_logo
            BlockchainNetwork.TRON -> MR.images.ic_trx_logo
            BlockchainNetwork.NEAR -> MR.images.ic_near_logo
            else -> null
        }
    }
}

