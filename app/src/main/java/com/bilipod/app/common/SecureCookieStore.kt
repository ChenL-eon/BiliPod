package com.bilipod.app.common

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Base64
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密 Cookie 容器，同时充当 OkHttp 的 CookieJar。
 *
 * 安全设计：
 * 1. 内存里保存 Cookie 对象（OkHttp 需要同步读取）；
 * 2. 落盘前用 AndroidKeyStore 中的 AES/GCM 密钥整体加密，密钥不出安全硬件/系统密钥库；
 * 3. SharedPreferences 里只存在密文，禁止明文 SESSDATA 落盘。
 *
 * 风险点：AndroidKeyStore 在少数定制 ROM 上可能异常，这里做了兜底（内存态继续可用，
 * 但不再持久化），不会因此让整个 App 崩溃。
 */
class SecureCookieStore(context: Context) : CookieJar {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 诊断日志只在可调试包里输出；AGP 8 默认不生成 BuildConfig，用 debuggable 标记判断 */
    private val debuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** key = domain|name，value = Cookie；用 LinkedHashMap 保持写入顺序便于排查 */
    private val cookies = LinkedHashMap<String, Cookie>()
    private val lock = Any()

    init {
        try {
            restore()
        } catch (t: Throwable) {
            Log.w(TAG, "Cookie 解密失败，本次会话将不恢复登录态", t)
        }
    }

    // ---------------- CookieJar 实现 ----------------

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            cookies.forEach { this.cookies[keyOf(it)] = it }
            persist()
        }
        logSnapshot("Set-Cookie from ${url.host}")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            // 只有"有明确过期时间且已经过去"的才算过期。
            // 会话 Cookie（没有 Expires 的，OkHttp 里 expiresAt 等于 Long.MIN_VALUE）不能参与这个判断，
            // 否则每一发请求都会把它当过期删掉——网页登录回填的 SESSDATA 就是这么丢的。
            val expired = cookies.values.filter {
                it.expiresAt != Long.MIN_VALUE && it.expiresAt < now
            }
            if (expired.isNotEmpty()) {
                expired.forEach { cookies.remove(keyOf(it)) }
                persist()
            }
            // 这里只做域名/协议匹配，B 站 Cookie 的 path 基本都是 "/"，不做 path 前缀判断
            return cookies.values.filter { match(it, url) && !(skipBuvid3 && it.name == "buvid3") }
        }
    }

    // ---------------- 业务侧读取 ----------------

    /** 取单个 Cookie 值，如 SESSDATA / bili_jct / buvid3 */
    fun value(name: String): String? = synchronized(lock) {
        cookies.values.firstOrNull { it.name == name }?.value
    }

    /** 手动塞 Cookie，用于 buvid 之类的初始化、以及网页登录回填 SESSDATA */
    fun put(name: String, value: String, domain: String = "bilibili.com") {
        // 必须自己给过期时间：OkHttp 里不调 expiresAt 就是"会话 Cookie"（expiresAt = Long.MIN_VALUE），
        // 这种值落盘再读回来会被当成"已过期"，登录态在第一发请求之后就没了。
        // 这里给一年，B 站真实的失效由服务端判定（nav 返回 -101 就是没登录）。
        val expires = System.currentTimeMillis() + COOKIE_TTL_MS
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain.removePrefix("."))
            .path("/")
            .expiresAt(expires)
            .build()
        synchronized(lock) {
            cookies[keyOf(cookie)] = cookie
            persist()
        }
        logSnapshot("put $name")
    }

    fun isLoggedIn(): Boolean = synchronized(lock) {
        val sess = cookies.values.firstOrNull { it.name == "SESSDATA" } ?: return false
        if (sess.value.isBlank()) return false
        // 会话 Cookie 没有过期时间，按"仍然有效"算；有明确过期时间的要真没到点
        sess.expiresAt == Long.MIN_VALUE || sess.expiresAt > System.currentTimeMillis()
    }

    /** 删除指定 Cookie（用于清理失效/自造的设备指纹） */
    fun remove(name: String) = synchronized(lock) {
        val keys = cookies.filterValues { it.name == name }.keys
        if (keys.isNotEmpty()) {
            keys.forEach { cookies.remove(it) }
            persist()
        }
    }

    /**
     * 临时不下发 buvid3（内存里仍然保留，只影响本次请求）。
     *
     * 用途：未登录搜索命中风控包（code=0 但只有 v_voucher、没有 result）时，
     * 实测去掉 buvid3 再查一次通常能拿到正常结果，见 ContentRepository.search。
     */
    @Volatile
    var skipBuvid3: Boolean = false

    /** 退出登录：清内存 + 清密文 */
    fun clear() {
        synchronized(lock) {
            cookies.clear()
            prefs.edit().remove(KEY_CIPHER).apply()
        }
    }

    // ---------------- 内部实现 ----------------

    private fun keyOf(cookie: Cookie): String = "${cookie.domain}|${cookie.name}"

    private fun match(cookie: Cookie, url: HttpUrl): Boolean {
        if (cookie.secure && url.scheme != "https") return false
        val host = url.host
        return if (cookie.hostOnly) {
            host == cookie.domain
        } else {
            host == cookie.domain || host.endsWith(".${cookie.domain}")
        }
    }

    private fun persist() {
        try {
            val plain = cookies.values.joinToString("\n") { c ->
                listOf(
                    c.domain,
                    c.name,
                    c.value,
                    c.path,
                    c.expiresAt.toString(),
                    if (c.hostOnly) "1" else "0"
                ).joinToString("\t")
            }
            val cipherText = encrypt(plain)
            prefs.edit().putString(KEY_CIPHER, cipherText).apply()
        } catch (t: Throwable) {
            // 风险点：加密失败不写入磁盘，保证不会退化成明文存储
            Log.w(TAG, "Cookie 加密持久化失败，仅保留内存态", t)
        }
    }

    private fun restore() {
        val cipherText = prefs.getString(KEY_CIPHER, null) ?: return
        val plain = decrypt(cipherText)
        if (plain.isBlank()) return
        plain.split("\n").forEach { line ->
            val parts = line.split("\t")
            if (parts.size < 6) return@forEach
            val domain = parts[0]
            val name = parts[1]
            val value = parts[2]
            val path = parts[3]
            val expiresAt = parts[4].toLongOrNull() ?: 0L
            val hostOnly = parts[5] == "1"
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .path(path)
                .expiresAt(expiresAt)
            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            val cookie = builder.build()
            cookies[keyOf(cookie)] = cookie
        }
        logSnapshot("restore")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** 返回 base64(iv + 密文) */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val combined = Base64.decode(payload, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    /** 诊断用：把当前 Cookie 名单和剩余有效期打出来。只在 debug 构建里输出 */
    private fun logSnapshot(scene: String) {
        // 不能用 Log.isLoggable：它的默认级别是 INFO，DEBUG 会被直接丢掉
        if (!debuggable) return
        val now = System.currentTimeMillis()
        val desc = cookies.values.joinToString(", ") { c ->
            val ttl = if (c.expiresAt == Long.MIN_VALUE) "session" else "${(c.expiresAt - now) / 86_400_000}天"
            "${c.name}($ttl)"
        }
        Log.d(TAG, "$scene → $desc")
    }

    private companion object {
        const val TAG = "SecureCookieStore"
        const val PREF_NAME = "bili_secure_cookie"
        const val KEY_CIPHER = "cookie_cipher"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bili_cookie_aes_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val COOKIE_TTL_MS = 365L * 24 * 60 * 60 * 1000
    }
}
