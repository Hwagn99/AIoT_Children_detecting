package com.childguard.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.childguard.app.ui.navigation.AppNav
import com.childguard.app.ui.theme.ChildGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChildGuardTheme {
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* người dùng từ chối thì vẫn xem được trạng thái trong app */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Điều hướng chính của ứng dụng
                AppNav()
            }
        }
    }
}
