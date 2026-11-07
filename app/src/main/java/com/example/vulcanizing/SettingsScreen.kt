@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.vulcanizing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(store: Store, notify: (String) -> Unit) {
    var threshold by remember { mutableStateOf(store.lowStockDefault.toString()) }
    var confirmDemo by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", "Preferences & appearance")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Appearance") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark mode", fontWeight = FontWeight.Medium)
                        Text(
                            if (store.darkMode) "Dark theme is on" else "Light theme is on",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = store.darkMode, onCheckedChange = {
                        store.darkMode = it
                        store.saveSettings()
                    })
                }
            }

            Section("Shop information") {
                OutlinedTextField(
                    value = store.shopName,
                    onValueChange = { store.shopName = it; store.saveSettings() },
                    label = { Text("Shop name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = store.shopInfo,
                    onValueChange = { store.shopInfo = it; store.saveSettings() },
                    label = { Text("Address / contact (shown on receipts)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = store.currency,
                    onValueChange = { store.currency = it.take(3); store.saveSettings() },
                    label = { Text("Currency symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }

            Section("Inventory alerts") {
                OutlinedTextField(
                    value = threshold,
                    onValueChange = {
                        threshold = it.filter { c -> c.isDigit() }
                        val n = threshold.toIntOrNull()
                        if (n != null) {
                            store.lowStockDefault = n
                            store.saveSettings()
                        }
                    },
                    label = { Text("Low-stock level for new products") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = {
                    store.applyThresholdToAll()
                    notify("Applied to all products")
                }) { Text("Apply to all existing products") }
            }

            Section("Receipt") {
                OutlinedTextField(
                    value = store.receiptFooter,
                    onValueChange = { store.receiptFooter = it; store.saveSettings() },
                    label = { Text("Receipt footer message") }, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show address / contact on receipt", Modifier.weight(1f))
                    Switch(checked = store.receiptShowInfo, onCheckedChange = {
                        store.receiptShowInfo = it
                        store.saveSettings()
                    })
                }
            }

            Section("Data export") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Include demo transactions in exports", Modifier.weight(1f))
                    Switch(checked = store.exportDemo, onCheckedChange = {
                        store.exportDemo = it
                        store.saveSettings()
                    })
                }
            }

            Section("Demo data") {
                Text(
                    "Demo data is a set of sample products and transactions, marked DEMO, for trying the app. Your own records are never touched.",
                    fontSize = 13.sp
                )
                Text(
                    if (store.hasDemo()) "Status: demo data is loaded." else "Status: no demo data.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !store.hasDemo(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (store.addDemoData()) notify("Demo data added") else notify("Demo data is already loaded.")
                        }
                    ) { Text("Add demo data") }
                    OutlinedButton(
                        enabled = store.hasDemo(),
                        modifier = Modifier.weight(1f),
                        onClick = { confirmDemo = true }
                    ) { Text("Remove demo data") }
                }
            }

            Section("About") {
                Text("StockFlow — Inventory & Sales for a vulcanizing shop and auto parts store.")
                Text(
                    "Version 1.0. Data is stored on this device only and is not synced to other devices.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmDemo) {
        ConfirmDialog(
            title = "Remove demo data?",
            message = "All sample products and sample transactions will be deleted. Your own records stay.",
            confirmText = "Remove",
            onConfirm = {
                store.clearDemo()
                confirmDemo = false
                notify("Demo data removed")
            },
            onDismiss = { confirmDemo = false }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}