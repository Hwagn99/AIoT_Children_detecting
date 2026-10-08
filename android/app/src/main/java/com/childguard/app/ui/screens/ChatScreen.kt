package com.childguard.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.childguard.app.data.model.ChatMessage
import com.childguard.app.ui.viewmodel.ChatViewModel
import com.childguard.app.ui.viewmodel.chatSuggestions

/** Hỏi đáp với AI (Crew 3) — POST /chat. Agent chỉ đọc dữ liệu, không điều khiển thiết bị. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val ui by vm.ui.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(ui.messages.size) {
        if (ui.messages.isNotEmpty()) listState.animateScrollToItem(ui.messages.lastIndex)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Hỏi trợ lý an toàn") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ui.input,
                        onValueChange = vm::onInput,
                        placeholder = { Text("Hỏi về tình trạng trong xe…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { vm.ask() },
                        enabled = ui.canSend,
                        modifier = Modifier.size(52.dp),
                    ) {
                        if (ui.sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, "Gửi")
                    }
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (ui.messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Hỏi bất cứ điều gì về tình trạng trong xe",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    chatSuggestions.forEach { q ->
                        SuggestionChip(
                            onClick = { vm.ask(q) },
                            label = { Text(q) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.messages) { Bubble(it, pending = it.answer.isEmpty() && ui.sending) }
                }
            }
            ui.error?.let {
                Text("Lỗi: $it", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, pending: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Text(m.question, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        if (pending) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Đang phân tích…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.answer, style = MaterialTheme.typography.bodyMedium)
                    if (m.tools.isNotEmpty()) {
                        Text("Nguồn: ${m.tools.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}