package chat.simplex.common.platform

data class DexgramProduct(
    val productId: String,
    val title: String,
    val price: String,
    val durationLabel: String
)

data class DexgramPurchaseResult(
    val success: Boolean,
    val productId: String = "",
    val purchaseToken: String = "",
    val errorMessage: String = ""
)

expect object DexgramBilling {
    val isAvailable: Boolean
    suspend fun connect(): Boolean
    suspend fun queryProducts(): List<DexgramProduct>
    suspend fun purchase(productId: String): DexgramPurchaseResult
    fun disconnect()
}
