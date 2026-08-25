package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MarketplaceTab
import com.example.ui.theme.*

@Composable
fun MarketplaceBottomBar(
    currentTab: MarketplaceTab,
    onTabSelected: (MarketplaceTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .background(DarkBg)
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = DarkBg,
        contentColor = TextPrimary,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(MarketplaceTab.HOME, Icons.Filled.Home, Icons.Outlined.Home),
            Triple(MarketplaceTab.SHOP, Icons.Filled.GridView, Icons.Outlined.GridView),
            Triple(MarketplaceTab.ORDERS, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
            Triple(MarketplaceTab.WALLET, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
            Triple(MarketplaceTab.PROFILE, Icons.Filled.Person, Icons.Outlined.Person)
        )

        items.forEach { (tab, selectedIcon, unselectedIcon) ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) selectedIcon else unselectedIcon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TealAccent,
                    selectedTextColor = TealAccent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
