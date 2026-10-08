package com.childguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.childguard.app.data.model.ReportDto
import com.childguard.app.ui.viewmodel.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Báo cáo sau sự cố do Crew 2 (Analyst + Reporter) sinh — GET /reports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: ReportsViewModel) {
    val ui by vm.ui.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Báo cáo sự cố") }, actions = {
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Tải lại") }
            })
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null -> Text("Lỗi: ${ui.error}", Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error)
                ui.reports.isEmpty() -> Text(
                    "Chưa có báo cáo nào.\nBáo cáo được tạo tự động sau khi sự cố kết thúc.",
                    Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { items(ui.reports, key = { it.id }) { ReportCard(it, ui.expanded == it.id, { vm.toggle(it.id) }) } }
            }
        }
    }
}

@Composable
private fun ReportCard(r: ReportDto, expanded: Boolean, onToggle: () -> Unit) {
    Card(onClick = onToggle, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(formatTs(r.ts), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                if (r.falseAlarm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Báo động sai", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD97706))
                    }
                } else {
                    Badge(containerColor = Color(0xFFE0F2FE), contentColor = Color(0xFF075985)) {
                        Text(formatDuration(r.durationS.toInt()))
                    }
                }
            }

            Text(r.summary.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

            if (r.falseAlarm && r.falseAlarmNote.isNotBlank()) {
                Text(r.falseAlarmNote, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD97706))
            }

            if (expanded) {
                if (r.timeline.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Dòng thời gian", style = MaterialTheme.typography.titleSmall)
                    r.timeline.forEach { line ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(Color(0xFF0EA5E9)))
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (r.systemResponse.isNotBlank()) {
                    HorizontalDivider()
                    Text("Hệ thống phản ứng", style = MaterialTheme.typography.titleSmall)
                    Text(r.systemResponse, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Chạm để xem chi tiết", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

private fun formatTs(ts: Double): String =
    SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date((ts * 1000).toLong()))

private fun formatDuration(sec: Int): String = if (sec >= 60) "${sec / 60}p ${sec % 60}s" else "${sec}s"