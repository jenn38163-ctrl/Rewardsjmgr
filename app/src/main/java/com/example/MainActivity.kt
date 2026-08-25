package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MarketplaceTab
import com.example.model.ShopCategory
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.MarketplaceViewModel

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Order Notifications"
        val descriptionText = "Instant digital goods delivery alerts"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("ORDERS_CHANNEL", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showNotification(context: Context, title: String, message: String) {
    val builder = NotificationCompat.Builder(context, "ORDERS_CHANNEL")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.example.network.SessionManager.init(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        createNotificationChannel(this)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MarketplaceViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current

                LaunchedEffect(uiState.successMessage) {
                    uiState.successMessage?.let { msg ->
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short
                        )
                        showNotification(context, "Order Delivered", msg)
                        viewModel.dismissMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBg,
                    contentColor = TextPrimary,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        MarketplaceBottomBar(
                            currentTab = uiState.currentTab,
                            onTabSelected = { viewModel.setTab(it) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.currentTab) {
                            MarketplaceTab.HOME -> {
                                HomeScreen(
                                    giftCards = uiState.giftCards,
                                    esimPlans = uiState.esimPlans,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onSelectGiftCard = { viewModel.selectGiftCard(it) },
                                    onSelectEsimPlan = { viewModel.selectEsimPlan(it) },
                                    onNavigateToCategory = { category ->
                                        viewModel.setShopCategory(category)
                                        viewModel.setTab(MarketplaceTab.SHOP)
                                    },
                                    onNavigateToOrders = { viewModel.setTab(MarketplaceTab.ORDERS) },
                                    onNavigateToShop = { viewModel.setTab(MarketplaceTab.SHOP) },
                                    onOpenResellerModal = { viewModel.showResellerModal(true) },
                                    onNotificationClick = { viewModel.showNotificationDialog(true) }
                                )
                            }

                            MarketplaceTab.SHOP -> {
                                ShopScreen(
                                    giftCards = uiState.giftCards,
                                    esimPlans = uiState.esimPlans,
                                    topUpPlans = uiState.topUpPlans,
                                    activeCategory = uiState.activeShopCategory,
                                    activeRegion = uiState.activeEsimRegion,
                                    onCategoryChange = { viewModel.setShopCategory(it) },
                                    onRegionChange = { viewModel.setEsimRegion(it) },
                                    onSelectGiftCard = { viewModel.selectGiftCard(it) },
                                    onSelectEsimPlan = { viewModel.selectEsimPlan(it) },
                                    onOpenTopUpSheet = { viewModel.showTopUpSheet(true) }
                                )
                            }

                            MarketplaceTab.ORDERS -> {
                                OrdersScreen(
                                    orders = uiState.orders,
                                    activeFilter = uiState.activeOrderFilter,
                                    onFilterChange = { viewModel.setOrderFilter(it) },
                                    onSelectOrder = { viewModel.selectOrderDetails(it) }
                                )
                            }

                            MarketplaceTab.WALLET -> {
                                WalletScreen(
                                    totalSpentInr = uiState.profile.totalSpentInr,
                                    completedOrdersCount = uiState.profile.completedOrders,
                                    transactions = uiState.transactions
                                )
                            }

                            MarketplaceTab.PROFILE -> {
                                ProfileScreen(
                                    profile = uiState.profile,
                                    onOpenApiKeys = { viewModel.showResellerModal(true) },
                                    onOpenResellerModal = { viewModel.showResellerModal(true) }
                                )
                            }
                        }
                    }
                }

                // Modal Dialogs
                uiState.selectedGiftCard?.let { giftCard ->
                    GiftCardPurchaseDialog(
                        giftCard = giftCard,
                        isProcessing = uiState.isProcessingPayment,
                        onDismiss = { viewModel.selectGiftCard(null) },
                        onConfirm = { amount, recipient ->
                            viewModel.placeOrder(
                                title = "${giftCard.title} Gift Card ₹$amount",
                                amountInr = amount,
                                provider = giftCard.provider,
                                recipient = recipient,
                                itemType = "Gift Card"
                            )
                        }
                    )
                }

                uiState.selectedEsimPlan?.let { esim ->
                    EsimPurchaseDialog(
                        plan = esim,
                        isProcessing = uiState.isProcessingPayment,
                        onDismiss = { viewModel.selectEsimPlan(null) },
                        onConfirm = {
                            viewModel.placeOrder(
                                title = "${esim.title} eSIM (${esim.dataAllowance})",
                                amountInr = esim.priceInr,
                                provider = esim.provider,
                                recipient = "ICCID: 89014103211118510${(1000..9999).random()}",
                                itemType = "eSIM Plan"
                            )
                        }
                    )
                }

                if (uiState.showTopUpSheet) {
                    TopUpSheetDialog(
                        topUpPlans = uiState.topUpPlans,
                        isProcessing = uiState.isProcessingPayment,
                        onDismiss = { viewModel.showTopUpSheet(false) },
                        onRecharge = { operator, phone, plan ->
                            viewModel.placeOrder(
                                title = "$operator Recharge ₹${plan.amountInr}",
                                amountInr = plan.amountInr,
                                provider = "Zendit",
                                recipient = phone,
                                itemType = "Mobile Top-up"
                            )
                        }
                    )
                }

                uiState.selectedOrderDetails?.let { order ->
                    OrderDetailsDialog(
                        order = order,
                        onDismiss = { viewModel.selectOrderDetails(null) }
                    )
                }

                if (uiState.showResellerModal) {
                    ResellerApiKeysDialog(
                        onDismiss = { viewModel.showResellerModal(false) }
                    )
                }

                if (uiState.showNotificationDialog) {
                    NotificationHistoryDialog(
                        onDismiss = { viewModel.showNotificationDialog(false) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationHistoryDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = TealAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Notifications", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(DarkBg)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(text = "Order Delivered · Amazon ₹500", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Your digital gift voucher is active and ready to redeem.", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(DarkBg)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(text = "Zendit Gateway Synced", color = TealAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Live eSIM & Top-up adapters verified on production gateway.", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccentContainer)
                ) {
                    Text("Close", color = TealAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
