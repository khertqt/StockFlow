@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.vulcanizing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SalesScreen(store: Store, notify: (String) -> Unit) {
    val ctx = LocalContext.current
    var range by remember { mutableIntStateOf(0) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var includeDemo by remember { mutableStateOf(true) }
    var monthly by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Sale?>(null) }
    var pendingText by remember { mutableStateOf("") }

    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            notify(if (writeTextToUri(ctx, uri, pendingText)) "Report saved." else "Export failed. Nothing was saved.")
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            notify(if (writeTextToUri(ctx, uri, pendingText)) "CSV saved." else "Export failed. Nothing was saved.")
        }
    }

    val now = System.currentTimeMillis()
    val (rangeStart, rangeEnd) = when (range) {
        0 -> startOfDay(now) to endOfDay(now)
        1 -> startOfWeek(now) to endOfDay(now)
        2 -> startOfMonth(now) to endOfDay(now)
        else -> (customStart ?: startOfDay(now)) to (customEnd ?: endOfDay(now))
    }
    val periodLabel = if (range == 0) formatDate(rangeStart) else formatDate(rangeStart) + " to " + formatDate(rangeEnd)

    val filtered = store.sales
        .filter { it.time in rangeStart..rangeEnd && (includeDemo || !it.demo) }
        .sortedByDescending { it.time }
    val revenue = filtered.sumOf { it.total }
    val itemsSold = filtered.sumOf { s -> s.lines.sumOf { l -> l.qty } }
    val best = filtered.flatMap { it.lines }
        .groupBy { it.name }
        .mapValues { e -> e.value.sumOf { l -> l.qty } }
        .maxByOrNull { it.value }

    // daily chart data
    val dayMs = 24L * 60 * 60 * 1000
    val lastDay = startOfDay(rangeEnd)
    val firstDay = maxOf(startOfDay(rangeStart), lastDay - 61 * dayMs)
    val dayLabels = mutableListOf<String>()
    val dayValues = mutableListOf<Double>()
    var d = firstDay
    while (d <= lastDay) {
        val next = d + dayMs
        dayValues.add(filtered.filter { it.time >= d && it.time < next }.sumOf { it.total })
        dayLabels.add(formatShortDate(d))
        d = next
    }

    // monthly chart data (last 6 months)
    val monthLabels = mutableListOf<String>()
    val monthValues = mutableListOf<Double>()
    for (k in 5 downTo 0) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -k)
        val ms = startOfMonth(cal.timeInMillis)
        val c2 = Calendar.getInstance()
        c2.timeInMillis = ms
        c2.add(Calendar.MONTH, 1)
        val me = c2.timeInMillis
        monthValues.add(
            store.sales.filter { (includeDemo || !it.demo) && it.time >= ms && it.time < me }.sumOf { it.total }
        )
        monthLabels.add(SimpleDateFormat("MMM yy", Locale.getDefault()).format(Date(ms)))
    }

    val visible = filtered.filter { s ->
        search.isBlank() || s.id.contains(search, true) || s.lines.any { it.name.contains(search, true) }
    }

    fun exportList(): List<Sale>? {
        val list = filtered.filter { store.exportDemo || !it.demo }
        if (list.isEmpty()) {
            notify("Nothing to export for this period. Demo data is excluded unless enabled in Settings.")
            return null
        }
        return list
    }

    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sales", "Performance & transactions")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ChipRow(listOf("Today", "This Week", "This Month", "Custom"), range, 0.dp) {
                    if (it == 3) showPicker = true else range = it
                }
            }
            item { Text("Period: $periodLabel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (store.sales.any { it.demo }) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = includeDemo, onCheckedChange = { includeDemo = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Include demo data", fontSize = 13.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total revenue", store.money(revenue), Modifier.weight(1f))
                    StatCard("Transactions", "${filtered.size}", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Items sold", "$itemsSold", Modifier.weight(1f))
                    StatCard(
                        "Best seller",
                        best?.let { "${it.key} (${it.value})" } ?: "—",
                        Modifier.weight(1f)
                    )
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Sales over time", fontWeight = FontWeight.Bold)
                        ChipRow(listOf("Daily", "Monthly"), if (monthly) 1 else 0, 0.dp) { monthly = it == 1 }
                        if (monthly) {
                            Text(
                                "Last 6 months", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LineChart(monthLabels, monthValues, store.currency)
                        } else {
                            LineChart(dayLabels, dayValues, store.currency)
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Export report", fontWeight = FontWeight.Bold)
                        Text(
                            "Exports follow the period selected above. For Google Sheets: save the CSV to Google Drive, then open it with Google Sheets.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                val list = exportList()
                                if (list != null) {
                                    pendingText = store.salesReportText(list, periodLabel)
                                    saveTxt.launch("sales_report_$stamp.txt")
                                }
                            }) { Text("Text (.txt)") }
                            Button(modifier = Modifier.weight(1f), onClick = {
                                val list = exportList()
                                if (list != null) {
                                    pendingText = store.salesCsv(list)
                                    saveCsv.launch("sales_report_$stamp.csv")
                                }
                            }) { Text("CSV / Sheets") }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Search transaction ID or item") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            if (visible.isEmpty()) {
                item { EmptyState("🧾", "No transactions", "Completed sales for this period will appear here.") }
            }
            items(visible, key = { it.id }) { s ->
                AppCard(Modifier.fillMaxWidth(), onClick = { selected = s }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.id, fontWeight = FontWeight.Bold)
                            if (s.demo) {
                                Spacer(Modifier.width(6.dp))
                                DemoBadge()
                            }
                            Spacer(Modifier.weight(1f))
                            Text(store.money(s.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            formatDateTime(s.time) + "  •  " + s.method, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            s.lines.joinToString(", ") { "${it.qty}x ${it.name}" }, fontSize = 13.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        customStart = utcMillisToLocalStart(s)
                        customEnd = endOfDay(utcMillisToLocalStart(e))
                        range = 3
                    }
                    showPicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(500.dp))
        }
    }

    selected?.let { s -> ReceiptDialog(store, s) { selected = null } }
}