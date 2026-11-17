package com.bilipod.app.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilipod.app.common.AppResult
import com.bilipod.app.common.Constants
import com.bilipod.app.di.ServiceLocator
import com.bilipod.app.repository.QrPollState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val qrContent: String? = null,
    val statusText: String = "正在获取二维码…",
    val error: String? = null,
    val expired: Boolean = false,
    val loggedIn: Boolean = false,
    val userName: String? = null
)

/**
 * 扫码登录流程：
 * generate 拿二维码 → 每 2 秒 poll 一次 → code=0 时 Cookie 已由 Set-Cookie 落地 → 拉一次 nav 校验。
 *
 * 风险点：轮询有频率限制，必须单线程串行 + 固定间隔；180 秒后停止并提示刷新。
 */
class LoginViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refreshQr()
    }

    fun refreshQr() {
        pollJob?.cancel()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    qrContent = null,
                    error = null,
                    expired = false,
                    statusText = "正在获取二维码…"
                )
            }
            authRepository.ensureBuvid()
            when (val result = authRepository.generateQr()) {
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            loading = false,
                            qrContent = result.data.url,
                            statusText = "请使用哔哩哔哩客户端扫码",
                            error = null
                        )
                    }
                    startPolling(result.data.qrcodeKey)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = "获取二维码失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(loading = false, error = "网络异常：${result.throwable.message}")
                }
            }
        }
    }

    private fun startPolling(qrcodeKey: String) {
        pollJob = viewModelScope.launch {
            val startAt = System.currentTimeMillis()
            while (isActive) {
                delay(Constants.QR_POLL_INTERVAL_MS)
                if (System.currentTimeMillis() - startAt > Constants.QR_POLL_MAX_MS) {
                    _state.update {
                        it.copy(statusText = "二维码已过期，请点击刷新", expired = true)
                    }
                    return@launch
                }
                when (val result = authRepository.pollQr(qrcodeKey)) {
                    is AppResult.Success -> when (result.data) {
                        QrPollState.Waiting -> _state.update { it.copy(statusText = "等待扫码…") }

                        QrPollState.Scanning -> _state.update {
                            it.copy(statusText = "已扫码，请在手机上确认登录")
                        }

                        QrPollState.Expired -> {
                            _state.update {
                                it.copy(statusText = "二维码已过期，请点击刷新", expired = true)
                            }
                            return@launch
                        }

                        QrPollState.Success -> {
                            // 登录成功后立刻校验一次，拿到昵称并确认 Cookie 可用
                            val profile = authRepository.profile()
                            val name = (profile as? AppResult.Success<com.bilipod.app.repository.model.UserProfile?>)?.data?.name
                            _state.update {
                                it.copy(
                                    statusText = "登录成功",
                                    loggedIn = true,
                                    userName = name
                                )
                            }
                            return@launch
                        }
                    }

                    is AppResult.Failure -> _state.update {
                        it.copy(error = "扫码状态异常：${result.message}")
                    }

                    is AppResult.Error -> _state.update {
                        it.copy(error = "网络异常：${result.throwable.message}")
                    }
                }
            }
        }
    }

    /**
     * 账号 / 短信登录的最后一公里：把内嵌登录页拿到的 Cookie 搬进本机 CookieStore，再确认登录态。
     *
     * 注意只认 SESSDATA：网页里只是打开了登录页还没登录时，CookieManager 也会给出一串
     * 访客 Cookie（buvid3、_uuid 之类），没有 SESSDATA 就说明登录没完成，不能当成成功。
     */
    fun acceptWebCookies(cookieHeader: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, statusText = "正在确认登录…") }
            val pairs = cookieHeader.split(';').mapNotNull { segment ->
                val index = segment.indexOf('=')
                if (index <= 0) null else segment.substring(0, index).trim() to segment.substring(index + 1).trim()
            }
            pairs.forEach { (name, value) -> authRepository.putLoginCookie(name, value) }

            if (pairs.none { it.first == "SESSDATA" && it.second.isNotBlank() }) {
                _state.update {
                    it.copy(
                        loading = false,
                        statusText = "等待登录",
                        error = "没读到登录凭据，网页里可能还没登录成功"
                    )
                }
                return@launch
            }

            when (val profile = authRepository.profile()) {
                is AppResult.Success -> {
                    val user = profile.data
                    if (user != null) {
                        _state.update {
                            it.copy(
                                loading = false,
                                statusText = "登录成功",
                                loggedIn = true,
                                userName = user.name
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(loading = false, error = "凭据已写入，但账号校验没通过，可以再试一次")
                        }
                    }
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = "账号校验失败：${profile.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(loading = false, error = "网络异常：${profile.throwable.message}")
                }
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _state.value = LoginUiState()
    }
}
