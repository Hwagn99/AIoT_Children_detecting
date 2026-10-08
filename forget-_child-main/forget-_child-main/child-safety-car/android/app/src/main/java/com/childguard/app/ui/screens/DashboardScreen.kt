package com.childguard.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.childguard.app.data.model.Labels
import com.childguard.app.data.model.StatusDto
import com.childguard.app.ui.theme.levelColor
import com.childguard.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.toast) {
        ui.toast?.let { snackbar.showSnackbar(it); vm.toastShown() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Giám sát") }, actions = { ConnectionDot(ui.connected) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusBanner(ui.status)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(Icons.Filled.Face, "Trẻ", ui.status.children.toString(), Modifier.weight(1f))
                InfoTile(Icons.Filled.Person, "Người lớn", ui.status.adults.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(
                    Icons.Filled.Timer, "Trẻ một mình",
                    formatDuration(ui.status.aloneSeconds.toInt()), Modifier.weight(1f),
                )
                InfoTile(
                    Icons.Filled.Thermostat, "Nhiệt độ",
                    ui.status.tempC?.let { "%.1f°C".format(it) } ?: "--", Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoTile(Icons.Filled.Mood, "Cảm xúc", Labels.emotion(ui.status.emotion), Modifier.weight(1f))
                InfoTile(Icons.Filled.Lock, "Cửa xe", Labels.door(ui.status.doorState), Modifier.weight(1f))
            }

            DeviceHealth(ui.status)

            if (ui.status.level > 0) {
                Button(
                    onClick = vm::silence,
                    enabled = !ui.status.silenced,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.VolumeOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.status.silenced) "Còi đã tắt" else "Tắt còi (tôi đã biết)")
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ảnh từ camera", style = MaterialTheme.typography.titleMedium)
                    val bmp = ui.snapshot
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = "Ảnh camera",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    OutlinedButton(onClick = { vm.refreshSnapshot() }, enabled = !ui.snapshotLoading) {
                        if (ui.snapshotLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Chụp ảnh ngay")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(s: StatusDto) {
    val color = levelColor(s.level)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(color).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (s.level == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                null, tint = Color.White, modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(Labels.level(s.level), color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium)
        }
        Text(s.message.ifBlank { "Hệ thống đang hoạt động bình thường" }, color = Color.White)
    }
}

@Composable
private fun InfoTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DeviceHealth(s: StatusDto) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("Camera") },
            leadingIcon = { ConnectionDot(s.cameraOnline) })
        AssistChip(onClick = {}, label = { Text("Phần cứng") },
            leadingIcon = { ConnectionDot(s.hardwareOnline) })
    }
}

@Composable
private fun ConnectionDot(ok: Boolean) {
    Box(
        Modifier.padding(horizontal = 8.dp).size(10.dp).clip(RoundedCornerShape(50))
            .background(if (ok) Color(0xFF2E7D32) else Color(0xFFC62828))
    )
}

private fun formatDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
