package chat.simplex.common.platform

actual object DexgramBilling {
    actual val isAvailable: Boolean = false

    actual suspend fun connect(): Boolean = false

    actual suspend fun queryProducts(): List<DexgramProduct> = emptyList()

    actual suspend fun purchase(productId: String): DexgramPurchaseResult =
        DexgramPurchaseResult(success = false, errorMessage = "Not available on desktop")

    actual fun disconnect() {}
}
