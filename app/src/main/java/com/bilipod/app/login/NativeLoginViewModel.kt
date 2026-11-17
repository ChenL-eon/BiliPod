package com.bilipod.app.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilipod.app.common.AppResult
import com.bilipod.app.di.ServiceLocator
import com.bilipod.app.repository.CaptchaChallenge
import com.bilipod.app.repository.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 登录方式 */
enum class LoginMode { SMS, PASSWORD }

/** 拿到极验结果之后要接着做的事 */
enum class CaptchaPurpose { SEND_SMS, PASSWORD_LOGIN }

data class NativeLoginState(
    val mode: LoginMode = LoginMode.SMS,
    val tel: String = "",
    val code: String = "",
    val account: String = "",
    val password: String = "",
    val sending: Boolean = false,
    val loading: Boolean = false,
    val countdown: Int = 0,
    val smsCaptchaKey: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val captcha: CaptchaChallenge? = null,
    val captchaPurpose: CaptchaPurpose = CaptchaPurpose.SEND_SMS,
    val loggedIn: Boolean = false,
    val userName: String? = null
) {
    /** 极验面板是否要显示 */
    val showCaptcha: Boolean get() = captcha != null
}

/**
 * 原生表单登录的 ViewModel。
 *
 * 密码和短信两条路的公共开头是极验：点按钮 → 现取 captcha 参数 → 弹 WebView 过验证
 * → 拿到 validate / seccode → 再走各自接口。所以状态里用 [captchaPurpose] 记住
 * "这次验证是为了发短信还是为了提交密码"，验证回来时分派。
 *
 * 风险点：
 * 1. challenge 有时效，必须在点击时才取，不能缓存；
 * 2. 短信的 captcha_key 也有限期，过期要重新走一遍验证；
 * 3. 验证失败（-105）说明极验结果没被认，要清掉本次结果重新验证。
 */
class NativeLoginViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository

    private val _state = MutableStateFlow(NativeLoginState())
    val state: StateFlow<NativeLoginState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun updateTel(value: String) {
        // 手机号只收数字，长度收敛到 11 位，避免用户粘进 +86 之类
        val digits = value.filter { it.isDigit() }.take(11)
        _state.update { it.copy(tel = digits, error = null) }
    }

    fun updateCode(value: String) {
        _state.update { it.copy(code = value.filter { it.isDigit() }.take(6), error = null) }
    }

    fun updateAccount(value: String) {
        _state.update { it.copy(account = value.trim(), error = null) }
    }

    fun updatePassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    /** 点"获取验证码"：先过极验，过了才发短信 */
    fun requestSmsCode() {
        val current = _state.value
        if (current.sending || current.countdown > 0) return
        if (current.tel.length < 11) {
            _state.update { it.copy(error = "先填 11 位手机号") }
            return
        }
        prepareCaptcha(CaptchaPurpose.SEND_SMS)
    }

    /** 点"登录"（密码模式） */
    fun submitPasswordLogin() {
        val current = _state.value
        if (current.loading) return
        when {
            current.account.isBlank() -> _state.update { it.copy(error = "账号不能为空") }
            current.password.isBlank() -> _state.update { it.copy(error = "密码不能为空") }
            else -> {
                // 同页现在有两个登录按钮，mode 用来区分"谁在转圈"
                _state.update { it.copy(mode = LoginMode.PASSWORD) }
                prepareCaptcha(CaptchaPurpose.PASSWORD_LOGIN)
            }
        }
    }

    /** 点"登录"（短信模式）。短信登录不需要再验证，captcha_key 在发短信时已经拿到 */
    fun submitSmsLogin() {
        val current = _state.value
        if (current.loading) return
        when {
            current.tel.length < 11 -> _state.update { it.copy(error = "先填 11 位手机号") }
            current.code.length < 4 -> _state.update { it.copy(error = "验证码还没输完") }
            current.smsCaptchaKey.isNullOrBlank() ->
                _state.update { it.copy(error = "先点获取验证码，通过人机验证后再登录") }
            else -> {
                _state.update { it.copy(mode = LoginMode.SMS) }
                doSmsLogin()
            }
        }
    }

    fun cancelCaptcha() {
        Log.d("NativeLogin", "cancelCaptcha -> captcha=null")
        _state.update { it.copy(captcha = null) }
    }

    /** 极验结果回来 */
    fun onCaptchaResult(challenge: String, validate: String, seccode: String) {
        val captcha = _state.value.captcha
        val purpose = _state.value.captchaPurpose
        Log.d("NativeLogin", "onCaptchaResult purpose=$purpose hasCaptcha=${captcha != null}")
        _state.update { it.copy(captcha = null) }
        if (captcha == null) {
            Log.d("NativeLogin", "onCaptchaResult dropped: 面板已关闭")
            return
        }
        when (purpose) {
            CaptchaPurpose.SEND_SMS -> doSendSms(captcha, challenge, validate, seccode)
            CaptchaPurpose.PASSWORD_LOGIN -> doPasswordLogin(captcha, challenge, validate, seccode)
        }
    }

    fun onCaptchaError(message: String) {
        _state.update { it.copy(captcha = null, error = "人机验证没通过：$message") }
    }

    // ---------------- 内部流程 ----------------

    private fun prepareCaptcha(purpose: CaptchaPurpose) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            when (val result = authRepository.requestCaptcha()) {
                is AppResult.Success -> _state.update {
                    it.copy(loading = false, captcha = result.data, captchaPurpose = purpose)
                }

                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = "取验证参数失败：${result.message}")
                }

                is AppResult.Error -> _state.update {
                    it.copy(loading = false, error = "网络异常：${result.throwable.message}")
                }
            }
        }
    }

    private fun doSendSms(
        captcha: CaptchaChallenge,
        challenge: String,
        validate: String,
        seccode: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, notice = null) }
            when (
                val result = authRepository.sendSmsCode(
                    tel = _state.value.tel,
                    captcha = captcha,
                    validate = validate,
                    seccode = seccode,
                    challenge = challenge
                )
            ) {
                is AppResult.Success -> {
                    Log.d("NativeLogin", "sendSms ok, captcha_key=${result.data.take(8)}")
                    _state.update {
                        it.copy(
                            sending = false,
                            smsCaptchaKey = result.data,
                            notice = "验证码已发送，注意查收"
                        )
                    }
                    startCountdown()
                }

                is AppResult.Failure -> {
                    Log.d("NativeLogin", "sendSms fail: ${result.message}")
                    _state.update { it.copy(sending = false, error = result.message) }
                }

                is AppResult.Error -> {
                    Log.d("NativeLogin", "sendSms error: ${result.throwable.message}")
                    _state.update {
                        it.copy(sending = false, error = "网络异常：${result.throwable.message}")
                    }
                }
            }
        }
    }

    private fun doPasswordLogin(
        captcha: CaptchaChallenge,
        challenge: String,
        validate: String,
        seccode: String
    ) {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(loading = true, error = null) }
            when (
                val result = authRepository.passwordLogin(
                    username = current.account,
                    password = current.password,
                    captcha = captcha,
                    validate = validate,
                    seccode = seccode,
                    challenge = challenge
                )
            ) {
                is AppResult.Success -> confirmLogin()
                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }

                is AppResult.Error -> _state.update {
                    it.copy(loading = false, error = "网络异常：${result.throwable.message}")
                }
            }
        }
    }

    private fun doSmsLogin() {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(loading = true, error = null, notice = null) }
            when (
                val result = authRepository.smsLogin(
                    tel = current.tel,
                    code = current.code,
                    captchaKey = current.smsCaptchaKey.orEmpty()
                )
            ) {
                is AppResult.Success -> confirmLogin()
                is AppResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message)
                }

                is AppResult.Error -> _state.update {
                    it.copy(loading = false, error = "网络异常：${result.throwable.message}")
                }
            }
        }
    }

    /**
     * 接口说成功了不代表真的能用：Cookie 是否被 CookieJar 接住、账号是否被 nav 认，
     * 都要再问一次 nav。nav 也认了才算登录完成。
     */
    private suspend fun confirmLogin() {
        when (val profile = authRepository.profile()) {
            is AppResult.Success -> {
                val user: UserProfile? = profile.data
                if (user != null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            loggedIn = true,
                            userName = user.name,
                            notice = "登录成功"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(loading = false, error = "接口已通过，但账号校验没认，请重试一次")
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

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (second in 60 downTo 1) {
                _state.update { it.copy(countdown = second) }
                delay(1000)
            }
            _state.update { it.copy(countdown = 0) }
        }
    }
}
