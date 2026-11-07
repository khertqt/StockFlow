@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.vulcanizing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.print.PrintHelper

@Composable
fun ScanScreen(store: Store, notify: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Barcode Scan", "Scan or generate product barcodes")
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Scan") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Generate") })
        }
        if (tab == 0) ScanTab(store, notify) else GenerateTab(store, notify)
    }
}

@Composable
private fun ScanTab(store: Store, notify: (String) -> Unit) {
    val ctx = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) notify("Camera permission denied. You can type the barcode instead.")
    }
    var code by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }
    var registerCode by remember { mutableStateOf<String?>(null) }
    val scanned = code
    val product = if (scanned == null) null else store.products.firstOrNull { it.barcode == scanned }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasPermission) {
            if (scanned == null) {
                Box(
                    Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CameraScanner(Modifier.fillMaxSize()) { c -> if (code == null) code = c }
                    Box(
                        Modifier.size(width = 240.dp, height = 130.dp)
                            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    )
                }
                Text(
                    "Point the camera at a barcode inside the frame.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera access is needed to scan barcodes.")
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    Text(
                        "Or type the barcode below.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // manual entry
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manual, onValueChange = { manual = it.trim() },
                label = { Text("Enter barcode manually") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (manual.isBlank()) notify("Type a barcode first.") else {
                    code = manual
                    manual = ""
                }
            }) { Text("Find") }
        }

        // result
        if (scanned != null) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (product != null) {
                        Text("Product found", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(product.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Barcode: ${product.barcode}")
                        Text("Price: ${store.money(product.price)}")
                        Text("Available stock: ${product.stock}")
                        StatusChip(product.status())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = product.stock > 0, onClick = {
                                val e = store.addToCart(product)
                                if (e != null) notify(e) else notify("Added to cart")
                            }) { Text("Add to cart") }
                            OutlinedButton(onClick = { code = null }) { Text("Scan again") }
                        }
                    } else {
                        Text("Not registered", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        Text("The barcode “$scanned” is not linked to any product.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { registerCode = scanned }) { Text("Register product") }
                            OutlinedButton(onClick = { code = null }) { Text("Scan again") }
                        }
                    }
                }
            }
        }
    }

    registerCode?.let { c ->
        ProductEditorDialog(store, null, prefillBarcode = c, onDismiss = { registerCode = null }) {
            registerCode = null
            notify(it)
        }
    }
}

@Composable
private fun GenerateTab(store: Store, notify: (String) -> Unit) {
    val ctx = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    val product = store.products.firstOrNull { it.id == selectedId }

    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bmp = pending
        if (uri != null && bmp != null) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            } catch (e: Exception) {
                false
            }
            notify(if (ok) "Barcode label saved." else "Could not save the label.")
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Every product gets a unique barcode automatically when it is created. Pick a product to view, save or print its label.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(product?.let { "${it.name} (${it.id})" } ?: "Select a product")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                store.products.forEach { p ->
                    DropdownMenuItem(text = { Text("${p.name} (${p.id})") }, onClick = {
                        selectedId = p.id
                        menu = false
                    })
                }
            }
        }
        if (store.products.isEmpty()) {
            EmptyState("📦", "No products", "Add a product in the Stocks tab first.")
        }
        if (product != null) {
            val label = remember(product.barcode, product.name) { labelBitmap(product) }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (label != null) {
                        Image(
                            bitmap = label.asImageBitmap(),
                            contentDescription = "Barcode label",
                            modifier = Modifier.fillMaxWidth().background(Color.White)
                        )
                    } else {
                        Text("This barcode cannot be drawn. Generate a new one.", color = MaterialTheme.colorScheme.error)
                    }
                    Text("Product: ${product.name}")
                    Text("SKU: ${product.id}")
                    Text("Barcode: ${product.barcode}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = label != null, onClick = {
                            pending = label
                            savePng.launch("barcode_${product.id}.png")
                        }) { Text("Save PNG") }
                        OutlinedButton(enabled = label != null, onClick = {
                            if (label != null) PrintHelper(ctx).printBitmap("Barcode ${product.id}", label)
                        }) { Text("Print") }
                    }
                    TextButton(onClick = { confirmNew = true }) { Text("Generate a new barcode") }
                }
            }
        }
    }

    if (confirmNew && product != null) {
        ConfirmDialog(
            title = "Generate a new barcode?",
            message = "The old barcode will stop working for “${product.name}”. Labels already printed will no longer match.",
            confirmText = "Generate",
            onConfirm = {
                store.regenerateBarcode(product.id)
                confirmNew = false
                notify("New barcode generated")
            },
            onDismiss = { confirmNew = false }
        )
    }
}