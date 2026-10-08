package com.childguard.app.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.childguard.app.data.repo.Repository

fun appViewModelFactory(repo: Repository): ViewModelProvider.Factory = viewModelFactory {
    initializer { LoginViewModel(repo) }
    initializer { DashboardViewModel(repo) }
    initializer { ControlViewModel(repo) }
    initializer { HistoryViewModel(repo) }
    initializer { ReportsViewModel(repo) }
    initializer { ChatViewModel(repo) }
    initializer { SettingsViewModel(repo) }
}
