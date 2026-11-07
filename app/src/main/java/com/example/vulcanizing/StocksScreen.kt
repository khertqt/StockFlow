@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.vulcanizing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

@Composable
fun StocksScreen(store: Store, notify: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var adjusting by remember { mutableStateOf<Product?>(null) }
    var deleting by remember { mutableStateOf<Product?>(null) }

    val filtered = store.products.filter { p ->
        (category == "All" || p.category == category) &&
                (query.isBlank() || p.name.contains(query, true) ||
                        p.barcode.contains(query, true) || p.id.contains(query, true))
    }
    val totalUnits = store.products.sumOf { it.stock }
    val lowCount = store.products.count { it.status() == StockStatus.LOW }
    val outCount = store.products.count { it.status() == StockStatus.OUT }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Stocks", "Inventory overview")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (store.products.any { it.demo }) {
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            Text(
                                "Demo data is loaded so you can try the app. Remove it in Settings when you start using real records.",
                                modifier = Modifier.padding(12.dp), fontSize = 13.sp
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Total products", "${store.products.size}", Modifier.weight(1f))
                        StatCard("Total units", "$totalUnits", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Low stock", "$lowCount", Modifier.weight(1f))
                        StatCard("Out of stock", "$outCount", Modifier.weight(1f))
                    }
                }
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search product, SKU or barcode") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                item { CategoryChips(category, 0.dp) { category = it } }
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Stock by product", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            if (filtered.isEmpty()) {
                                Text("No data to chart yet.")
                            } else {
                                BarChart(filtered.map { shortLabel(it.name) }, filtered.map { it.stock })
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState("📦", "No products yet", "Tap “Add Product” to create your first item.")
                    }
                }
                items(filtered, key = { it.id }) { p ->
                    ProductRow(
                        store, p,
                        onEdit = { editing = p },
                        onAdjust = { adjusting = p },
                        onDelete = { deleting = p }
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add Product") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (adding) {
        ProductEditorDialog(store, null, onDismiss = { adding = false }) {
            adding = false
            notify(it)
        }
    }
    editing?.let { p ->
        ProductEditorDialog(store, p, onDismiss = { editing = null }) {
            editing = null
            notify(it)
        }
    }
    adjusting?.let { p ->
        AdjustStockDialog(store, p, onDismiss = { adjusting = null }) {
            adjusting = null
            notify(it)
        }
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete product?",
            message = "“${p.name}” and its photo will be removed from inventory. Past sales records are kept.",
            confirmText = "Delete",
            onConfirm = {
                store.deleteProduct(p.id)
                deleting = null
                notify("Product deleted")
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun ProductRow(
    store: Store, p: Product,
    onEdit: () -> Unit, onAdjust: () -> Unit, onDelete: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductImage(p.imagePath, p.category, Modifier.size(56.dp), 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                        if (p.demo) {
                            Spacer(Modifier.width(6.dp))
                            DemoBadge()
                        }
                    }
                    Text(p.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(p.status())
            }
            Text(
                "SKU: ${p.id}   •   Barcode: ${p.barcode}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${store.money(p.price)}   •   Stock: ${p.stock}   •   Min: ${p.minStock}",
                fontWeight = FontWeight.Medium
            )
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onAdjust) { Text("Adjust stock") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun ProductEditorDialog(
    store: Store,
    initial: Product?,
    prefillBarcode: String = "",
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val ctx = LocalContext.current

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: CATEGORIES.first()) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var price by remember { mutableStateOf(initial?.let { String.format(Locale.US, "%.2f", it.price) } ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "0") }
    var minStock by remember { mutableStateOf(initial?.minStock?.toString() ?: store.lowStockDefault.toString()) }
    var barcode by remember {
        mutableStateOf(initial?.barcode ?: prefillBarcode.ifBlank { store.newBarcode() })
    }
    var error by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    // ----- photo state -----
    val existingPath = initial?.imagePath ?: ""
    var newPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf("") }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = cameraUri
            val bmp = if (uri != null) loadScaledBitmap(ctx, uri) else null
            if (bmp != null) {
                newPhoto = bmp
                removePhoto = false
                photoMessage = ""
            } else {
                photoMessage = "Could not read the photo. Please try again."
            }
        }
        try {
            cameraFile?.delete()
        } catch (e: Exception) {
        }
    }

    fun launchCamera() {
        try {
            val dir = File(ctx.cacheDir, "camera")
            dir.mkdirs()
            val file = File(dir, "capture_" + System.currentTimeMillis() + ".jpg")
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            cameraFile = file
            cameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            photoMessage = "Could not open the camera. You can choose a photo from the gallery instead."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            photoMessage = "Camera permission was denied. You can choose a photo from the gallery instead."
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = loadScaledBitmap(ctx, uri)
            if (bmp != null) {
                newPhoto = bmp
                removePhoto = false
                photoMessage = ""
            } else {
                photoMessage = "Could not open that image. Please choose a different one."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "Add Product" else "Edit Product") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ----- optional photo -----
                Text("Product photo (optional)", fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val np = newPhoto
                    if (np != null) {
                        Image(
                            bitmap = np.asImageBitmap(),
                            contentDescription = "Photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ProductImage(
                            if (removePhoto) "" else existingPath,
                            category, Modifier.fillMaxSize(), 56.sp
                        )
                    }
                }
                val hasPhoto = newPhoto != null || (existingPath.isNotBlank() && !removePhoto)
                OutlinedButton(
                    onClick = {
                        photoMessage = ""
                        val granted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Take Photo") }
                OutlinedButton(
                    onClick = {
                        photoMessage = ""
                        try {
                            pickImage.launch("image/*")
                        } catch (e: Exception) {
                            photoMessage = "Could not open the gallery on this device."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose from Gallery") }
                if (hasPhoto) {
                    TextButton(
                        onClick = {
                            newPhoto = null
                            removePhoto = true
                            photoMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remove Photo", color = MaterialTheme.colorScheme.error) }
                } else {
                    Text(
                        "No photo selected. A photo is optional, so you can skip it and save without one.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (photoMessage.isNotEmpty()) {
                    Text(photoMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                // ----- product details -----
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Product name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Category: $category")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        CATEGORIES.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = {
                                category = c
                                menuOpen = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Selling price (${store.currency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter { c -> c.isDigit() } },
                    label = { Text("Stock quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minStock,
                    onValueChange = { minStock = it.filter { c -> c.isDigit() } },
                    label = { Text("Minimum stock level") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode, onValueChange = { barcode = it.trim() },
                        label = { Text("Barcode") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { barcode = store.newBarcode() }) { Text("Generate") }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = Product(
                    id = initial?.id ?: "",
                    name = name.trim(),
                    category = category,
                    description = description.trim(),
                    barcode = barcode.trim(),
                    price = price.toDoubleOrNull() ?: 0.0,
                    stock = stock.toIntOrNull() ?: -1,
                    minStock = minStock.toIntOrNull() ?: -1,
                    demo = initial?.demo ?: false,
                    imagePath = existingPath
                )
                val err = if (initial == null) {
                    store.addProduct(p, newPhoto)
                } else {
                    store.updateProduct(p, newPhoto, removePhoto)
                }
                if (err != null) error = err
                else onSaved(if (initial == null) "Product added" else "Product updated")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AdjustStockDialog(store: Store, p: Product, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Adjust stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${p.name}\nCurrent stock: ${p.stock}")
                ChipRow(listOf("Add delivery", "Set exact count"), mode, 0.dp) { mode = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() } },
                    label = { Text(if (mode == 0) "Units received" else "Correct count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = amount.toIntOrNull()
                if (n == null || n < 0) {
                    error = "Enter a whole number (0 or more)."
                } else {
                    store.adjustStock(p.id, if (mode == 0) p.stock + n else n)
                    onDone("Stock updated: ${p.name}")
                }
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}