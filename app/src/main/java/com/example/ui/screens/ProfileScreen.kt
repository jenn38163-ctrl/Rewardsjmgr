package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ResellerProfile
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    profile: ResellerProfile,
    onOpenApiKeys: () -> Unit,
    onOpenResellerModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        item {
            Text(
                text = "Profile",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // User Profile Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PurpleAccent, PurpleDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.initials,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profile.email,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0xFF2C1F4D))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = PurpleAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Reseller Account",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3 Stat Counters Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCounter(value = "${profile.totalOrders}", label = "Orders")
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(DarkSurfaceBorder))
                    StatCounter(value = "${profile.completedOrders}", label = "Completed")
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(DarkSurfaceBorder))
                    StatCounter(value = "₹${profile.totalSpentInr}", label = "Total Spent")
                }
            }
        }

        // Connected Providers Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Connected Providers",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ProviderStatusRow(
                        name = "Zendit",
                        category = "eSIM & Top-up",
                        isLive = profile.zenditLive
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProviderStatusRow(
                        name = "Giftbit",
                        category = "Gift Cards",
                        isLive = profile.giftbitLive
                    )
                }
            }
        }

        // Section: ACCOUNT
        item {
            SectionHeader(title = "ACCOUNT")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMenuTile(
                    icon = Icons.Outlined.Person,
                    title = "Personal Info",
                    subtitle = "Name, email, phone",
                    onClick = { Toast.makeText(context, "Account profile up to date", Toast.LENGTH_SHORT).show() }
                )
                ProfileMenuTile(
                    icon = Icons.Outlined.Storefront,
                    title = "Reseller Account",
                    subtitle = "Bulk pricing & API access",
                    badge = "PRO",
                    badgeColor = PurpleAccent,
                    badgeBg = Color(0xFF2C1F4D),
                    onClick = onOpenResellerModal
                )
                ProfileMenuTile(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Order alerts, promotions",
                    onClick = { Toast.makeText(context, "Push notifications enabled", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        // Section: API & INTEGRATION
        item {
            SectionHeader(title = "API & INTEGRATION")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMenuTile(
                    icon = Icons.Outlined.Key,
                    title = "API Keys",
                    subtitle = "Zendit & Giftbit credentials",
                    badge = "DEV",
                    badgeColor = TealAccent,
                    badgeBg = TealAccentContainer,
                    onClick = onOpenApiKeys
                )
                ProfileMenuTile(
                    icon = Icons.Outlined.Webhook,
                    title = "Webhooks",
                    subtitle = "Order status callbacks",
                    onClick = { Toast.makeText(context, "Webhooks active on NestJS Gateway", Toast.LENGTH_SHORT).show() }
                )
                ProfileMenuTile(
                    icon = Icons.Outlined.Receipt,
                    title = "Billing & Invoices",
                    subtitle = "GST invoices, payment history",
                    onClick = { Toast.makeText(context, "Invoices available for export", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        // Section: SUPPORT
        item {
            SectionHeader(title = "SUPPORT")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMenuTile(
                    icon = Icons.Outlined.SupportAgent,
                    title = "Customer Support",
                    subtitle = "24/7 ticket & live chat",
                    onClick = { Toast.makeText(context, "Support chat connected", Toast.LENGTH_SHORT).show() }
                )
                ProfileMenuTile(
                    icon = Icons.Outlined.HelpOutline,
                    title = "FAQs",
                    subtitle = "Common questions",
                    onClick = { Toast.makeText(context, "Opening Knowledge Base...", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@Composable
private fun StatCounter(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProviderStatusRow(name: String, category: String, isLive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = category, color = TextSecondary, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (isLive) GreenContainer else RedContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isLive) "LIVE" else "OFFLINE",
                color = if (isLive) GreenAccent else RedAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ProfileMenuTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = TealAccent,
    badgeBg: Color = TealAccentContainer,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkSurfaceBorder, DarkSurfaceHighlight)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF192238)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badge,
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
