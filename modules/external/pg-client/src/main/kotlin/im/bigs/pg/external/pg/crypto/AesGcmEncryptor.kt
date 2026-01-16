package im.bigs.pg.external.pg.crypto

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 암호화 유틸리티.
 * TestPG API 연동을 위한 암호화 처리를 담당합니다.
 *
 * - 알고리즘: AES/GCM/NoPadding
 * - 키: API-KEY를 SHA-256 해싱하여 32바이트 키 생성
 * - IV: 12바이트 (96비트)
 * - 태그 길이: 128비트 (16바이트)
 * - 인코딩: Base64URL (패딩 없음)
 */
class AesGcmEncryptor(
    apiKey: String,
    ivBase64Url: String,
) {
    private val secretKey: SecretKeySpec
    private val iv: ByteArray

    init {
        // SHA-256으로 API-KEY를 해싱하여 32바이트 키 생성
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
        secretKey = SecretKeySpec(keyBytes, "AES")

        // Base64URL 디코딩으로 IV 생성 (12바이트)
        iv = Base64.getUrlDecoder().decode(ivBase64Url)
        require(iv.size == IV_LENGTH_BYTES) {
            "IV must be $IV_LENGTH_BYTES bytes, but was ${iv.size} bytes"
        }
    }

    /**
     * 평문을 AES-256-GCM으로 암호화하고 Base64URL로 인코딩합니다.
     *
     * @param plaintext 암호화할 평문 (UTF-8)
     * @return Base64URL 인코딩된 암호문 (ciphertext||tag, 패딩 없음)
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertextWithTag = cipher.doFinal(plaintextBytes)

        return Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertextWithTag)
    }

    /**
     * Base64URL 인코딩된 암호문을 복호화합니다.
     *
     * @param encryptedBase64Url Base64URL 인코딩된 암호문 (ciphertext||tag)
     * @return 복호화된 평문 (UTF-8)
     */
    fun decrypt(encryptedBase64Url: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val ciphertextWithTag = Base64.getUrlDecoder().decode(encryptedBase64Url)
        val plaintextBytes = cipher.doFinal(ciphertextWithTag)

        return String(plaintextBytes, Charsets.UTF_8)
    }

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }
}
