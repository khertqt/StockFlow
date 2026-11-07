@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.vulcanizing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CheckoutScreen(store: Store, notify: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var detail by remember { mutableStateOf<Product?>(null) }
    var showCart by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<Sale?>(null) }
    var warning by remember { mutableStateOf("") }

    val shown = store.products.filter { p ->
        (category == "All" || p.category == category) &&
                (query.isBlank() || p.name.contains(query, true) ||
                        p.barcode.contains(query, true) || p.id.contains(query, true))
    }
    val cartCount = store.cart.values.sum()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Check Out", store.shopName)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search product, SKU or barcode") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        CategoryChips(category) { category = it }

        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState("📦", "No products found", "Add products in the Stocks tab or change your search.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(shown, key = { it.id }) { p ->
                    ProductCard(store, p, onClick = { detail = p }, onAdd = {
                        val e = store.addToCart(p)
                        if (e != null) notify(e) else notify("Added: ${p.name}")
                    })
                }
            }
        }

        if (cartCount > 0) {
            Button(
                onClick = { showCart = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("View Cart ($cartCount)  •  ${store.money(store.cartTotal())}") }
        }
    }

    // ----- product details -----
    detail?.let { p0 ->
        val p = store.products.firstOrNull { it.id == p0.id } ?: p0
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(p.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProductImage(
                        p.imagePath, p.category,
                        Modifier.fillMaxWidth().height(180.dp), 64.sp
                    )
                    Text(p.description)
                    Text("Category: ${p.category}")
                    Text("SKU / ID: ${p.id}")
                    Text("Barcode: ${p.barcode}")
                    val bmp = remember(p.barcode) { barcodeBitmap(p.barcode) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Barcode",
                            modifier = Modifier.fillMaxWidth().height(70.dp).background(Color.White)
                        )
                    }
                    Text(
                        store.money(p.price), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Available stock: ${p.stock}")
                    StatusChip(p.status())
                }
            },
            confirmButton = {
                Button(enabled = p.stock > 0, onClick = {
                    val e = store.addToCart(p)
                    if (e != null) notify(e) else {
                        notify("Added: ${p.name}")
                        detail = null
                    }
                }) { Text("Add to Cart") }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Close") } }
        )
    }

    // ----- cart -----
    if (showCart) {
        ModalBottomSheet(
            onDismissRequest = { showCart = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CartContent(store, notify) { sale, low ->
                showCart = false
                receipt = sale
                warning = if (low.isEmpty()) "" else "Low stock warning: " + low.joinToString(", ")
            }
        }
    }

    // ----- receipt -----
    receipt?.let { s ->
        ReceiptDialog(store, s, warning) {
            receipt = null
            warning = ""
        }
    }
}

@Composable
private fun ProductCard(store: Store, p: Product, onClick: () -> Unit, onAdd: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProductImage(
                p.imagePath, p.category,
                Modifier.fillMaxWidth().height(96.dp), 36.sp
            )
            Text(
                p.name, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, fontSize = 14.sp
            )
            Text(
                store.money(p.price), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Stock: ${p.stock}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusChip(p.status())
            Button(onClick = onAdd, enabled = p.stock > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (p.stock > 0) "Add to Cart" else "Unavailable", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CartContent(store: Store, notify: (String) -> Unit, onSold: (Sale, List<String>) -> Unit) {
    var paid by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var error by remember { mutableStateOf("") }
    val lines = store.cartLines()
    val total = store.cartTotal()
    val paidValue = paid.toDoubleOrNull()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Order Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (lines.isEmpty()) Text("Your cart is empty.")
        lines.forEach { (p, q) ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(p.imagePath, p.category, Modifier.size(44.dp), 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${store.money(p.price)} each", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(store.money(p.price * q), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { store.setCartQty(p, q - 1)?.let(notify) }) {
                            Text("−", fontSize = 22.sp)
                        }
                        Text("$q", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { store.setCartQty(p, q + 1)?.let(notify) }) {
                            Text("+", fontSize = 20.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { store.removeFromCart(p.id) }) {
                            Icon(Icons.Default.Delete, "Remove")
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TOTAL", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(store.money(total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Text("Payment method", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Cash", "GCash", "Card").forEach { m ->
                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
            }
        }

        if (method == "Cash") {
            OutlinedTextField(
                value = paid,
                onValueChange = { paid = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount received (${store.currency})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (paidValue != null && paidValue >= total && lines.isNotEmpty()) {
                Text(
                    "Change: ${store.money(paidValue - total)}", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

        Button(
            enabled = lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val result = store.completeSale(paidValue, method)
                val err = result.error
                if (err != null) {
                    error = err
                } else {
                    val s = result.sale
                    if (s != null) onSold(s, result.lowStock)
                }
            }
        ) { Text("Complete Sale") }
    }
}