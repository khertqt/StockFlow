package com.example.vulcanizing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            StockFlowTheme(darkTheme = store.darkMode) {
                AppRoot(store)
            }
        }
    }
}

@Composable
fun AppRoot(store: Store) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    Crossfade(targetState = showSplash, animationSpec = tween(500), label = "splash") { splash ->
        if (splash) SplashScreen { showSplash = false } else MainShell(store)
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val grow = remember { Animatable(0.75f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(900)) }
        launch { grow.animateTo(1f, tween(900)) }
        delay(2300.milliseconds)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                this.alpha = fade.value
                scaleX = grow.value
                scaleY = grow.value
            }
        ) {
            Image(
                painter = painterResource(id = R.drawable.stockflow_logo),
                contentDescription = "StockFlow logo",
                modifier = Modifier.size(200.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("StockFlow", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BrandNavy)
            Text("Inventory & Sales Management", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

private data class NavTab(val label: String, val icon: ImageVector)

// Messages that mean "something went wrong" are shown in red
private fun isWarning(msg: String): Boolean {
    val m = msg.lowercase()
    return listOf(
        "only ", "out of stock", "not ", "failed", "could not", "denied",
        "nothing", "type a", "empty", "cannot", "enter "
    ).any { m.contains(it) }
}

@Composable
fun MainShell(store: Store) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Top notification banner
    var bannerText by remember { mutableStateOf("") }
    var bannerVisible by remember { mutableStateOf(false) }
    var bannerId by remember { mutableIntStateOf(0) }
    val notify: (String) -> Unit = { msg ->
        bannerText = msg
        bannerVisible = true
        bannerId++
    }
    LaunchedEffect(bannerId) {
        if (bannerId > 0) {
            delay(2500.milliseconds)
            bannerVisible = false
        }
    }

    val tabs = listOf(
        NavTab("Check Out", Icons.Default.ShoppingCart),
        NavTab("Stocks", Icons.AutoMirrored.Filled.List),
        NavTab("Sales", Icons.Default.DateRange),
        NavTab("Scan", Icons.Default.Search),
        NavTab("Settings", Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> CheckoutScreen(store, notify)
                1 -> StocksScreen(store, notify)
                2 -> SalesScreen(store, notify)
                3 -> ScanScreen(store, notify)
                else -> SettingsScreen(store, notify)
            }

            // The banner sits on top of everything in this area
            AnimatedVisibility(
                visible = bannerVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                val warning = isWarning(bannerText)
                Surface(
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (warning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clickable { bannerVisible = false }
                ) {
                    Text(
                        bannerText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}