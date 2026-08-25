package com.example.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class MarketplaceUiState(
    val currentTab: MarketplaceTab = MarketplaceTab.HOME,
    val searchQuery: String = "",
    val activeShopCategory: ShopCategory = ShopCategory.GIFT_CARDS,
    val activeOrderFilter: String = "All",
    val activeEsimRegion: String = "All",
    val selectedGiftCard: GiftCardProduct? = null,
    val selectedEsimPlan: EsimPlan? = null,
    val selectedOrderDetails: MarketplaceOrder? = null,
    val showResellerModal: Boolean = false,
    val showNotificationDialog: Boolean = false,
    val showTopUpSheet: Boolean = false,
    val showCheckoutSheet: Boolean = false,
    val isProcessingPayment: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val giftCards: List<GiftCardProduct> = emptyList(),
    val esimPlans: List<EsimPlan> = emptyList(),
    val topUpPlans: List<TopUpPlan> = emptyList(),
    val orders: List<MarketplaceOrder> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val profile: ResellerProfile = ResellerProfile()
)

class MarketplaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MarketplaceUiState())
    val uiState: StateFlow<MarketplaceUiState> = _uiState.asStateFlow()

    init {
        loadInitialCatalog()
    }

    private fun loadInitialCatalog() {
        val defaultGiftCards = listOf(
            GiftCardProduct(
                id = "gc_amazon",
                title = "Amazon",
                subtitle = "Amazon.in Gift Card",
                minPriceInr = 100,
                isPopular = true,
                gradientStart = AmazonGradientStart,
                gradientEnd = AmazonGradientEnd,
                denominations = listOf(100, 250, 500, 1000, 2000, 5000)
            ),
            GiftCardProduct(
                id = "gc_googleplay",
                title = "Google Play",
                subtitle = "Google Play Gift Card",
                minPriceInr = 100,
                isPopular = true,
                gradientStart = GooglePlayGradientStart,
                gradientEnd = GooglePlayGradientEnd,
                denominations = listOf(100, 300, 500, 1000, 1500)
            ),
            GiftCardProduct(
                id = "gc_flipkart",
                title = "Flipkart",
                subtitle = "Flipkart Digital E-Voucher",
                minPriceInr = 150,
                isPopular = true,
                gradientStart = FlipkartGradientStart,
                gradientEnd = FlipkartGradientEnd,
                denominations = listOf(150, 500, 1000, 2500)
            ),
            GiftCardProduct(
                id = "gc_netflix",
                title = "Netflix",
                subtitle = "Netflix Gift Card",
                minPriceInr = 299,
                isPopular = false,
                gradientStart = NetflixGradientStart,
                gradientEnd = NetflixGradientEnd,
                denominations = listOf(299, 499, 649, 1000)
            ),
            GiftCardProduct(
                id = "gc_steam",
                title = "Steam",
                subtitle = "Steam Wallet Gift Card",
                minPriceInr = 250,
                isPopular = false,
                gradientStart = SteamGradientStart,
                gradientEnd = SteamGradientEnd,
                denominations = listOf(250, 500, 1000, 2500)
            ),
            GiftCardProduct(
                id = "gc_spotify",
                title = "Spotify",
                subtitle = "Spotify Premium Voucher",
                minPriceInr = 119,
                isPopular = false,
                gradientStart = Color(0xFF1DB954),
                gradientEnd = Color(0xFF121212),
                denominations = listOf(119, 299, 599, 1189)
            ),
            GiftCardProduct(
                id = "gc_apple",
                title = "Apple Store",
                subtitle = "App Store & iTunes Code",
                minPriceInr = 200,
                isPopular = false,
                gradientStart = Color(0xFF475569),
                gradientEnd = Color(0xFF0F172A),
                denominations = listOf(200, 500, 1000, 2000, 5000)
            ),
            GiftCardProduct(
                id = "gc_playstation",
                title = "PlayStation",
                subtitle = "PSN Wallet Top-up Code",
                minPriceInr = 500,
                isPopular = false,
                gradientStart = Color(0xFF003791),
                gradientEnd = Color(0xFF001F52),
                denominations = listOf(500, 1000, 2000, 4000)
            )
        )

        val defaultEsims = listOf(
            EsimPlan(
                id = "esim_global_1gb",
                title = "Global 1GB",
                subtitle = "1GB Data — 30 countries",
                dataAllowance = "1 GB",
                validity = "30 days",
                countriesCovered = 30,
                priceInr = 299,
                region = "Global",
                isPopular = true
            ),
            EsimPlan(
                id = "esim_asia_3gb",
                title = "Asia 3GB",
                subtitle = "3GB Data — 15 Asian countries",
                dataAllowance = "3 GB",
                validity = "30 days",
                countriesCovered = 15,
                priceInr = 499,
                region = "Asia",
                isPopular = true
            ),
            EsimPlan(
                id = "esim_europe_5gb",
                title = "Europe 5GB",
                subtitle = "5GB High-Speed — 33 EU Nations",
                dataAllowance = "5 GB",
                validity = "30 days",
                countriesCovered = 33,
                priceInr = 699,
                region = "Europe",
                isPopular = false
            ),
            EsimPlan(
                id = "esim_usa_10gb",
                title = "USA & Canada 10GB",
                subtitle = "10GB 5G Ultra Wideband",
                dataAllowance = "10 GB",
                validity = "30 days",
                countriesCovered = 2,
                priceInr = 999,
                region = "Americas",
                isPopular = false
            ),
            EsimPlan(
                id = "esim_global_unlimited",
                title = "Global 20GB Pro",
                subtitle = "20GB Unlimited Global Roaming",
                dataAllowance = "20 GB",
                validity = "60 days",
                countriesCovered = 80,
                priceInr = 1799,
                region = "Global",
                isPopular = false
            )
        )

        val defaultTopUps = listOf(
            TopUpPlan("topup_jio_239", "Jio", 239, "28 Days", "1.5 GB/day", "Unlimited Calls", "Trending 1.5GB/day 5G welcome plan"),
            TopUpPlan("topup_jio_299", "Jio", 299, "28 Days", "2.0 GB/day", "Unlimited Calls", "2GB/Day high speed + Jio Cinema"),
            TopUpPlan("topup_airtel_299", "Airtel", 299, "28 Days", "1.5 GB/day", "Unlimited Calls", "True 5G Unlimited data included"),
            TopUpPlan("topup_airtel_719", "Airtel", 719, "84 Days", "1.5 GB/day", "Unlimited Calls", "Best value 3-month plan"),
            TopUpPlan("topup_vi_299", "Vodafone Idea", 299, "28 Days", "1.5 GB/day", "Unlimited Calls", "Binge all night free 12am-6am")
        )

        val defaultOrders = listOf(
            MarketplaceOrder(
                id = "ord_001",
                orderNumber = "#ORD-001",
                title = "Amazon Gift Card ₹500",
                dateFormatted = "15 Aug 2026",
                amountInr = 500,
                status = OrderStatus.COMPLETED,
                provider = "Giftbit",
                recipient = "user@example.com",
                voucherCode = "AMZ-9948-2819-KLAX",
                voucherPin = "4820",
                itemType = "Gift Card"
            ),
            MarketplaceOrder(
                id = "ord_002",
                orderNumber = "#ORD-002",
                title = "Global 1GB eSIM",
                dateFormatted = "14 Aug 2026",
                amountInr = 299,
                status = OrderStatus.COMPLETED,
                provider = "Zendit",
                recipient = "ICCID: 89014103211118510720",
                voucherCode = "LPA:1\$smdp.io\$ZENDIT-9281-KLA",
                voucherPin = "N/A",
                itemType = "eSIM Plan"
            ),
            MarketplaceOrder(
                id = "ord_003",
                orderNumber = "#ORD-003",
                title = "Jio Recharge ₹239",
                dateFormatted = "16 Aug 2026",
                amountInr = 239,
                status = OrderStatus.PROCESSING,
                provider = "Zendit",
                recipient = "+91 98765 43210",
                voucherCode = "TXN-JIO-8831920",
                voucherPin = "ACTIVE",
                itemType = "Mobile Top-up"
            )
        )

        val defaultTransactions = listOf(
            TransactionRecord("tx_1", "Amazon Gift Card ₹500", "15 Aug", 500, true),
            TransactionRecord("tx_2", "Global 1GB eSIM", "14 Aug", 299, true),
            TransactionRecord("tx_3", "Jio Recharge ₹239", "16 Aug", 239, false)
        )

        _uiState.update {
            it.copy(
                giftCards = defaultGiftCards,
                esimPlans = defaultEsims,
                topUpPlans = defaultTopUps,
                orders = defaultOrders,
                transactions = defaultTransactions
            )
        }
    }

    fun setTab(tab: MarketplaceTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setShopCategory(category: ShopCategory) {
        _uiState.update { it.copy(activeShopCategory = category) }
    }

    fun setOrderFilter(filter: String) {
        _uiState.update { it.copy(activeOrderFilter = filter) }
    }

    fun setEsimRegion(region: String) {
        _uiState.update { it.copy(activeEsimRegion = region) }
    }

    fun selectGiftCard(card: GiftCardProduct?) {
        _uiState.update { it.copy(selectedGiftCard = card) }
    }

    fun selectEsimPlan(plan: EsimPlan?) {
        _uiState.update { it.copy(selectedEsimPlan = plan) }
    }

    fun selectOrderDetails(order: MarketplaceOrder?) {
        _uiState.update { it.copy(selectedOrderDetails = order) }
    }

    fun showResellerModal(show: Boolean) {
        _uiState.update { it.copy(showResellerModal = show) }
    }

    fun showNotificationDialog(show: Boolean) {
        _uiState.update { it.copy(showNotificationDialog = show) }
    }

    fun showTopUpSheet(show: Boolean) {
        _uiState.update { it.copy(showTopUpSheet = show) }
    }

    fun placeOrder(
        title: String,
        amountInr: Int,
        provider: String,
        recipient: String,
        itemType: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPayment = true, errorMessage = null) }
            delay(1200) // Realistic secure checkout simulation

            val orderNum = "#ORD-${String.format(Locale.US, "%03d", _uiState.value.orders.size + 1)}"
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
            val dateStr = sdf.format(Date())

            val randomCode = "VOUCH-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
            val randomPin = "${(1000..9999).random()}"

            val newOrder = MarketplaceOrder(
                id = "ord_${System.currentTimeMillis()}",
                orderNumber = orderNum,
                title = title,
                dateFormatted = dateStr,
                amountInr = amountInr,
                status = OrderStatus.COMPLETED,
                provider = provider,
                recipient = recipient.ifBlank { "john@example.com" },
                voucherCode = randomCode,
                voucherPin = randomPin,
                itemType = itemType
            )

            val newTx = TransactionRecord(
                id = "tx_${System.currentTimeMillis()}",
                title = title,
                dateFormatted = SimpleDateFormat("dd MMM", Locale.US).format(Date()),
                amountInr = amountInr,
                isCompleted = true
            )

            val updatedOrders = listOf(newOrder) + _uiState.value.orders
            val updatedTxs = listOf(newTx) + _uiState.value.transactions
            val updatedProfile = _uiState.value.profile.copy(
                totalOrders = updatedOrders.size,
                completedOrders = updatedOrders.count { it.status == OrderStatus.COMPLETED },
                totalSpentInr = _uiState.value.profile.totalSpentInr + amountInr
            )

            _uiState.update {
                it.copy(
                    isProcessingPayment = false,
                    selectedGiftCard = null,
                    selectedEsimPlan = null,
                    showTopUpSheet = false,
                    orders = updatedOrders,
                    transactions = updatedTxs,
                    profile = updatedProfile,
                    successMessage = "Order $orderNum confirmed! Digital code issued.",
                    selectedOrderDetails = newOrder
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
