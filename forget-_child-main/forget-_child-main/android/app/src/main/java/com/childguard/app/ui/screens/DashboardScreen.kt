package com.childguard.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.childguard.app.ui.viewmodel.DashboardState
import com.childguard.app.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val bgColor = Color(0xFFF8FAFC)

    Scaffold(
        containerColor = bgColor,
        topBar = { TopNavBar(uiState.isOnline) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { CameraFeedSection(uiState) }
            item { AIStatusCard(uiState) }
            item { SensorGrid(uiState) }
            item { ControlPanel(viewModel) }
            item { SystemLogSection(uiState.logs) }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun TopNavBar(isOnline: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SafeCam-DCN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (isOnline) Color(0xFF10B981) else Color.Red))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isOnline) "Hệ thống Online" else "Offline", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun CameraFeedSection(uiState: DashboardState) {
    val borderColor by animateColorAsState(
        targetValue = if (uiState.isDanger) Color(0xFFEF4444) else Color(0xFF10B981),
        animationSpec = tween(500),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E293B))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text(text = "AI: ${uiState.aiEmotion}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AIStatusCard(uiState: DashboardState) {
    val bgColor by animateColorAsState(targetValue = if (uiState.isDanger) Color(0xFFFEF2F2) else Color(0xFFECFDF5))
    val textColor by animateColorAsState(targetValue = if (uiState.isDanger) Color(0xFFDC2626) else Color(0xFF059669))

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(imageVector = if (uiState.isDanger) Icons.Default.Warning else Icons.Default.Face, contentDescription = null, tint = textColor, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (uiState.isDanger) "CẢNH BÁO KHẨN CẤP" else "Trạng thái: An Toàn",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor,
            )
        }
    }
}

@Composable
fun SensorGrid(uiState: DashboardState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SensorItem(modifier = Modifier.weight(1f), icon = Icons.Default.Thermostat, title = "Nhiệt độ", value = "${uiState.temperature}°C", isAlert = uiState.temperature > 35)
        SensorItem(modifier = Modifier.weight(1f), icon = Icons.Default.Air, title = "Khí thở (CO2)", value = uiState.gasStatus, isAlert = uiState.gasStatus != "Bình thường")
    }
}

@Composable
fun SensorItem(modifier: Modifier = Modifier, icon: ImageVector, title: String, value: String, isAlert: Boolean) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = if(isAlert) Color.Red else Color(0xFF64748B))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if(isAlert) Color.Red else Color.Black)
        }
    }
}

@Composable
fun ControlPanel(viewModel: DashboardViewModel) {
    Column {
        Text("BẢNG ĐIỀU KHIỂN KHẨN CẤP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ControlButton("Bật Còi Báo Động", Icons.Default.NotificationsActive, Color(0xFFFFF7ED), Color(0xFFEA580C)) {
                    viewModel.sendHardwareCommand("Còi (Horn)", "Bật")
                }
                Spacer(modifier = Modifier.height(12.dp))
                ControlButton("Chớp Đèn Cảnh Báo", Icons.Default.Lightbulb, Color(0xFFEFF6FF), Color(0xFF2563EB)) {
                    viewModel.sendHardwareCommand("Đèn (Light)", "Chớp nháy")
                }
                Spacer(modifier = Modifier.height(12.dp))
                ControlButton("Hé Mở Kính (Servo)", Icons.Default.CarRepair, Color(0xFFECFEFF), Color(0xFF0891B2)) {
                    viewModel.sendHardwareCommand("Cửa kính (Servo)", "Mở hé")
                }
            }
        }
    }
}

@Composable
fun ControlButton(text: String, icon: ImageVector, bgColor: Color, contentColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = contentColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun SystemLogSection(logs: List<String>) {
    Column {
        Text("NHẬT KÝ HỆ THỐNG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth().height(150.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                items(logs) { log ->
                    Text(
                        text = log,
                        fontSize = 13.sp,
                        color = if(log.contains("CẢNH BÁO") || log.contains("Auto")) Color.Red else Color.DarkGray,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
