package com.example.vulcanizing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val CATEGORIES = listOf(
    "Tires & Tubes", "Engine Oils", "Rims & Mags", "Oil Filters",
    "Vulcanizing Supplies", "Valves & Accessories", "Tire Sealants",
    "Automotive Tools", "Other Parts"
)

fun categoryEmoji(category: String): String = when (category) {
    "Tires & Tubes" -> "⭕"
    "Engine Oils" -> "🛢️"
    "Rims & Mags" -> "⚙️"
    "Oil Filters" -> "🔧"
    "Vulcanizing Supplies" -> "🧰"
    "Valves & Accessories" -> "🔩"
    "Tire Sealants" -> "💧"
    "Automotive Tools" -> "🛠️"
    else -> "📦"
}

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val barcode: String,
    val price: Double,
    val stock: Int,
    val minStock: Int,
    val demo: Boolean = false,
    val imagePath: String = ""
)

data class SaleLine(val productId: String, val name: String, val qty: Int, val price: Double)

data class Sale(
    val id: String,
    val time: Long,
    val lines: List<SaleLine>,
    val total: Double,
    val paid: Double,
    val method: String,
    val demo: Boolean = false
)

enum class StockStatus { IN, LOW, OUT }

fun Product.status(): StockStatus = when {
    stock <= 0 -> StockStatus.OUT
    stock <= minStock -> StockStatus.LOW
    else -> StockStatus.IN
}

class SaleResult(val sale: Sale?, val error: String?, val lowStock: List<String> = emptyList())

// ---------- Date helpers ----------
fun startOfDay(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun endOfDay(ts: Long): Long = startOfDay(ts) + 24L * 60 * 60 * 1000 - 1

fun startOfWeek(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
    return startOfDay(c.timeInMillis)
}

fun startOfMonth(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.DAY_OF_MONTH, 1)
    return startOfDay(c.timeInMillis)
}

// The date picker returns UTC midnight; convert it to local midnight of the same date
fun utcMillisToLocalStart(ms: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = ms
    val local = Calendar.getInstance()
    local.clear()
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    return local.timeInMillis
}

fun formatDateTime(ts: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(ts))

fun formatDate(ts: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))

fun formatShortDate(ts: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))

fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean = try {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
} catch (e: Exception) {
    false
}

// ---------- Demo data ----------
private class DemoDef(
    val name: String, val category: String, val description: String,
    val price: Double, val stock: Int, val min: Int
)

private val DEMO_ITEMS = listOf(
    DemoDef("Motorcycle Tire 80/90-17", "Tires & Tubes", "Tubeless tire for small motorcycles.", 1650.0, 12, 4),
    DemoDef("Inner Tube 17 (Motorcycle)", "Tires & Tubes", "Standard motorcycle inner tube.", 350.0, 20, 6),
    DemoDef("Engine Oil 10W-40 (1L)", "Engine Oils", "Semi-synthetic motorcycle engine oil.", 420.0, 30, 8),
    DemoDef("Gear Oil 80W-90 (120ml)", "Engine Oils", "Gear oil for scooters.", 85.0, 3, 5),
    DemoDef("Mags Rim 17 (Pair)", "Rims & Mags", "Alloy mags, sold per pair.", 4200.0, 2, 2),
    DemoDef("Oil Filter (Universal)", "Oil Filters", "Fits most small-displacement motorcycles.", 180.0, 15, 5),
    DemoDef("Tire Patch Kit", "Vulcanizing Supplies", "Patches and glue for flat repair.", 120.0, 25, 8),
    DemoDef("Rubber Cement (Small)", "Vulcanizing Supplies", "Vulcanizing cement.", 65.0, 0, 5),
    DemoDef("Tire Valve Stem (Tubeless)", "Valves & Accessories", "Rubber snap-in valve.", 45.0, 50, 15),
    DemoDef("Valve Cap Set", "Valves & Accessories", "Set of 4 valve caps.", 25.0, 40, 10),
    DemoDef("Tire Sealant (250ml)", "Tire Sealants", "Puncture sealant for tubeless tires.", 220.0, 9, 4),
    DemoDef("Tire Lever Set", "Automotive Tools", "Set of 3 steel tire levers.", 260.0, 7, 3),
    DemoDef("Spark Plug", "Other Parts", "Standard spark plug.", 150.0, 18, 6)
)

// ---------- JSON ----------
private fun Product.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("category", category)
    .put("description", description).put("barcode", barcode)
    .put("price", price).put("stock", stock).put("minStock", minStock).put("demo", demo)
    .put("image", imagePath)

private fun JSONObject.toProduct(): Product = Product(
    getString("id"), getString("name"), getString("category"), getString("description"),
    getString("barcode"), getDouble("price"), getInt("stock"), getInt("minStock"),
    optBoolean("demo", false), optString("image", "")
)

private fun Sale.toJson(): JSONObject {
    val arr = JSONArray()
    lines.forEach {
        arr.put(JSONObject().put("pid", it.productId).put("name", it.name).put("qty", it.qty).put("price", it.price))
    }
    return JSONObject().put("id", id).put("time", time).put("total", total)
        .put("paid", paid).put("method", method).put("demo", demo).put("lines", arr)
}

private fun JSONObject.toSale(): Sale {
    val arr = getJSONArray("lines")
    val ls = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        SaleLine(o.getString("pid"), o.getString("name"), o.getInt("qty"), o.getDouble("price"))
    }
    return Sale(
        getString("id"), getLong("time"), ls, getDouble("total"),
        getDouble("paid"), getString("method"), optBoolean("demo", false)
    )
}

// ---------- The data store (saved on this device) ----------
class Store(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("stockflow_data", Context.MODE_PRIVATE)

    val products = mutableStateListOf<Product>()
    val sales = mutableStateListOf<Sale>()
    val cart = mutableStateMapOf<String, Int>()

    var shopName by mutableStateOf(prefs.getString("shopName", "StockFlow Vulcanizing Shop") ?: "")
    var shopInfo by mutableStateOf(prefs.getString("shopInfo", "") ?: "")
    var currency by mutableStateOf(prefs.getString("currency", "₱") ?: "₱")
    var lowStockDefault by mutableStateOf(prefs.getInt("lowStockDefault", 5))
    var receiptFooter by mutableStateOf(prefs.getString("receiptFooter", "Thank you! Come again.") ?: "")
    var receiptShowInfo by mutableStateOf(prefs.getBoolean("receiptShowInfo", true))
    var darkMode by mutableStateOf(prefs.getBoolean("darkMode", false))
    var exportDemo by mutableStateOf(prefs.getBoolean("exportDemo", false))

    private var productCounter = prefs.getInt("productCounter", 0)
    private var saleCounter = prefs.getInt("saleCounter", 0)

    init {
        load()
    }

    fun money(x: Double): String = currency + String.format(Locale.US, "%,.2f", x)

    fun saveSettings() {
        prefs.edit()
            .putString("shopName", shopName)
            .putString("shopInfo", shopInfo)
            .putString("currency", currency)
            .putInt("lowStockDefault", lowStockDefault)
            .putString("receiptFooter", receiptFooter)
            .putBoolean("receiptShowInfo", receiptShowInfo)
            .putBoolean("darkMode", darkMode)
            .putBoolean("exportDemo", exportDemo)
            .apply()
    }

    private fun saveData() {
        val pa = JSONArray()
        products.forEach { pa.put(it.toJson()) }
        val sa = JSONArray()
        sales.forEach { sa.put(it.toJson()) }
        prefs.edit()
            .putString("products", pa.toString())
            .putString("sales", sa.toString())
            .putInt("productCounter", productCounter)
            .putInt("saleCounter", saleCounter)
            .apply()
    }

    private fun load() {
        val pj = prefs.getString("products", null)
        if (pj == null) {
            seedDemo()
            return
        }
        try {
            val pa = JSONArray(pj)
            for (i in 0 until pa.length()) products.add(pa.getJSONObject(i).toProduct())
            val sj = prefs.getString("sales", null)
            if (sj != null) {
                val sa = JSONArray(sj)
                for (i in 0 until sa.length()) sales.add(sa.getJSONObject(i).toSale())
            }
        } catch (e: Exception) {
            products.clear()
            sales.clear()
        }
    }

    private fun seedDemo() {
        DEMO_ITEMS.forEach { d ->
            products.add(
                Product(nextProductId(), d.name, d.category, d.description, newBarcode(), d.price, d.stock, d.min, true)
            )
        }
        val rnd = java.util.Random(42)
        var n = 0
        for (daysAgo in 29 downTo 0) {
            val count = rnd.nextInt(4)
            for (k in 0 until count) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                cal.set(Calendar.HOUR_OF_DAY, 8 + rnd.nextInt(10))
                cal.set(Calendar.MINUTE, rnd.nextInt(60))
                cal.set(Calendar.SECOND, 0)
                val picks = products.toList().shuffled(java.util.Random(rnd.nextLong())).take(1 + rnd.nextInt(3))
                val lines = picks.map { SaleLine(it.id, it.name, 1 + rnd.nextInt(2), it.price) }
                val total = lines.sumOf { it.qty * it.price }
                val method = listOf("Cash", "GCash", "Cash")[rnd.nextInt(3)]
                n++
                sales.add(
                    Sale("DEMO-" + n.toString().padStart(3, '0'), cal.timeInMillis, lines, total, total, method, true)
                )
            }
        }
        saveData()
    }

    private fun nextProductId(): String {
        productCounter++
        return "P" + productCounter.toString().padStart(4, '0')
    }

    fun newBarcode(): String {
        val rnd = java.util.Random()
        while (true) {
            val code = "SF" + (10000000 + rnd.nextInt(90000000))
            if (products.none { it.barcode == code }) return code
        }
    }

    // ----- product photos -----
    // Each photo is a small JPEG saved in the app's private storage.
    // The file name starts with the product ID, so it is tied to the right product.
    private fun imageDir(): File {
        val d = File(appContext.filesDir, "product_images")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun saveImage(productId: String, bmp: Bitmap): String? {
        return try {
            val f = File(imageDir(), productId + "_" + System.currentTimeMillis() + ".jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteImage(path: String) {
        if (path.isBlank()) return
        try {
            File(path).delete()
        } catch (e: Exception) {
        }
    }

    // ----- products -----
    fun validateProduct(p: Product): String? {
        if (p.name.isBlank()) return "Product name is required."
        if (p.price <= 0.0) return "Price must be greater than 0."
        if (p.stock < 0) return "Stock must be a whole number (0 or more)."
        if (p.minStock < 0) return "Minimum stock must be a whole number (0 or more)."
        if (p.barcode.isBlank()) return "Barcode is required."
        if (p.barcode.any { it.code < 33 || it.code > 126 }) return "Barcode cannot contain spaces or special characters."
        if (products.any { it.id != p.id && it.barcode == p.barcode }) return "This barcode is already assigned to another product."
        return null
    }

    // photo is optional: pass null to add a product without a photo
    fun addProduct(p: Product, photo: Bitmap? = null): String? {
        val err = validateProduct(p)
        if (err != null) return err
        val newId = nextProductId()
        var path = ""
        if (photo != null) {
            path = saveImage(newId, photo)
                ?: return "Could not save the photo. Try another photo, or skip the photo."
        }
        products.add(p.copy(id = newId, imagePath = path, demo = false))
        saveData()
        return null
    }

    // photo = null and removePhoto = false keeps the current photo unchanged
    fun updateProduct(p: Product, photo: Bitmap? = null, removePhoto: Boolean = false): String? {
        val err = validateProduct(p)
        if (err != null) return err
        val i = products.indexOfFirst { it.id == p.id }
        if (i < 0) return "Product not found."
        val oldPath = products[i].imagePath
        var path = oldPath
        if (photo != null) {
            path = saveImage(p.id, photo)
                ?: return "Could not save the photo. Try another photo, or keep the current one."
        } else if (removePhoto) {
            path = ""
        }
        products[i] = p.copy(imagePath = path, demo = false)
        if (path != oldPath) deleteImage(oldPath)
        val q = cart[p.id]
        if (q != null && q > p.stock) {
            if (p.stock <= 0) cart.remove(p.id) else cart[p.id] = p.stock
        }
        saveData()
        return null
    }

    fun deleteProduct(id: String) {
        products.firstOrNull { it.id == id }?.let { deleteImage(it.imagePath) }
        products.removeAll { it.id == id }
        cart.remove(id)
        saveData()
    }

    fun adjustStock(id: String, newStock: Int) {
        val i = products.indexOfFirst { it.id == id }
        if (i < 0) return
        products[i] = products[i].copy(stock = newStock)
        val q = cart[id]
        if (q != null && q > newStock) {
            if (newStock <= 0) cart.remove(id) else cart[id] = newStock
        }
        saveData()
    }

    fun regenerateBarcode(id: String) {
        val i = products.indexOfFirst { it.id == id }
        if (i < 0) return
        products[i] = products[i].copy(barcode = newBarcode())
        saveData()
    }

    fun applyThresholdToAll() {
        for (i in products.indices) products[i] = products[i].copy(minStock = lowStockDefault)
        saveData()
    }

    fun hasDemo(): Boolean = products.any { it.demo } || sales.any { it.demo }

    fun clearDemo() {
        val ids = products.filter { it.demo }.map { it.id }
        products.filter { it.demo }.forEach { deleteImage(it.imagePath) }
        products.removeAll { it.demo }
        sales.removeAll { it.demo }
        ids.forEach { cart.remove(it) }
        saveData()
    }

    // Adds sample products and sample transactions (all marked DEMO).
    // Returns false if demo data is already loaded, so nothing gets duplicated.
    fun addDemoData(): Boolean {
        if (hasDemo()) return false
        val demoProducts = mutableListOf<Product>()
        DEMO_ITEMS.forEach { d ->
            val p = Product(
                nextProductId(), d.name, d.category, d.description,
                newBarcode(), d.price, d.stock, d.min, true
            )
            products.add(p)
            demoProducts.add(p)
        }
        val rnd = java.util.Random(42)
        var n = 0
        for (daysAgo in 29 downTo 0) {
            val count = rnd.nextInt(4)
            for (k in 0 until count) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                cal.set(Calendar.HOUR_OF_DAY, 8 + rnd.nextInt(10))
                cal.set(Calendar.MINUTE, rnd.nextInt(60))
                cal.set(Calendar.SECOND, 0)
                val picks = demoProducts.shuffled(java.util.Random(rnd.nextLong())).take(1 + rnd.nextInt(3))
                val lines = picks.map { SaleLine(it.id, it.name, 1 + rnd.nextInt(2), it.price) }
                val total = lines.sumOf { it.qty * it.price }
                val method = listOf("Cash", "GCash", "Cash")[rnd.nextInt(3)]
                n++
                sales.add(
                    Sale("DEMO-" + n.toString().padStart(3, '0'), cal.timeInMillis, lines, total, total, method, true)
                )
            }
        }
        saveData()
        return true
    }

    // ----- cart -----
    fun cartLines(): List<Pair<Product, Int>> =
        cart.entries.toList().mapNotNull { e -> products.firstOrNull { it.id == e.key }?.let { it to e.value } }

    fun cartTotal(): Double = cartLines().sumOf { (p, q) -> p.price * q }

    fun addToCart(p: Product): String? {
        val current = cart[p.id] ?: 0
        if (p.stock <= 0) return "${p.name} is out of stock."
        if (current + 1 > p.stock) return "Only ${p.stock} in stock for ${p.name}."
        cart[p.id] = current + 1
        return null
    }

    fun setCartQty(p: Product, qty: Int): String? {
        if (qty <= 0) {
            cart.remove(p.id)
            return null
        }
        if (qty > p.stock) return "Only ${p.stock} in stock for ${p.name}."
        cart[p.id] = qty
        return null
    }

    fun removeFromCart(id: String) {
        cart.remove(id)
    }

    // ----- sales -----
    fun completeSale(paidInput: Double?, method: String): SaleResult {
        val lines = cartLines()
        if (lines.isEmpty()) return SaleResult(null, "The cart is empty.")
        for ((p, q) in lines) {
            if (q > p.stock) return SaleResult(null, "Not enough stock for ${p.name} (available: ${p.stock}).")
        }
        val total = lines.sumOf { (p, q) -> p.price * q }
        val paid: Double
        if (method == "Cash") {
            if (paidInput == null) return SaleResult(null, "Enter the amount received.")
            if (paidInput < total - 0.001) return SaleResult(null, "Payment is not enough. Total is ${money(total)}.")
            paid = paidInput
        } else {
            paid = total
        }
        val low = mutableListOf<String>()
        val saleLines = mutableListOf<SaleLine>()
        for ((p, q) in lines) {
            val i = products.indexOfFirst { it.id == p.id }
            val updated = p.copy(stock = p.stock - q)
            products[i] = updated
            saleLines.add(SaleLine(p.id, p.name, q, p.price))
            if (updated.stock <= updated.minStock) {
                low.add(if (updated.stock <= 0) "${p.name} (out of stock)" else "${p.name} (${updated.stock} left)")
            }
        }
        saleCounter++
        val sale = Sale(
            "TX" + saleCounter.toString().padStart(5, '0'),
            System.currentTimeMillis(), saleLines, total, paid, method, false
        )
        sales.add(sale)
        cart.clear()
        saveData()
        return SaleResult(sale, null, low)
    }

    fun receiptText(sale: Sale): String {
        val sb = StringBuilder()
        sb.appendLine(shopName)
        if (receiptShowInfo && shopInfo.isNotBlank()) sb.appendLine(shopInfo)
        sb.appendLine("--------------------------------")
        sb.appendLine("Receipt: ${sale.id}")
        sb.appendLine("Date: ${formatDateTime(sale.time)}")
        sb.appendLine("Payment: ${sale.method}")
        if (sale.demo) sb.appendLine("*** DEMO TRANSACTION ***")
        sb.appendLine("--------------------------------")
        sale.lines.forEach { l ->
            sb.appendLine(l.name)
            sb.appendLine("  ${l.qty} x ${money(l.price)} = ${money(l.qty * l.price)}")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("TOTAL: ${money(sale.total)}")
        sb.appendLine("Paid: ${money(sale.paid)}")
        sb.appendLine("Change: ${money(sale.paid - sale.total)}")
        if (receiptFooter.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(receiptFooter)
        }
        return sb.toString()
    }

    fun salesReportText(list: List<Sale>, periodLabel: String): String {
        val sb = StringBuilder()
        sb.appendLine("$shopName - SALES REPORT")
        sb.appendLine("Period: $periodLabel")
        sb.appendLine("Generated: ${formatDateTime(System.currentTimeMillis())}")
        sb.appendLine("================================")
        sb.appendLine("Transactions: ${list.size}")
        sb.appendLine("Items sold: ${list.sumOf { s -> s.lines.sumOf { l -> l.qty } }}")
        sb.appendLine("Total sales: ${money(list.sumOf { it.total })}")
        sb.appendLine()
        list.forEach { s ->
            sb.appendLine("${s.id} | ${formatDateTime(s.time)} | ${s.method}")
            s.lines.forEach { l ->
                sb.appendLine("   ${l.qty} x ${l.name} @ ${money(l.price)} = ${money(l.qty * l.price)}")
            }
            sb.appendLine("   TOTAL: ${money(s.total)}")
            sb.appendLine()
        }
        return sb.toString()
    }

    fun salesCsv(list: List<Sale>): String {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.appendLine("Transaction ID,Date,Item,Quantity,Unit Price,Line Total,Payment Method,Demo")
        list.forEach { s ->
            s.lines.forEach { l ->
                sb.appendLine(
                    listOf(
                        q(s.id), q(formatDateTime(s.time)), q(l.name), l.qty.toString(),
                        String.format(Locale.US, "%.2f", l.price),
                        String.format(Locale.US, "%.2f", l.qty * l.price),
                        q(s.method), if (s.demo) "yes" else "no"
                    ).joinToString(",")
                )
            }
        }
        sb.appendLine(",,,,,${String.format(Locale.US, "%.2f", list.sumOf { it.total })},TOTAL,")
        return sb.toString()
    }
}