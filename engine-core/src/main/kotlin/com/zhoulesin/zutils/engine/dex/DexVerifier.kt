package com.zhoulesin.zutils.engine.dex

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object DexVerifier {

    private var publicKey: PublicKey? = null
    private var publicKeyPem: String? = null

    private val DEFAULT_PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsHqpP4q0dGLbGNw/PZH0
        Q+ydobtGv+DUHC5CrTsSOUh7Op+d5w/0Edn35B50p387tEI47pkmUGoJ8ZszDaKN
        4J4fBTge8E8CBd5uP0a9w7qYBMfla/9hklflMZSpj4/ZBM9cxXh39m/hdRTTJQIx
        ZeSaZrWBLh/UBZy98aLCWGdDJxwSGwtZMyejgsiL7sVykggCxEL9DmPB1hwPQNYZ
        hWSw8+DuPonc399XFkyuLJ41Zx1A0spsZDaRR9HgszheUulckNAeEKb+j01fX6sf
        F0fg35Jn921tcFBueFZDSn6ENVGgybdOAqRFsn1/YMFcN5st6rVI+luS4Tt/6Rul
        cQIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent()

    fun setPublicKeyPem(pem: String) {
        publicKey = null
        publicKeyPem = pem
    }

    private fun loadPublicKey(): PublicKey? {
        publicKey?.let { return it }
        synchronized(this) {
            publicKey?.let { return it }
            val pem = publicKeyPem ?: DEFAULT_PUBLIC_KEY_PEM
            try {
                val base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\\s".toRegex(), "")
                val keyBytes = base64Decode(base64)
                val keySpec = X509EncodedKeySpec(keyBytes)
                val kf = KeyFactory.getInstance("RSA")
                val key = kf.generatePublic(keySpec)
                publicKey = key
                return key
            } catch (e: Exception) {
                return null
            }
        }
    }

    /**
     * Verify DEX integrity and signature.
     * @return null if verification passes, error message string on failure
     */
    fun verify(dexBytes: ByteArray, spec: DexSpec): String? {
        if (spec.checksum.isNotEmpty()) {
            val actual = sha256Hex(dexBytes)
            if (actual != spec.checksum) {
                return "Checksum mismatch: expected ${spec.checksum}, actual $actual"
            }
        }

        if (spec.signature.isNotEmpty()) {
            val key = loadPublicKey()
            if (key == null) {
                return "Public key not available, cannot verify signature"
            }
            try {
                val sig = Signature.getInstance(spec.signatureAlgorithm.ifEmpty { "SHA256withRSA" })
                sig.initVerify(key)
                sig.update(dexBytes)
                val sigBytes = base64Decode(spec.signature)
                if (!sig.verify(sigBytes)) {
                    return "Signature verification failed for ${spec.functionName}"
                }
            } catch (e: Exception) {
                return "Signature verification error: ${e.message}"
            }
        }

        return null
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        val sb = StringBuilder("sha256:")
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xff))
        }
        return sb.toString()
    }

    private fun base64Decode(input: String): ByteArray {
        val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val DECODE = IntArray(128) { -1 }.also {
            for (i in ALPHABET.indices) {
                it[ALPHABET[i].code] = i
            }
        }

        val cleaned = input.replace("\\s".toRegex(), "")
        val buffer = mutableListOf<Byte>()
        var i = 0
        val len = cleaned.length

        while (i < len) {
            val a = DECODE.getOrElse(cleaned[i].code) { -1 }
            val b = DECODE.getOrElse(if (i + 1 < len) cleaned[i + 1].code else -1) { -1 }
            val c = DECODE.getOrElse(if (i + 2 < len) cleaned[i + 2].code else -1) { -1 }
            val d = DECODE.getOrElse(if (i + 3 < len) cleaned[i + 3].code else -1) { -1 }
            i += 4

            if (a < 0 || b < 0) break
            buffer.add(((a shl 2) or (b shr 4)).toByte())
            if (c < 0) break
            buffer.add(((b shl 4) or (c shr 2)).toByte())
            if (d < 0) break
            buffer.add(((c shl 6) or d).toByte())
        }

        return buffer.toByteArray()
    }
}
