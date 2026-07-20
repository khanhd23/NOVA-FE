package com.nova.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.PurpleMain

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var screenState by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.Main) }
    var currentLanguage by remember { mutableStateOf("English") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (screenState) {
            SettingsSubScreen.Main -> MainSettings(
                isDarkMode = isDarkMode,
                currentLanguage = currentLanguage,
                onBack = onBack,
                onNavigate = { screenState = it },
                onDarkModeToggle = onDarkModeToggle,
                onLogout = onLogout,
            )
            SettingsSubScreen.Privacy -> PrivacySettings(onBack = { screenState = SettingsSubScreen.Main })
            SettingsSubScreen.Language -> LanguageSettings(
                currentLanguage = currentLanguage,
                onBack = { screenState = SettingsSubScreen.Main },
                onSelect = { 
                    currentLanguage = it
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
        NovaTopBar(title = "Settings", onBack = onBack)
        
        LazyColumn(modifier = Modifier.padding(horizontal = 24.dp)) {
            item {
                SettingsItem(title = "Privacy", icon = Icons.Default.Lock, onClick = { onNavigate(SettingsSubScreen.Privacy) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = "Account Management", icon = Icons.Default.AccountBox, onClick = { onNavigate(SettingsSubScreen.AccountManagement) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = "Payments & VIP", icon = Icons.Default.Payment, onClick = { onNavigate(SettingsSubScreen.Payments) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Dark Mode", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeToggle)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = "Language", icon = Icons.Default.Language, detail = currentLanguage, onClick = { onNavigate(SettingsSubScreen.Language) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = "Help & Feedback", icon = Icons.Default.HelpCenter, onClick = {})
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                
                SettingsItem(title = "Logout", icon = Icons.AutoMirrored.Filled.Logout, color = Color.Red, onClick = onLogout)
            }
        }
    }
}

@Composable
fun PrivacySettings(onBack: () -> Unit) {
    val privacyOptions = listOf(
        "Incognito Mode" to "Hide your profile from discovery",
        "Read Receipts" to "Let others know when you've seen their messages",
        "Online Status" to "Show when you're active",
        "Distance Visible" to "Show how far away you are"
    )
    
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = "Privacy", onBack = onBack)
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
        NovaTopBar(title = "Account", onBack = onBack)
        Column(modifier = Modifier.padding(24.dp)) {
            SettingsItem(title = "Download My Data", icon = Icons.Default.Download, onClick = {})
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingsItem(title = "Lock Account", icon = Icons.Default.LockPerson, onClick = {})
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingsItem(title = "Delete Account", icon = Icons.Default.DeleteForever, color = Color.Red, onClick = {})
        }
    }
}

@Composable
fun PaymentSettings(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = "Payments & VIP", onBack = onBack)
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Active Subscription: None", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 16.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleMain),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Upgrade to VIP", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            SettingsItem(title = "Payment Methods", icon = Icons.Default.CreditCard, onClick = {})
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingsItem(title = "Transaction History", icon = Icons.Default.History, onClick = {})
        }
    }
}

@Composable
fun LanguageSettings(currentLanguage: String, onBack: () -> Unit, onSelect: (String) -> Unit) {
    val languages = listOf("English", "Tiếng Việt", "日本語", "한국어", "Français")
    
    Column(modifier = Modifier.fillMaxSize()) {
        NovaTopBar(title = "Language", onBack = onBack)
        Column(modifier = Modifier.padding(24.dp)) {
            languages.forEach { lang ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(lang) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = lang == currentLanguage, onClick = { onSelect(lang) })
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(lang, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
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
