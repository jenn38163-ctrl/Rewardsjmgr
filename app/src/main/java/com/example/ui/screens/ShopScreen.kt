package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ShopScreen(
    giftCards: List<GiftCardProduct>,
    esimPlans: List<EsimPlan>,
    topUpPlans: List<TopUpPlan>,
    activeCategory: ShopCategory,
    activeRegion: String,
    onCategoryChange: (ShopCategory) -> Unit,
    onRegionChange: (String) -> Unit,
    onSelectGiftCard: (GiftCardProduct) -> Unit,
    onSelectEsimPlan: (EsimPlan) -> Unit,
    onOpenTopUpSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header: Shop + Cart Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Shop",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { /* View active checkout/cart */ },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkSurfaceBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Cart",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Top Category Tabs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val categories = listOf(
                Pair(ShopCategory.GIFT_CARDS, Icons.Outlined.CardGiftcard),
                Pair(ShopCategory.ESIM, Icons.Outlined.SimCard),
                Pair(ShopCategory.TOP_UP, Icons.Outlined.Smartphone)
            )

            categories.forEach { (cat, icon) ->
                val isSelected = activeCategory == cat
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCategoryChange(cat) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = cat.label,
                            tint = if (isSelected) AmberAccent else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cat.label,
                            color = if (isSelected) TextPrimary else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    // Amber active indicator bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(if (isSelected) AmberAccent else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        when (activeCategory) {
            ShopCategory.GIFT_CARDS -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(giftCards) { card ->
                        GiftCardGridCard(
                            giftCard = card,
                            onClick = { onSelectGiftCard(card) }
                        )
                    }
                }
            }

            ShopCategory.ESIM -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Region Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val regions = listOf("All", "Global", "Asia", "Europe", "Americas")
                        items(regions) { reg ->
                            val isSelected = activeRegion == reg
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(if (isSelected) TealAccentContainer else DarkSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) TealAccent else DarkSurfaceBorder,
                                        RoundedCornerShape(100.dp)
                                    )
                                    .clickable { onRegionChange(reg) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = reg,
                                    color = if (isSelected) TealAccent else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val filteredPlans = if (activeRegion == "All") esimPlans else esimPlans.filter { it.region == activeRegion }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredPlans) { plan ->
                            PopularEsimCard(
                                plan = plan,
                                onClick = { onSelectEsimPlan(plan) }
                            )
                        }
                    }
                }
            }

            ShopCategory.TOP_UP -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Mobile Recharge & Data Packs",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Instant airtime top-up across Jio, Airtel, Vodafone Idea & BSNL.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onOpenTopUpSheet,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("btn_start_topup"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Smartphone,
                                        contentDescription = null,
                                        tint = Color(0xFF00331E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Start Instant Recharge",
                                        color = Color(0xFF00331E),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Popular Recharge Plans",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(topUpPlans) { plan ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${plan.operator} · ₹${plan.amountInr}",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${plan.data} · ${plan.validity} · ${plan.talktime}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Button(
                                    onClick = onOpenTopUpSheet,
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccentContainer),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Select",
                                        color = TealAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
