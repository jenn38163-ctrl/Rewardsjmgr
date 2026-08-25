package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MarketplaceOrder
import com.example.model.OrderStatus
import com.example.ui.components.OrderItemCard
import com.example.ui.theme.*

@Composable
fun OrdersScreen(
    orders: List<MarketplaceOrder>,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    onSelectOrder: (MarketplaceOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf("All", "Completed", "Processing", "Failed")

    val filteredOrders = when (activeFilter) {
        "Completed" -> orders.filter { it.status == OrderStatus.COMPLETED }
        "Processing" -> orders.filter { it.status == OrderStatus.PROCESSING }
        "Failed" -> orders.filter { it.status == OrderStatus.FAILED }
        else -> orders
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "My Orders",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters) { filter ->
                val isSelected = activeFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (isSelected) TealAccentContainer else DarkSurface)
                        .border(
                            1.dp,
                            if (isSelected) TealAccent else DarkSurfaceBorder,
                            RoundedCornerShape(100.dp)
                        )
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) TealAccent else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No orders found in $activeFilter",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredOrders) { order ->
                    OrderItemCard(
                        order = order,
                        onClick = { onSelectOrder(order) }
                    )
                }
            }
        }
    }
}
