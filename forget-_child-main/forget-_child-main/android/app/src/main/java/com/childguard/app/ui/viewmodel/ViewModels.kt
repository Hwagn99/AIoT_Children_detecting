package com.childguard.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.childguard.app.data.model.EventDto
import com.childguard.app.data.model.Labels
import com.childguard.app.data.model.RuntimeSettingsDto
import com.childguard.app.data.repo.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== 1. LOGIN ====================
data class LoginUiState(
    val serverUrl: String = "http://192.168.1.100:8000",
    val username: String = "admin",
    val password: String = "admin123",
    val error: String? = null,
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val repository: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val session = repository.session()
                if (session.serverUrl.isNotBlank()) {
                    _ui.update { it.copy(serverUrl = session.serverUrl, username = session.username) }
                }
            }
        }
    }

    fun onServerUrl(url: String) { _ui.update { it.copy(serverUrl = url) } }
    fun onUsername(u: String) { _ui.update { it.copy(username = u) } }
    fun onPassword(p: String) { _ui.update { it.copy(password = p) } }

    fun login() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val s = _ui.value
                repository.login(s.serverUrl, s.username, s.password)
            }.onSuccess {
                _ui.update { it.copy(loading = false, loggedIn = true) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.localizedMessage ?: "Đăng nhập thất bại") }
            }
        }
    }

    fun loginDemo() {
        _ui.update { it.copy(loading = false, loggedIn = true) }
    }
}

// ==================== 2. DASHBOARD ====================
data class DashboardState(
    val isOnline: Boolean = true,
    val isDanger: Boolean = false,
    val aiEmotion: String = "Bình tĩnh / An toàn",
    val temperature: Float = 25.0f,
    val gasStatus: String = "Bình thường",
    val logs: List<String> = emptyList(),
)

class DashboardViewModel(private val repository: Repository) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        addLog("Hệ thống SafeCam-DCN khởi động.")
        addLog("Đang kết nối Camera AI và cảm biến...")

        viewModelScope.launch {
            runCatching {
                repository.statusStream().catch { e ->
                    addLog("Lỗi kết nối stream: ${e.localizedMessage}")
                }.collect { status ->
                    val isDanger = status.level >= 2 || (status.tempC ?: 0.0) > 35.0 || status.emotion == "CRYING"
                    val emotionStr = Labels.emotion(status.emotion)
                    val temp = (status.tempC ?: 25.0).toFloat()
                    val gas = if (status.level >= 2) "Nguy hiểm (Mức CO2 cao)" else "Bình thường"
                    val isOnline = status.hardwareOnline || status.cameraOnline

                    _uiState.update { state ->
                        state.copy(
                            isOnline = isOnline,
                            isDanger = isDanger,
                            aiEmotion = emotionStr,
                            temperature = temp,
                            gasStatus = gas
                        )
                    }

                    if (isDanger) {
                        addLog("[CẢNH BÁO] Phát hiện nguy hiểm: Nhiệt độ ${temp}°C, Cảm xúc $emotionStr!")
                    }
                }
            }.onFailure {
                addLog("Chế độ dùng thử local (không kết nối server).")
            }
        }
    }

    fun sendHardwareCommand(device: String, action: String) {
        viewModelScope.launch {
            addLog("[Gửi lệnh] Yêu cầu $action cho $device...")
            runCatching {
                when {
                    device.contains("còi", ignoreCase = true) || device.contains("horn", ignoreCase = true) -> {
                        repository.siren(action.contains("bật", ignoreCase = true) || action.contains("on", ignoreCase = true))
                    }
                    device.contains("cửa", ignoreCase = true) || device.contains("servo", ignoreCase = true) || device.contains("window", ignoreCase = true) -> {
                        repository.door(if (action.contains("mở", ignoreCase = true) || action.contains("open", ignoreCase = true)) "unlock" else "lock")
                    }
                    else -> {
                        repository.siren(true)
                    }
                }
            }.onSuccess {
                addLog("[Thành công] $device đã phản hồi lệnh $action.")
            }.onFailure { e ->
                addLog("[Lỗi] Không thể gửi lệnh tới $device: ${e.localizedMessage}")
            }
        }
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logWithTime = "$time - $message"
        _uiState.update { state ->
            val newLogs = listOf(logWithTime) + state.logs
            state.copy(logs = newLogs)
        }
    }
}

// ==================== 3. CONTROL ====================
data class ControlUiState(
    val busy: Boolean = false,
    val toast: String? = null,
)

class ControlViewModel(private val repository: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(ControlUiState())
    val ui: StateFlow<ControlUiState> = _ui.asStateFlow()

    fun toastShown() { _ui.update { it.copy(toast = null) } }

    fun lockDoor() {
        executeCommand("Đã khoá cửa xe") { repository.door("lock") }
    }

    fun unlockDoor() {
        executeCommand("Đã mở cửa xe khẩn cấp") { repository.door("unlock") }
    }

    fun siren(on: Boolean) {
        val msg = if (on) "Đã bật còi báo động" else "Đã tắt còi báo động"
        executeCommand(msg) { repository.siren(on) }
    }

    fun testAlert(level: Int) {
        executeCommand("Đã kích hoạt thử nghiệm mức $level") { repository.testAlert(level) }
    }

    private fun executeCommand(successToast: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            runCatching { action() }
                .onSuccess { _ui.update { it.copy(busy = false, toast = successToast) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, toast = "Lỗi: ${e.localizedMessage}") } }
        }
    }
}

// ==================== 4. HISTORY ====================
data class HistoryUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val events: List<EventDto> = emptyList(),
)

class HistoryViewModel(private val repository: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(HistoryUiState())
    val ui: StateFlow<HistoryUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                repository.events()
            }.onSuccess { list ->
                _ui.update { it.copy(loading = false, events = list) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.localizedMessage ?: "Không thể tải lịch sử") }
            }
        }
    }
}

// ==================== 5. SETTINGS ====================
data class SettingsUiState(
    val settings: RuntimeSettingsDto = RuntimeSettingsDto(),
    val contactsText: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val loggedOut: Boolean = false,
)

class SettingsViewModel(private val repository: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            runCatching {
                repository.getSettings()
            }.onSuccess { s ->
                _ui.update {
                    it.copy(
                        loading = false,
                        settings = s,
                        contactsText = s.emergencyContacts.joinToString(", ")
                    )
                }
            }.onFailure {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    fun messageShown() { _ui.update { it.copy(message = null) } }

    fun editContacts(text: String) { _ui.update { it.copy(contactsText = text) } }

    fun edit(transform: (RuntimeSettingsDto) -> RuntimeSettingsDto) {
        _ui.update { it.copy(settings = transform(it.settings)) }
    }

    fun save() {
        viewModelScope.launch {
            _ui.update { it.copy(saving = true) }
            runCatching {
                val contacts = _ui.value.contactsText
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val updated = _ui.value.settings.copy(emergencyContacts = contacts)
                repository.saveSettings(updated)
            }.onSuccess { saved ->
                _ui.update { it.copy(saving = false, settings = saved, message = "Đã lưu cấu hình!") }
            }.onFailure { e ->
                _ui.update { it.copy(saving = false, message = "Lỗi lưu cấu hình: ${e.localizedMessage}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { repository.logout() }
            _ui.update { it.copy(loggedOut = true) }
        }
    }
}
