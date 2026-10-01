package com.nova.app.feature.settings

import com.nova.app.core.designsystem.NovaColors

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import android.content.res.Resources
import androidx.compose.ui.platform.LocalResources
import com.nova.app.core.i18n.AppLanguage
import com.nova.app.core.i18n.LocalAppLanguage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.backend.BackendCommerceCatalog
import com.nova.app.core.backend.BackendCommerceMe
import com.nova.app.core.backend.BackendCommerceOrder
import com.nova.app.core.backend.BackendDiamondPackage
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.BackendVipTier
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var screenState by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.Main) }
    val languageController = LocalAppLanguage.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (screenState) {
            SettingsSubScreen.Main -> MainSettings(
                isDarkMode = isDarkMode,
                currentLanguage = languageController.current.nativeName,
                onBack = onBack,
                onNavigate = { screenState = it },
                onDarkModeToggle = onDarkModeToggle,
                onLogout = onLogout,
            )
            SettingsSubScreen.Privacy -> PrivacySettings(onBack = { screenState = SettingsSubScreen.Main })
            SettingsSubScreen.Language -> LanguageSettings(
                currentLanguage = languageController.current,
                onBack = { screenState = SettingsSubScreen.Main },
                onSelect = {
                    languageController.select(it)
                    screenState = SettingsSubScreen.Main
                }
            )
            SettingsSubScreen.AccountManagement -> AccountManagementSettings(onBack = { screenState = SettingsSubScreen.Main })
            SettingsSubScreen.Payments -> PaymentSettings(onBack = { screenState = SettingsSubScreen.Main })
        }
    }
}

@Composable
fun MainSettings(
    isDarkMode: Boolean,
    currentLanguage: String,
    onBack: () -> Unit,
    onNavigate: (SettingsSubScreen) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        
        LazyColumn(modifier = Modifier.padding(horizontal = 24.dp)) {
            item {
                SettingsItem(title = stringResource(R.string.settings_privacy), icon = Icons.Default.Lock, onClick = { onNavigate(SettingsSubScreen.Privacy) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = stringResource(R.string.settings_account_management), icon = Icons.Default.AccountBox, onClick = { onNavigate(SettingsSubScreen.AccountManagement) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = stringResource(R.string.settings_payments_vip), icon = Icons.Default.Payment, onClick = { onNavigate(SettingsSubScreen.Payments) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_dark_mode), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeToggle)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = stringResource(R.string.settings_language), icon = Icons.Default.Language, detail = currentLanguage, onClick = { onNavigate(SettingsSubScreen.Language) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = stringResource(R.string.settings_help_feedback), icon = Icons.Default.HelpCenter, onClick = {})
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = stringResource(R.string.settings_logout), icon = Icons.AutoMirrored.Filled.Logout, color = NovaColors.current.danger, onClick = onLogout)
            }
        }
    }
}

@Composable
fun PrivacySettings(onBack: () -> Unit) {
    val privacyOptions = listOf(
        stringResource(R.string.privacy_incognito) to stringResource(R.string.privacy_incognito_desc),
        stringResource(R.string.privacy_read_receipts) to stringResource(R.string.privacy_read_receipts_desc),
        stringResource(R.string.privacy_online_status) to stringResource(R.string.privacy_online_status_desc),
        stringResource(R.string.privacy_distance) to stringResource(R.string.privacy_distance_desc)
    )
    
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = stringResource(R.string.settings_privacy), onBack = onBack)
        Column(modifier = Modifier.padding(24.dp)) {
            privacyOptions.forEach { (title, desc) ->
                var checked by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                        Text(desc, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Switch(checked = checked, onCheckedChange = { checked = it })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun AccountManagementSettings(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = stringResource(R.string.account_title), onBack = onBack)
        Column(modifier = Modifier.padding(24.dp)) {
            SettingsItem(title = stringResource(R.string.account_download_data), icon = Icons.Default.Download, onClick = {})
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingsItem(title = stringResource(R.string.account_lock), icon = Icons.Default.LockPerson, onClick = {})
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingsItem(title = stringResource(R.string.account_delete), icon = Icons.Default.DeleteForever, color = NovaColors.current.danger, onClick = {})
        }
    }
}

@Composable
fun PaymentSettings(onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<BackendCommerceCatalog?>(null) }
    var commerceMe by remember { mutableStateOf<BackendCommerceMe?>(null) }
    var loading by remember { mutableStateOf(true) }
    var buyingProductId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val runtime = BackendRuntimeRegistry.runtime
    val res = LocalResources.current

    fun loadCommerce() {
        scope.launch {
            loading = true
            message = null
            catalog = runtime?.fetchCommerceCatalog()
            commerceMe = runtime?.fetchCommerceMe()
            loading = false
            if (catalog == null) {
                message = res.getString(R.string.pay_load_failed)
            }
        }
    }

    fun buy(productId: String, purchaseType: String) {
        scope.launch {
            val activeRuntime = BackendRuntimeRegistry.runtime
            if (activeRuntime == null || activeRuntime.currentSession() == null) {
                message = res.getString(R.string.pay_sign_in_first)
                return@launch
            }
            buyingProductId = productId
            message = null
            val order = activeRuntime.createCommerceOrder(productId, purchaseType, provider = "DEMO")
            val confirmed = order?.let {
                activeRuntime.confirmCommerceOrder(
                    orderId = it.orderId,
                    success = true,
                    transactionId = "demo-${System.currentTimeMillis()}",
                    message = "Demo payment approved",
                )
            }
            if (confirmed?.status == "SUCCESS") {
                commerceMe = activeRuntime.fetchCommerceMe()
                activeRuntime.fetchMe()
                message = purchaseSuccessMessage(res, confirmed)
            } else {
                message = confirmed?.failureReason ?: res.getString(R.string.pay_failed)
            }
            buyingProductId = null
        }
    }

    LaunchedEffect(Unit) {
        loadCommerce()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = stringResource(R.string.settings_payments_vip), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CommerceHero(
                    commerceMe = commerceMe,
                    loading = loading,
                    onRefresh = ::loadCommerce,
                )
            }

            if (message != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = message.orEmpty(),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            item {
                CommerceSectionHeader(
                    title = stringResource(R.string.pay_vip_packages),
                    subtitle = stringResource(R.string.pay_vip_packages_desc),
                )
            }

            item {
                val tiers = catalog?.vipTiers.orEmpty()
                if (loading && tiers.isEmpty()) {
                    CommerceLoadingCard()
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(tiers, key = { it.id }) { tier ->
                            VipTierCard(
                                tier = tier,
                                active = commerceMe?.vipTierId == tier.id && commerceMe?.vipActive == true,
                                buying = buyingProductId == tier.id,
                                onBuy = { buy(tier.id, "VIP") },
                            )
                        }
                    }
                }
            }

            item {
                CommerceSectionHeader(
                    title = stringResource(R.string.pay_diamond_packs),
                    subtitle = stringResource(R.string.pay_diamond_packs_desc),
                )
            }

            items(catalog?.diamondPackages.orEmpty(), key = { it.id }) { pack ->
                DiamondPackageCard(
                    pack = pack,
                    buying = buyingProductId == pack.id,
                    onBuy = { buy(pack.id, "DIAMOND") },
                )
            }

            item {
                CommerceSectionHeader(
                    title = stringResource(R.string.pay_method),
                    subtitle = stringResource(R.string.pay_method_desc),
                )
                PaymentProvidersRow(catalog = catalog)
            }

            item {
                RecentOrdersCard(orders = commerceMe?.recentOrders.orEmpty())
            }
        }
    }
}

@Composable
private fun CommerceHero(
    commerceMe: BackendCommerceMe?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val vipLabel = when {
        commerceMe?.vipActive == true -> commerceMe.vipTierName ?: stringResource(R.string.pay_vip_active)
        else -> stringResource(R.string.pay_free_account)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF101828), Color(0xFF233876), PurpleMain)))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD166))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pay_store_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(vipLabel, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                }
                TextButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) stringResource(R.string.common_loading) else stringResource(R.string.common_refresh), color = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                WalletMetric(
                    title = stringResource(R.string.pay_diamonds),
                    value = "${commerceMe?.diamondBalance ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                WalletMetric(
                    title = stringResource(R.string.pay_vip_expires),
                    value = commerceMe?.vipExpiresAt ?: "-",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WalletMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun CommerceSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 12.sp)
    }
}

@Composable
private fun VipTierCard(
    tier: BackendVipTier,
    active: Boolean,
    buying: Boolean,
    onBuy: () -> Unit,
) {
    val accent = parseAccent(tier.accentColor, PurpleMain)
    Box(
        modifier = Modifier
            .width(286.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.32f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                )
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CommerceBadge(text = if (active) stringResource(R.string.pay_active) else tier.badgeLabel, color = accent)
                Spacer(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.pay_vip_level, tier.level.toString()), color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tier.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(tier.subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f), fontSize = 12.sp)
            }
            Text(
                text = "${tier.price}${tier.cycle}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tier.features.take(4).forEach { feature ->
                    CommerceFeature(text = feature, color = accent)
                }
            }
            Button(
                onClick = onBuy,
                enabled = !active && !buying,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Text(
                    text = when {
                        active -> stringResource(R.string.pay_current_plan)
                        buying -> stringResource(R.string.common_processing)
                        else -> stringResource(R.string.pay_buy_vip)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DiamondPackageCard(
    pack: BackendDiamondPackage,
    buying: Boolean,
    onBuy: () -> Unit,
) {
    val accent = parseAccent(pack.accentColor, PurplePink)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Diamond, contentDescription = null, tint = accent)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pack.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold)
                    if (pack.bestValue) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CommerceBadge(text = stringResource(R.string.pay_best_value), color = accent)
                    }
                }
                Text(stringResource(R.string.pay_diamond_count, pack.diamonds.toString()), color = accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(pack.subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pack.price, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black)
                OutlinedButton(
                    onClick = onBuy,
                    enabled = !buying,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (buying) "..." else stringResource(R.string.pay_buy))
                }
            }
        }
    }
}

@Composable
private fun CommerceFeature(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f), fontSize = 12.sp)
    }
}

@Composable
private fun CommerceBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun PaymentProvidersRow(catalog: BackendCommerceCatalog?) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
        items(catalog?.paymentProviders.orEmpty(), key = { it.id }) { provider ->
            Surface(
                color = if (provider.available) PurpleMain.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.width(160.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(provider.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (provider.available) stringResource(R.string.pay_available_now) else stringResource(R.string.pay_coming_soon),
                        color = if (provider.available) PurpleMain else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.54f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentOrdersCard(orders: List<BackendCommerceOrder>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pay_recent_transactions), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold)
            if (orders.isEmpty()) {
                Text(stringResource(R.string.pay_no_transactions), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 12.sp)
            } else {
                orders.take(4).forEach { order ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(order.productName, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                            Text(order.status, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 11.sp)
                        }
                        Text("${order.amount} ${order.currency}", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommerceLoadingCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.height(150.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

private fun purchaseSuccessMessage(res: Resources, order: BackendCommerceOrder): String {
    val grant = order.grant
    return when (order.purchaseType) {
        "VIP" -> res.getString(R.string.pay_vip_activated, grant?.vipTierName ?: order.productName)
        "DIAMOND" -> res.getString(
            R.string.pay_diamonds_added,
            (grant?.diamondsAdded ?: 0).toString(),
            grant?.diamondBalanceAfter?.toString() ?: "-",
        )
        else -> res.getString(R.string.pay_purchased, order.productName)
    }
}

private fun parseAccent(value: String, fallback: Color): Color {
    if (value.isBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)
}

@Composable
fun LanguageSettings(currentLanguage: AppLanguage, onBack: () -> Unit, onSelect: (AppLanguage) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = stringResource(R.string.settings_language), subtitle = stringResource(R.string.settings_language_hint), onBack = onBack)
        Column(modifier = Modifier.padding(24.dp)) {
            AppLanguage.entries.forEach { lang ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(lang) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = lang == currentLanguage, onClick = { onSelect(lang) })
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(lang.nativeName, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, detail: String? = null, color: Color = MaterialTheme.colorScheme.onBackground, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = color, fontSize = 16.sp)
            if (detail != null) {
                Text(detail, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

sealed class SettingsSubScreen {
    object Main : SettingsSubScreen()
    object Privacy : SettingsSubScreen()
    object Language : SettingsSubScreen()
    object AccountManagement : SettingsSubScreen()
    object Payments : SettingsSubScreen()
}
