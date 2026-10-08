package com.childguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.childguard.app.data.model.EventDto
import com.childguard.app.data.model.Labels
import com.childguard.app.ui.theme.levelColor
import com.childguard.app.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val ui by vm.ui.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Lịch sử sự kiện") }, actions = {
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Tải lại") }
            })
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null -> Text("Lỗi: ${ui.error}", Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error)
                ui.events.isEmpty() -> Text("Chưa có sự kiện nào", Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { items(ui.events, key = { it.id }) { EventRow(it) } }
            }
        }
    }
}

@Composable
private fun EventRow(e: EventDto) {
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(levelColor(e.level)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Labels.level(e.level), style = MaterialTheme.typography.titleMedium, color = levelColor(e.level))
                Text(e.reason.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Cảm xúc: ${Labels.emotion(e.emotion)} • Một mình ${e.aloneSeconds.toInt()}s" +
                        (e.tempC?.let { " • %.0f°C".format(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Text(formatTs(e.ts), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

private fun formatTs(ts: Double): String =
    SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date((ts * 1000).toLong()))
