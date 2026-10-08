package com.childguard.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.childguard.app.data.model.ChatMessage
import com.childguard.app.data.model.EventDto
import com.childguard.app.data.model.ReportDto
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
    // --- camera trực tiếp (GET /camera/frame) ---
    val liveFrame: Bitmap? = null,
    val liveOn: Boolean = false,
    val liveLoading: Boolean = false,
    // --- câu hỏi xác nhận của Verifier ---
    val confirmPending: Boolean = false,
    val confirmSending: Boolean = false,
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
                        _ui.update { it.copy(status = s, connected = true, confirmPending = s.needsParentConfirm) }
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

    // ---------- camera trực tiếp ----------
    fun startLive() {
        if (_ui.value.liveOn) return
        _ui.update { it.copy(liveOn = true, liveLoading = true) }
        viewModelScope.launch {
            while (_ui.value.liveOn && isActive) {
                val frame = runCatching { repo.cameraFrame() }.getOrNull()
                _ui.update { it.copy(liveFrame = frame ?: it.liveFrame, liveLoading = false) }
                if (frame == null) delay(1500) else delay(300)   // ~3 khung hình/giây
            }
            _ui.update { it.copy(liveOn = false, liveLoading = false) }
        }
    }

    fun stopLive() = _ui.update { it.copy(liveOn = false) }

    // ---------- xác nhận phụ huynh ----------
    fun confirmRealChild(isRealChild: Boolean) = viewModelScope.launch {
        _ui.update { it.copy(confirmSending = true) }
        try {
            repo.parentConfirm(isRealChild)
            _ui.update {
                it.copy(
                    confirmSending = false,
                    confirmPending = false,
                    toast = if (isRealChild) "Đã xác nhận: có trẻ thật" else "Đã xác nhận: không phải trẻ",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(confirmSending = false, toast = "Lỗi: ${e.readable()}") }
        }
    }

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

// ======================= BÁO CÁO SỰ CỐ =======================
data class ReportsUiState(
    val reports: List<ReportDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val expanded: Int? = null,
)

class ReportsViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(ReportsUiState())
    val ui: StateFlow<ReportsUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        try {
            _ui.update { it.copy(reports = repo.reports(), loading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = e.readable()) }
        }
    }

    fun toggle(id: Int) = _ui.update { it.copy(expanded = if (it.expanded == id) null else id) }
}

// ======================= HỎI ĐÁP AI =======================
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
) {
    val canSend get() = !sending && input.isNotBlank()
}

/** Câu hỏi gợi ý cho màn hình hỏi đáp. */
val chatSuggestions = listOf(
    "Hôm nay có sự cố nào không?",
    "Trẻ ở trong xe bao lâu rồi?",
    "Trong xe có bao nhiêu người?",
    "Tóm tắt báo cáo gần nhất.",
)

class ChatViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    fun onInput(v: String) = _ui.update { it.copy(input = v) }

    fun ask(question: String = _ui.value.input) {
        val q = question.trim()
        if (q.isEmpty() || _ui.value.sending) return
        _ui.update { it.copy(messages = it.messages + ChatMessage(q, ""), input = "", sending = true, error = null) }
        viewModelScope.launch {
            try {
                val res = repo.ask(q)
                _ui.update { st ->
                    st.copy(
                        messages = st.messages.dropLast(1) + ChatMessage(q, res.answer, res.usedTools),
                        sending = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { st ->
                    st.copy(
                        messages = st.messages.dropLast(1) +
                            ChatMessage(q, "Xin lỗi, không trả lời được câu hỏi này."),
                        sending = false,
                        error = e.readable(),
                    )
                }
            }
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
