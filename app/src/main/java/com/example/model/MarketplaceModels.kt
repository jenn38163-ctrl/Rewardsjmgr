package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class MarketplaceTab(val label: String) {
    HOME("Home"),
    SHOP("Shop"),
    ORDERS("Orders"),
    WALLET("Wallet"),
    PROFILE("Profile")
}

enum class ShopCategory(val label: String) {
    GIFT_CARDS("Gift Cards"),
    ESIM("eSIM"),
    TOP_UP("Top-up")
}

enum class OrderStatus(val label: String, val color: Color, val containerColor: Color) {
    COMPLETED("Completed", Color(0xFF10B981), Color(0xFF0D382A)),
    PROCESSING("Processing", Color(0xFF818CF8), Color(0xFF28234D)),
    FAILED("Failed", Color(0xFFEF4444), Color(0xFF3B151E))
}

data class GiftCardProduct(
    val id: String,
    val title: String,
    val subtitle: String,
    val minPriceInr: Int,
    val isPopular: Boolean = false,
    val gradientStart: Color,
    val gradientEnd: Color,
    val denominations: List<Int> = listOf(100, 250, 500, 1000, 2500, 5000),
    val provider: String = "Giftbit",
    val description: String = "Instant digital redemption code sent immediately upon payment confirmation."
)

data class EsimPlan(
    val id: String,
    val title: String,
    val subtitle: String,
    val dataAllowance: String,
    val validity: String,
    val countriesCovered: Int,
    val priceInr: Int,
    val region: String,
    val isPopular: Boolean = false,
    val provider: String = "Zendit",
    val features: List<String> = listOf("Instant eSIM QR Delivery", "High-Speed 4G/5G Roaming", "Hotspot / Tethering Enabled")
)

data class TopUpPlan(
    val id: String,
    val operator: String,
    val amountInr: Int,
    val validity: String,
    val data: String,
    val talktime: String,
    val description: String
)

data class MarketplaceOrder(
    val id: String,
    val orderNumber: String,
    val title: String,
    val dateFormatted: String,
    val amountInr: Int,
    val status: OrderStatus,
    val provider: String,
    val recipient: String = "",
    val voucherCode: String? = null,
    val voucherPin: String? = null,
    val itemType: String = "Gift Card"
)

data class TransactionRecord(
    val id: String,
    val title: String,
    val dateFormatted: String,
    val amountInr: Int,
    val isCompleted: Boolean = true
)

data class ResellerProfile(
    val name: String = "John Doe",
    val email: String = "john@example.com",
    val initials: String = "JD",
    val isReseller: Boolean = true,
    val totalOrders: Int = 3,
    val completedOrders: Int = 2,
    val totalSpentInr: Int = 799,
    val zenditLive: Boolean = true,
    val giftbitLive: Boolean = true
)
