@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.vulcanizing

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Brand colors (change these to match your logo exactly) ----
val BrandTeal = Color(0xFF12A58C)
val BrandNavy = Color(0xFF0D2B45)
val BrandLime = Color(0xFF8BD34B)

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1EA),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F1EA),
    onSecondaryContainer = BrandNavy,
    tertiary = BrandLime,
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF14202B),
    surface = Color.White,
    onSurface = Color(0xFF14202B),
    surfaceVariant = Color(0xFFE6EDF1),
    onSurfaceVariant = Color(0xFF45515C),
    error = Color(0xFFC62828)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3CCBB1),
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF0F4A3F),
    onPrimaryContainer = Color(0xFFD3F1EA),
    secondary = Color(0xFF9CC3E6),
    onSecondary = Color(0xFF0D2B45),
    secondaryContainer = Color(0xFF0F4A3F),
    onSecondaryContainer = Color(0xFFD3F1EA),
    tertiary = BrandLime,
    background = Color(0xFF0E161D),
    onBackground = Color(0xFFE6EEF3),
    surface = Color(0xFF16212A),
    onSurface = Color(0xFFE6EEF3),
    surfaceVariant = Color(0xFF22303B),
    onSurfaceVariant = Color(0xFFB5C2CC),
    error = Color(0xFFFF8A80)
)

@Composable
fun StockFlowTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

// ---- Shared components ----
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(3.dp)) {
            Image(
                painter = painterResource(id = R.drawable.stockflow_logo),
                contentDescription = "StockFlow logo",
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusChip(status: StockStatus) {
    val (text, color) = when (status) {
        StockStatus.IN -> "In Stock" to BrandTeal
        StockStatus.LOW -> "Low Stock" to Color(0xFFB77900)
        StockStatus.OUT -> "Out of Stock" to Color(0xFFC62828)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DemoBadge() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(
            "DEMO", fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyState(emoji: String, title: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(emoji, fontSize = 40.sp)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            hint, fontSize = 13.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, horizontalPadding: Dp = 16.dp, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(options) { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun CategoryChips(selected: String, horizontalPadding: Dp = 16.dp, onSelect: (String) -> Unit) {
    val options = listOf("All") + CATEGORIES
    ChipRow(options, options.indexOf(selected).coerceAtLeast(0), horizontalPadding) { onSelect(options[it]) }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ReceiptDialog(store: Store, sale: Sale, warning: String = "", onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text = store.receiptText(sale)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Receipt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                if (warning.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(warning, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                ctx.startActivity(Intent.createChooser(i, "Share receipt"))
            }) { Text("Share") }
        }
    )
}