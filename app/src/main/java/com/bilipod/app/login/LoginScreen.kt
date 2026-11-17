package com.bilipod.app.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 登录页：同一页里用三个 tab 切换登录方式，从左到右依次是
 * 1. 手机验证码；2. 账号密码；3. 扫码。
 *
 * 为什么不再分成二级页：以前扫码页下面挂两个文字入口，点进去整棵页面被替换，
 * 一旦极验白屏或表单卡住就回不来。现在三种方式共处一页，切 tab 只换中间那块内容，
 * 极验以悬浮卡片叠上来，验证完卡片消失、表单原地还在。
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onSkip: () -> Unit,
    vm: LoginViewModel = viewModel(),
    nativeVm: NativeLoginViewModel = viewModel()
) {
    val qr by vm.state.collectAsStateWithLifecycle()
    val form by nativeVm.state.collectAsStateWithLifecycle()
    var showWebLogin by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    // 0 验证码 / 1 密码 / 2 扫码
    var tab by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current

    /**
     * ViewModel 挂在 Activity 上，登录成功后 [qr.loggedIn] / [form.loggedIn] 会一直是 true。
     * 再次进入本页时不能拿旧的成功标志立刻退回首页，所以要等状态先回到 false 才"上膛"。
     */
    var armed by remember { mutableStateOf(!(qr.loggedIn || form.loggedIn)) }
    LaunchedEffect(qr.loggedIn, form.loggedIn) {
        val nowLoggedIn = qr.loggedIn || form.loggedIn
        if (nowLoggedIn && armed) onLoggedIn()
        if (!nowLoggedIn) armed = true
    }

    // 极验卡片出现时收起键盘，否则卡片下半截被输入法压住
    LaunchedEffect(form.showCaptcha) {
        if (form.showCaptcha) keyboard?.hide()
    }

    // 官方页面兜底：原生链路彻底卡死（极验一直不过）时还能从网页登
    if (showWebLogin) {
        WebLoginPane(
            onBack = { showWebLogin = false },
            onCookies = { vm.acceptWebCookies(it) }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "BiliPod",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "登录后同步你的收藏与收听历史，也能直接播放音频",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(24.dp))
            LoginTabs(selected = tab, onSelect = { tab = it })
            Spacer(Modifier.height(24.dp))

            // 二维码生成有成本，提到这里 remember：切 tab 来回不该每次重建一张图
            val qrBitmap = remember(qr.qrContent) {
                qr.qrContent?.let { QrCodeUtil.createQrBitmap(it) }
            }

            when (tab) {
                0 -> SmsPane(form = form, nativeVm = nativeVm)
                1 -> PasswordPane(
                    form = form,
                    nativeVm = nativeVm,
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible }
                )

                else -> QrPane(qr = qr, qrBitmap = qrBitmap, onRefresh = vm::refreshQr)
            }

            // 提示按当前 tab 归类：表单的报错不该跟到扫码页，扫码的报错也不该压在表单下面
            if (tab <= 1) {
                form.notice?.let { notice ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
                form.error?.let { error ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    )
                }
            } else {
                qr.error?.let { error ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "密码或极验一直不过？改用官方页面登录",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWebLogin = true }
                    .padding(vertical = 6.dp)
            )
            Text(
                text = "先不登录，返回首页",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSkip)
                    .padding(vertical = 6.dp)
            )
            Spacer(Modifier.height(28.dp))
        }

        // 极验悬浮卡片：只盖住中间一块，背后的表单还在，验证完/取消都直接回到表单
        form.captcha?.let { captcha ->
            GeetestOverlay(
                gt = captcha.gt,
                challenge = captcha.challenge,
                onResult = nativeVm::onCaptchaResult,
                onError = nativeVm::onCaptchaError,
                onCancel = nativeVm::cancelCaptcha
            )
        }
    }
}

/** 三种登录方式的切换，选中项下面一条主色短线 */
@Composable
private fun LoginTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        LoginTab("短信验证码", selected == 0) { onSelect(0) }
        LoginTab("账号密码", selected == 1) { onSelect(1) }
        LoginTab("扫码", selected == 2) { onSelect(2) }
    }
}

@Composable
private fun LoginTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(if (selected) 26.dp else 0.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** 验证码登录：手机号 → 获取验证码（先过极验）→ 登录 */
@Composable
private fun SmsPane(form: NativeLoginState, nativeVm: NativeLoginViewModel) {
    LoginField(
        label = "手机号",
        value = form.tel,
        onValueChange = nativeVm::updateTel,
        placeholder = "11 位手机号",
        keyboardType = KeyboardType.Phone
    )
    Spacer(Modifier.height(10.dp))
    LoginField(
        label = "验证码",
        value = form.code,
        onValueChange = nativeVm::updateCode,
        placeholder = "6 位短信验证码",
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        onDone = nativeVm::submitSmsLogin,
        trailing = {
            Text(
                text = when {
                    form.sending -> "发送中…"
                    form.countdown > 0 -> "${form.countdown}s"
                    else -> "获取验证码"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (form.sending || form.countdown > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                },
                modifier = Modifier
                    .clickable(enabled = !form.sending && form.countdown == 0) {
                        nativeVm.requestSmsCode()
                    }
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
            )
        }
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        text = "验证码登录",
        loading = form.loading && form.mode == LoginMode.SMS,
        onClick = nativeVm::submitSmsLogin
    )
}

/** 账号密码登录 */
@Composable
private fun PasswordPane(
    form: NativeLoginState,
    nativeVm: NativeLoginViewModel,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit
) {
    LoginField(
        label = "账号",
        value = form.account,
        onValueChange = nativeVm::updateAccount,
        placeholder = "手机号 / 邮箱",
        keyboardType = KeyboardType.Email
    )
    Spacer(Modifier.height(10.dp))
    LoginField(
        label = "密码",
        value = form.password,
        onValueChange = nativeVm::updatePassword,
        placeholder = "登录密码",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        onDone = nativeVm::submitPasswordLogin,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailing = {
            Text(
                text = if (passwordVisible) "隐藏" else "显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable(onClick = onTogglePassword)
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
            )
        }
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        text = "密码登录",
        loading = form.loading && form.mode == LoginMode.PASSWORD,
        onClick = nativeVm::submitPasswordLogin
    )
}

/** 扫码登录 */
@Composable
private fun QrPane(qr: LoginUiState, qrBitmap: Bitmap?, onRefresh: () -> Unit) {
    Text(
        text = "用哔哩哔哩客户端扫描下方二维码",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
    Spacer(Modifier.height(14.dp))

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                if (qr.loading || qrBitmap == null) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = qr.statusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "刷新二维码",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRefresh)
            .padding(vertical = 6.dp)
    )
}

/**
 * 一行输入框：左侧固定宽度标签 + 输入区 + 可选尾随按钮。
 * 用 BasicTextField 自绘，避免 OutlinedTextField 在深色/浅色切换时线框颜色不统一。
 */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.width(52.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onDone?.invoke() }
            ),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        trailing?.invoke()
    }
}

/** 主色实心按钮，加载时换成转圈 */
@Composable
private fun PrimaryButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = if (loading) 0.6f else 1f)
            )
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
