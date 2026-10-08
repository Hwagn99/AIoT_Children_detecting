package com.childguard.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.childguard.app.data.model.Labels
import com.childguard.app.data.model.StatusDto
import com.childguard.app.ui.theme.levelColor
import com.childguard.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val status = ui.status

    LaunchedEffect(ui.toast) {
        ui.toast?.let { snackbar.showSnackbar(it); vm.toastShown() }
    }
    // Dừng camera trực tiếp khi rời màn hình
    DisposableEffect(Unit) { onDispose { vm.stopLive() } }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = { SafeCamTopBar(ui.connected) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            if (ui.confirmPending) item { ParentConfirmCard(status, ui.confirmSending, vm::confirmRealChild) }
            item {
                CameraFeedSection(
                    ui.liveFrame ?: ui.snapshot, status.cameraOnline, ui.snapshotLoading,
                    vm::refreshSnapshot, status.level >= 2,
                    live = ui.liveOn, liveLoading = ui.liveLoading,
                    onToggleLive = { if (ui.liveOn) vm.stopLive() else vm.startLive() },
                )
            }
            item { AIStatusCard(status) }
            item { SensorGrid(status) }
            item { AgeRangeSummary(status.ageRanges, status.unclassifiedPersons) }
            item { DeviceHealth(status) }
            if (status.level > 0) item { SilenceButton(status, vm::silence) }
        }
    }
}

@Composable
private fun SafeCamTopBar(connected: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SafeCam-DCN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text("Giám sát an toàn trong xe", style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) Color(0xFF10B981) else Color(0xFFEF4444)))
            Spacer(Modifier.width(8.dp))
            Text(if (connected) "Online" else "Offline", style = MaterialTheme.typography.labelMedium, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun CameraFeedSection(
    snapshot: android.graphics.Bitmap?,
    cameraOnline: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    isDanger: Boolean,
    live: Boolean = false,
    liveLoading: Boolean = false,
    onToggleLive: () -> Unit = {},
) {
    val borderColor by animateColorAsState(
        targetValue = if (isDanger) Color(0xFFEF4444) else Color(0xFF10B981),
        animationSpec = tween(500),
        label = "camera-border",
    )
    Box(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1E293B)).border(3.dp, borderColor, RoundedCornerShape(22.dp)),
    ) {
        if (snapshot != null) {
            Image(
                bitmap = snapshot.asImageBitmap(), contentDescription = "Ảnh camera gần nhất",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text("Chưa có ảnh camera", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (cameraOnline) Color(0xFF34D399) else Color(0xFFF87171)))
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    !cameraOnline -> "CAMERA OFFLINE"
                    live -> "CAMERA TRỰC TIẾP"
                    else -> "CAMERA LIVE"
                },
                color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            )
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(
                onClick = onToggleLive,
                enabled = cameraOnline,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (live) Color(0xFFEF4444) else Color.White.copy(alpha = 0.94f),
                ),
            ) {
                if (liveLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(
                    if (live) Icons.Filled.StopCircle else Icons.Filled.PlayCircle,
                    contentDescription = if (live) "Dừng xem trực tiếp" else "Xem trực tiếp",
                    tint = if (live) Color.White else Color(0xFF0F172A),
                )
            }
            FilledIconButton(
                onClick = onRefresh,
                enabled = !loading && !live,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.94f)),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.CameraAlt, contentDescription = "Chụp ảnh ngay", tint = Color(0xFF0F172A))
            }
        }
    }
}

@Composable
private fun AIStatusCard(status: StatusDto) {
    val danger = status.level >= 2
    val background by animateColorAsState(
        targetValue = when {
            danger -> Color(0xFFFEF2F2)
            status.level == 1 -> Color(0xFFEFF6FF)
            else -> Color(0xFFECFDF5)
        },
        animationSpec = tween(350),
        label = "status-background",
    )
    val foreground = levelColor(status.level)
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (danger) Icons.Filled.Warning else Icons.Filled.Face,
                contentDescription = null, tint = foreground, modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(Labels.level(status.level).uppercase(), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = foreground)
            Text(status.message.ifBlank { "Cảm xúc: ${Labels.emotion(status.emotion)}" },
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFF334155), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SensorGrid(status: StatusDto) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SensorItem(
                Modifier.weight(1f), Icons.Filled.Groups, "Người lớn",
                status.adults.toString(),
                isAlert = status.adults == 0 && status.children > 0,
            )
            SensorItem(
                Modifier.weight(1f), Icons.Filled.ChildCare, "Trẻ em",
                status.children.toString(),
                isAlert = status.children > 0,
            )
        }
        SensorItem(
            Modifier.fillMaxWidth(), Icons.Filled.Timer, "Trẻ ở một mình",
            formatDuration(status.aloneSeconds.toInt()), isAlert = status.aloneSeconds > 0,
        )
    }
}

@Composable
private fun SensorItem(modifier: Modifier, icon: ImageVector, title: String, value: String, isAlert: Boolean) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, tint = if (isAlert) Color(0xFFDC2626) else Color(0xFF64748B))
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = if (isAlert) Color(0xFFDC2626) else Color(0xFF0F172A))
        }
    }
}

@Composable
private fun AgeRangeSummary(ageRanges: Map<String, Int>, unclassified: Int) {
    val ranges = listOf("0-2", "3-5", "6-12", "13-17", "18+")
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Độ tuổi ước lượng", style = MaterialTheme.typography.titleMedium, color = Color(0xFF0F172A))
            Row(Modifier.fillMaxWidth()) {
                ranges.take(3).forEach { AgeRangeCell(it, ageRanges[it] ?: 0, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth()) {
                ranges.drop(3).forEach { AgeRangeCell(it, ageRanges[it] ?: 0, Modifier.weight(1f)) }
                Spacer(Modifier.weight(1f))
            }
            if (unclassified > 0) {
                Text("Chưa phân loại: $unclassified", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun AgeRangeCell(label: String, count: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}

@Composable
private fun DeviceHealth(status: StatusDto) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AssistChip(onClick = {}, label = { Text("Camera") }, leadingIcon = { StatusDot(status.cameraOnline) })
        AssistChip(onClick = {}, label = { Text("Phần cứng") }, leadingIcon = { StatusDot(status.hardwareOnline) })
        AssistChip(onClick = {}, label = { Text(Labels.emotion(status.emotion)) }, leadingIcon = { Icon(Icons.Filled.Mood, null) })
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(if (online) Color(0xFF10B981) else Color(0xFFEF4444)))
}

@Composable
private fun SilenceButton(status: StatusDto, onSilence: () -> Unit) {
    Button(
        onClick = onSilence,
        enabled = !status.silenced,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Filled.VolumeOff, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(if (status.silenced) "Còi đã tắt" else "Tắt còi báo động", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ParentConfirmCard(
    status: StatusDto,
    sending: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QuestionAnswer, null, tint = Color(0xFFD97706))
                Spacer(Modifier.width(8.dp))
                Text("Cần bạn xác nhận", style = MaterialTheme.typography.titleMedium, color = Color(0xFF92400E))
            }
            Text(
                status.verifyNote.ifBlank {
                    "Hệ thống không chắc đối tượng trong xe có phải trẻ thật không " +
                        "(nghi: ${status.verifiedSubject.ifBlank { "không rõ" }})."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF78350F),
            )
            Text(
                "Xác nhận này KHÔNG tắt cảnh báo — còi và mở cửa vẫn do luật an toàn quyết định.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF92400E),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAnswer(true) }, enabled = !sending, modifier = Modifier.weight(1f),
                ) { Text("Có, là trẻ thật") }
                OutlinedButton(
                    onClick = { onAnswer(false) }, enabled = !sending, modifier = Modifier.weight(1f),
                ) { Text("Không phải trẻ") }
            }
        }
    }
}

private fun formatDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
