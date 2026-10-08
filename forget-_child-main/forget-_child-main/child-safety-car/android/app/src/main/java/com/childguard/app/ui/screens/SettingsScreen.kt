package com.childguard.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.childguard.app.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onLoggedOut: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val s = ui.settings

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(ui.loggedOut) { if (ui.loggedOut) onLoggedOut() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cài đặt hệ thống") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (ui.loading) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Group("Ngưỡng thời gian trẻ ở một mình") {
                SliderRow("Bắt đầu theo dõi", s.graceSeconds, 0..120, "s") { v -> vm.edit { it.copy(graceSeconds = v) } }
                SliderRow("Cảnh báo", s.warnSeconds, 10..600, "s") { v -> vm.edit { it.copy(warnSeconds = v) } }
                SliderRow("Khẩn cấp", s.emergencySeconds, 30..1200, "s") { v -> vm.edit { it.copy(emergencySeconds = v) } }
            }

            Group("Ngưỡng nhiệt độ cabin") {
                SliderRow("Cảnh báo", s.tempWarnC.roundToInt(), 25..50, "°C") { v -> vm.edit { it.copy(tempWarnC = v.toDouble()) } }
                SliderRow("Khẩn cấp", s.tempEmergencyC.roundToInt(), 30..60, "°C") { v -> vm.edit { it.copy(tempEmergencyC = v.toDouble()) } }
            }

            Group("Phân tích AI") {
                SliderRow("Chu kỳ phân tích cảm xúc", s.analysisIntervalS, 2..60, "s") { v -> vm.edit { it.copy(analysisIntervalS = v) } }
            }

            Group("Hành động tự động") {
                SwitchRow("Tự động mở cửa khi khẩn cấp", s.autoUnlock) { v -> vm.edit { it.copy(autoUnlock = v) } }
                SwitchRow("Thông báo đẩy (push)", s.enablePush) { v -> vm.edit { it.copy(enablePush = v) } }
                SwitchRow("Gửi SMS tới người thân", s.enableSms) { v -> vm.edit { it.copy(enableSms = v) } }
            }

            Group("Liên hệ khẩn cấp") {
                OutlinedTextField(
                    value = ui.contactsText, onValueChange = vm::editContacts,
                    label = { Text("Số điện thoại (cách nhau bằng dấu phẩy)") },
                    placeholder = { Text("+84901234567, +84912345678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(onClick = vm::save, enabled = !ui.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (ui.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Lưu cấu hình")
            }
            OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value$unit", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
