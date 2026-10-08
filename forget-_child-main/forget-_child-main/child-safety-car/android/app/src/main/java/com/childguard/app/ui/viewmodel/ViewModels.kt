package com.childguard.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.childguard.app.data.model.EventDto
import com.childguard.app.data.model.RuntimeSettingsDto
import com.childguard.app.data.model.StatusDto
import com.childguard.app.data.repo.Repository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private fun Throwable.readable() = message ?: this::class.java.simpleName

// ======================= LOGIN =======================
data class LoginUiState(
    val serverUrl: String = "http://192.168.1.10:8000",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repo.session()
            _ui.update {
                it.copy(
                    serverUrl = s.serverUrl.ifBlank { it.serverUrl },
                    username = s.username,
                    loggedIn = s.isLoggedIn,
                )
            }
        }
    }

    fun onServerUrl(v: String) = _ui.update { it.copy(serverUrl = v, error = null) }
    fun onUsername(v: String) = _ui.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }

    fun login() {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _ui.update { it.copy(error = "Vui lòng nhập đầy đủ thông tin") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                repo.login(s.serverUrl, s.username, s.password)
                registerPushToken()
                _ui.update { it.copy(loading = false, loggedIn = true, password = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "Đăng nhập thất bại: ${e.readable()}") }
            }
        }
    }

    private suspend fun registerPushToken() {
        // Chưa có google-services.json thì bước này bị bỏ qua, app vẫn chạy bình thường
        runCatching { repo.registerFcmToken(FirebaseMessaging.getInstance().token.await()) }
    }
}

// ======================= DASHBOARD =======================
data class DashboardUiState(
    val status: StatusDto = StatusDto(),
    val connected: Boolean = false,
    val snapshot: Bitmap? = null,
    val snapshotLoading: Boolean = false,
    val toast: String? = null,
)

class DashboardViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(DashboardUiState())
    val ui: StateFlow<DashboardUiState> = _ui.asStateFlow()

    init {
        // Vòng lặp WebSocket tự kết nối lại
        viewModelScope.launch {
            while (isActive) {
                try {
                    runCatching { repo.status() }.getOrNull()?.let { s ->
                        _ui.update { it.copy(status = s) }
                    }
                    repo.statusStream().collect { s ->
                        _ui.update { it.copy(status = s, connected = true) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                _ui.update { it.copy(connected = false) }
                delay(3000)
            }
        }
    }

    fun refreshSnapshot() = viewModelScope.launch {
        _ui.update { it.copy(snapshotLoading = true) }
        try {
            val bmp = repo.snapshot()
            _ui.update { it.copy(snapshot = bmp, snapshotLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(snapshotLoading = false, toast = "Không lấy được ảnh: ${e.readable()}") }
        }
    }

    fun silence() = launchAction("Đã tắt còi") { repo.silence() }
    fun toastShown() = _ui.update { it.copy(toast = null) }

    private fun launchAction(okMsg: String, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            _ui.update { it.copy(toast = okMsg) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(toast = "Lỗi: ${e.readable()}") }
        }
    }
}

// ======================= CONTROL =======================
data class ControlUiState(val busy: Boolean = false, val toast: String? = null)

class ControlViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(ControlUiState())
    val ui: StateFlow<ControlUiState> = _ui.asStateFlow()

    fun unlockDoor() = run("Đã gửi lệnh MỞ cửa") { repo.door("unlock") }
    fun lockDoor() = run("Đã gửi lệnh KHOÁ cửa") { repo.door("lock") }
    fun siren(on: Boolean) = run(if (on) "Đã bật còi" else "Đã tắt còi") { repo.siren(on) }
    fun testAlert(level: Int) = run("Đã test mức $level") { repo.testAlert(level) }
    fun toastShown() = _ui.update { it.copy(toast = null) }

    private fun run(okMsg: String, block: suspend () -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        try {
            block()
            _ui.update { it.copy(busy = false, toast = okMsg) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(busy = false, toast = "Lỗi: ${e.readable()}") }
        }
    }
}

// ======================= HISTORY =======================
data class HistoryUiState(
    val events: List<EventDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class HistoryViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(HistoryUiState())
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        try {
            _ui.update { it.copy(events = repo.events(), loading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.readable()) }
        }
    }
}

// ======================= SETTINGS =======================
data class SettingsUiState(
    val settings: RuntimeSettingsDto = RuntimeSettingsDto(),
    val contactsText: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
    val loggedOut: Boolean = false,
)

class SettingsViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _ui.update { it.copy(loading = true) }
        try {
            val s = repo.getSettings()
            _ui.update {
                it.copy(settings = s, contactsText = s.emergencyContacts.joinToString(", "), loading = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, message = "Không tải được cấu hình: ${e.readable()}") }
        }
    }

    fun edit(transform: (RuntimeSettingsDto) -> RuntimeSettingsDto) =
        _ui.update { it.copy(settings = transform(it.settings)) }

    fun editContacts(text: String) = _ui.update { it.copy(contactsText = text) }

    fun save() = viewModelScope.launch {
        val cur = _ui.value
        val contacts = cur.contactsText.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val payload = cur.settings.copy(emergencyContacts = contacts)
        if (!(payload.graceSeconds < payload.warnSeconds && payload.warnSeconds < payload.emergencySeconds)) {
            _ui.update { it.copy(message = "Cần: theo dõi < cảnh báo < khẩn cấp") }
            return@launch
        }
        _ui.update { it.copy(saving = true) }
        try {
            val saved = repo.saveSettings(payload)
            _ui.update { it.copy(settings = saved, saving = false, message = "Đã lưu cấu hình") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(saving = false, message = "Lưu thất bại: ${e.readable()}") }
        }
    }

    fun logout() = viewModelScope.launch {
        repo.logout()
        _ui.update { it.copy(loggedOut = true) }
    }

    fun messageShown() = _ui.update { it.copy(message = null) }
}
