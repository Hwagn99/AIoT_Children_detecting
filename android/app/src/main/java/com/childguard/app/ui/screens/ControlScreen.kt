package com.childguard.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.childguard.app.ui.theme.EmergencyRed
import com.childguard.app.ui.viewmodel.ControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(vm: ControlViewModel) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(ui.toast) { ui.toast?.let { snackbar.showSnackbar(it); vm.toastShown() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Điều khiển thiết bị") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier.padding(inner).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Cửa xe (servo)") {
                Button(
                    onClick = { confirmUnlock = true }, enabled = !ui.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Icon(Icons.Filled.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("MỞ CỬA KHẨN CẤP") }
                OutlinedButton(
                    onClick = vm::lockDoor, enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Khoá cửa") }
            }

            SectionCard("Còi báo động") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.siren(true) }, enabled = !ui.busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("Bật còi")
                    }
                    OutlinedButton(onClick = { vm.siren(false) }, enabled = !ui.busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.VolumeOff, null); Spacer(Modifier.width(6.dp)); Text("Tắt còi")
                    }
                }
            }

            SectionCard("Kiểm tra đèn + còi (test)") {
                Text("Kích hoạt thử từng mức để kiểm tra phần cứng. Không gửi thông báo.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { lvl ->
                        OutlinedButton(
                            onClick = { vm.testAlert(lvl) }, enabled = !ui.busy,
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                        ) { Text("Mức $lvl") }
                    }
                }
            }
        }
    }

    if (confirmUnlock) {
        AlertDialog(
            onDismissRequest = { confirmUnlock = false },
            title = { Text("Mở cửa xe?") },
            text = { Text("Servo sẽ mở khoá cửa ngay lập tức. Chỉ thực hiện khi bạn thực sự cần cứu trẻ.") },
            confirmButton = {
                TextButton(onClick = { confirmUnlock = false; vm.unlockDoor() }) { Text("Mở cửa") }
            },
            dismissButton = { TextButton(onClick = { confirmUnlock = false }) { Text("Huỷ") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
