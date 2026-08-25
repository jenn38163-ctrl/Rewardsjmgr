package com.example.ui.screens

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
fun HomeScreen(
    giftCards: List<GiftCardProduct>,
    esimPlans: List<EsimPlan>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectGiftCard: (GiftCardProduct) -> Unit,
    onSelectEsimPlan: (EsimPlan) -> Unit,
    onNavigateToCategory: (ShopCategory) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToShop: () -> Unit,
    onOpenResellerModal: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Header Greeting & Notification
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Good Morning",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "👋", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "What are you looking for today?",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier
                        .testTag("btn_notification")
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkSurfaceBorder, CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(TealAccent)
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search gift cards, eSIM, top-up...",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_bar"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = TealAccent,
                    unfocusedBorderColor = DarkSurfaceBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }

        // Hero Banner
        item {
            HeroMarketplaceBanner()
        }

        // 4 Category Quick Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CategoryQuickActionButton(
                    icon = Icons.Outlined.CardGiftcard,
                    label = "Gift Cards",
                    containerColor = Color(0xFF33230A),
                    iconTint = AmberAccent,
                    onClick = { onNavigateToCategory(ShopCategory.GIFT_CARDS) }
                )
                CategoryQuickActionButton(
                    icon = Icons.Outlined.SimCard,
                    label = "eSIM",
                    containerColor = Color(0xFF162038),
                    iconTint = BlueAccent,
                    onClick = { onNavigateToCategory(ShopCategory.ESIM) }
                )
                CategoryQuickActionButton(
                    icon = Icons.Outlined.Smartphone,
                    label = "Top-up",
                    containerColor = Color(0xFF103328),
                    iconTint = GreenAccent,
                    onClick = { onNavigateToCategory(ShopCategory.TOP_UP) }
                )
                CategoryQuickActionButton(
                    icon = Icons.Outlined.ReceiptLong,
                    label = "My Orders",
                    containerColor = Color(0xFF33230A),
                    iconTint = AmberAccent,
                    onClick = onNavigateToOrders
                )
            }
        }

        // Section: Popular Gift Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Popular Gift Cards",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See all",
                    color = TealAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onNavigateToShop)
                )
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(giftCards.filter { it.isPopular }.take(3)) { card ->
                    GiftCardGridCard(
                        giftCard = card,
                        onClick = { onSelectGiftCard(card) },
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }

        // Section: Popular eSIM Plans
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Popular eSIM Plans",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See all",
                    color = TealAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToCategory(ShopCategory.ESIM) }
                )
            }
        }

        items(esimPlans.filter { it.isPopular }.take(2)) { plan ->
            PopularEsimCard(
                plan = plan,
                onClick = { onSelectEsimPlan(plan) }
            )
        }

        // Become a Reseller Banner
        item {
            ResellerPromoCard(onClick = onOpenResellerModal)
        }
    }
}
