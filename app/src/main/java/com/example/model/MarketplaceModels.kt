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

/**
 * State Machine Definition for Gateway Orders
 * 
 * Valid Transitions:
 * - CREATED / PENDING -> PROCESSING
 * - PROCESSING -> COMPLETED (Terminal)
 * - PROCESSING -> FAILED_RETRYABLE (Retryable)
 * - FAILED_RETRYABLE -> PROCESSING -> COMPLETED
 * - FAILED_RETRYABLE -> FAILED_TERMINAL (Exhausted retries)
 * - FAILED_TERMINAL -> REFUNDED
 */
enum class GatewayOrderState(
    val tag: String,
    val label: String,
    val subtitle: String,
    val color: Color,
    val containerColor: Color,
    val borderColor: Color,
    val canRetry: Boolean,
    val isTerminal: Boolean
) {
    PENDING(
        tag = "PENDING",
        label = "Pending",
        subtitle = "Awaiting verification & gateway queue",
        color = Color(0xFF38BDF8),
        containerColor = Color(0xFF0C2B40),
        borderColor = Color(0xFF0284C7),
        canRetry = false,
        isTerminal = false
    ),
    PROCESSING(
        tag = "PROCESSING",
        label = "Processing",
        subtitle = "Dispatched to provider (Zendit / Giftbit)",
        color = Color(0xFFA78BFA),
        containerColor = Color(0xFF28234D),
        borderColor = Color(0xFF6D28D9),
        canRetry = false,
        isTerminal = false
    ),
    COMPLETED(
        tag = "COMPLETED",
        label = "Completed",
        subtitle = "Fulfillment delivered & voucher active",
        color = Color(0xFF10B981),
        containerColor = Color(0xFF0D382A),
        borderColor = Color(0xFF059669),
        canRetry = false,
        isTerminal = true
    ),
    FAILED_RETRYABLE(
        tag = "FAILED_RETRYABLE",
        label = "Failed (Retryable)",
        subtitle = "Provider timeout / throttle. Ready to retry.",
        color = Color(0xFFF59E0B),
        containerColor = Color(0xFF3D2706),
        borderColor = Color(0xFFD97706),
        canRetry = true,
        isTerminal = false
    ),
    FAILED_TERMINAL(
        tag = "FAILED_TERMINAL",
        label = "Failed (Terminal)",
        subtitle = "Permanent rejection or invalid credentials",
        color = Color(0xFFEF4444),
        containerColor = Color(0xFF3B151E),
        borderColor = Color(0xFFDC2626),
        canRetry = false,
        isTerminal = true
    ),
    REFUNDED(
        tag = "REFUNDED",
        label = "Refunded",
        subtitle = "Funds credited back to user wallet",
        color = Color(0xFF94A3B8),
        containerColor = Color(0xFF1E293B),
        borderColor = Color(0xFF475569),
        canRetry = false,
        isTerminal = true
    );

    companion object {
        fun fromString(stateStr: String?): GatewayOrderState {
            return when (stateStr?.uppercase()) {
                "COMPLETED" -> COMPLETED
                "PENDING" -> PENDING
                "FAILED_RETRYABLE" -> FAILED_RETRYABLE
                "PROCESSING" -> PROCESSING
                "FAILED", "FAILED_TERMINAL" -> FAILED_TERMINAL
                "REFUNDED" -> REFUNDED
                else -> PENDING
            }
        }
    }
}

// Retain OrderStatus alias for backwards compatibility
typealias OrderStatus = GatewayOrderState

data class OrderTimelineStep(
    val title: String,
    val timestamp: String,
    val isCompleted: Boolean,
    val isCurrent: Boolean,
    val isError: Boolean = false,
    val description: String = ""
)

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
    val status: GatewayOrderState,
    val provider: String,
    val recipient: String = "",
    val voucherCode: String? = null,
    val voucherPin: String? = null,
    val itemType: String = "Gift Card",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val failureReason: String? = null,
    val timeline: List<OrderTimelineStep> = emptyList()
) {
    val state: GatewayOrderState get() = status
}

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
    val totalOrders: Int = 4,
    val completedOrders: Int = 2,
    val totalSpentInr: Int = 1299,
    val zenditLive: Boolean = true,
    val giftbitLive: Boolean = true
)

