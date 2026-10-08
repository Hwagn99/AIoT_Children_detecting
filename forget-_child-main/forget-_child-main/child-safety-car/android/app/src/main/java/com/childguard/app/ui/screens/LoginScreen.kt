package com.childguard.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.childguard.app.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(vm: LoginViewModel, onLoggedIn: () -> Unit) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(ui.loggedIn) { if (ui.loggedIn) onLoggedIn() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ChildGuard", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("Giám sát an toàn trẻ em trong xe", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = ui.serverUrl, onValueChange = vm::onServerUrl, singleLine = true,
            label = { Text("Địa chỉ server (http://IP:8000)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ui.username, onValueChange = vm::onUsername, singleLine = true,
            label = { Text("Tên đăng nhập") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ui.password, onValueChange = vm::onPassword, singleLine = true,
            label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = vm::login, enabled = !ui.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (ui.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Đăng nhập")
        }
    }
}
