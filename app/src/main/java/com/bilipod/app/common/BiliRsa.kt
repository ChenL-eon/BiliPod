package com.bilipod.app.common

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * B 站网页登录的密码加密。
 *
 * 规则（详见 API-collect 的 password 登录页）：
 * 明文 = 服务端下发的 hash + 用户密码，再用 PKCS#1 v1.5 填充的 RSA 加密，最后 base64。
 * 公钥来自 `x/passport-login/web/key`，实测 2026-11 返回的是 PKCS#8 的
 * `-----BEGIN PUBLIC KEY-----` 格式、1024 位，所以直接按 X.509 编码解析即可。
 *
 * 风险点：hash 只有几十秒有效期，必须先取公钥再立刻提交，不能缓存到下一次登录。
 */
object BiliRsa {

    fun encryptPassword(hash: String, password: String, pem: String): String {
        val publicKey = parsePublicKey(pem)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal((hash + password).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun parsePublicKey(pem: String): PublicKey {
        val body = pem
            .replace("\r", "")
            .replace("\n", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .trim()
        val der = Base64.decode(body, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }
}
