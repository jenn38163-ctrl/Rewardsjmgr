package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GatewayOrderState
import com.example.model.MarketplaceOrder
import com.example.ui.components.OrderItemCard
import com.example.ui.theme.*

@Composable
fun OrdersScreen(
    orders: List<MarketplaceOrder>,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    onSelectOrder: (MarketplaceOrder) -> Unit,
    onRefreshOrders: () -> Unit,
    onRetryOrder: (String) -> Unit,
    isFetching: Boolean = false,
    isRetrying: Boolean = false,
    gatewayLatencyMs: Long = 24,
    lastSyncedTime: String = "Just now",
    modifier: Modifier = Modifier
) {
    val filters = listOf("All", "Completed", "Pending", "Processing", "Needs Retry")

    val filteredOrders = when (activeFilter) {
        "Completed" -> orders.filter { it.status == GatewayOrderState.COMPLETED }
        "Pending" -> orders.filter { it.status == GatewayOrderState.PENDING }
        "Processing" -> orders.filter { it.status == GatewayOrderState.PROCESSING }
        "Needs Retry" -> orders.filter { it.status == GatewayOrderState.FAILED_RETRYABLE }
        else -> orders
    }

    // Counts for state machine status overview
    val completedCount = orders.count { it.status == GatewayOrderState.COMPLETED }
    val pendingCount = orders.count { it.status == GatewayOrderState.PENDING }
    val processingCount = orders.count { it.status == GatewayOrderState.PROCESSING }
    val retryableCount = orders.count { it.status == GatewayOrderState.FAILED_RETRYABLE }

    val infiniteTransition = rememberInfiniteTransition(label = "spin_transition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header with Gateway Status Indicator & Refresh Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Gateway Orders",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TealAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NestJS Gateway: Live (${gatewayLatencyMs}ms) • $lastSyncedTime",
                        color = TealAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Sync with Gateway Button
            IconButton(
                onClick = onRefreshOrders,
                enabled = !isFetching,
                modifier = Modifier
                    .testTag("btn_sync_orders")
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Gateway",
                    tint = if (isFetching) TealAccent else TextPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (isFetching) Modifier.rotate(rotationAngle) else Modifier)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // State Machine Metrics Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Completed Metric Card
            StateMetricPill(
                label = "COMPLETED",
                count = completedCount,
                color = Color(0xFF10B981),
                containerColor = Color(0xFF062B1D),
                modifier = Modifier.weight(1f)
            )

            // Pending Metric Card
            StateMetricPill(
                label = "PENDING",
                count = pendingCount,
                color = Color(0xFF38BDF8),
                containerColor = Color(0xFF0B243B),
                modifier = Modifier.weight(1f)
            )

            // Needs Retry Metric Card
            StateMetricPill(
                label = "RETRYABLE",
                count = retryableCount,
                color = Color(0xFFF59E0B),
                containerColor = Color(0xFF3D2706),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters) { filter ->
                val isSelected = activeFilter == filter
                val count = when (filter) {
                    "Completed" -> completedCount
                    "Pending" -> pendingCount
                    "Processing" -> processingCount
                    "Needs Retry" -> retryableCount
                    else -> orders.size
                }

                Box(
                    modifier = Modifier
                        .testTag("filter_chip_$filter")
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (isSelected) TealAccentContainer else DarkSurface)
                        .border(
                            1.dp,
                            if (isSelected) TealAccent else DarkSurfaceBorder,
                            RoundedCornerShape(100.dp)
                        )
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = filter,
                            color = if (isSelected) TealAccent else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSelected) TealAccent.copy(alpha = 0.2f) else DarkBg)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) TealAccent else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No orders found in $activeFilter",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Orders will update automatically as gateway events resolve.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRefreshOrders,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh Gateway Orders", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderItemCard(
                        order = order,
                        onClick = { onSelectOrder(order) },
                        onRetry = if (order.status == GatewayOrderState.FAILED_RETRYABLE) {
                            { onRetryOrder(order.id) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun StateMetricPill(
    label: String,
    count: Int,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
