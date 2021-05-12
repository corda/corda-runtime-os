package net.corda.p2p.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.math.min

/**
 * An HMAC-based Extract-and-Expand Key Derivation Function (HKDF), using SHA-256.
 * As specified here: https://datatracker.ietf.org/doc/html/rfc5869.
 *
 *
 */
class HKDF {

    private val sha256HmacAlgoName = "HmacSHA256"
    private val hmac = Mac.getInstance(sha256HmacAlgoName, BouncyCastleProvider())

    fun extract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray {
        val hmacSalt = if (salt.isEmpty()) {
            SecretKeySpec(ByteArray(hmac.macLength), sha256HmacAlgoName)
        } else {
            SecretKeySpec(salt, sha256HmacAlgoName)
        }

        hmac.init(hmacSalt)
        hmac.update(inputKeyMaterial)
        return hmac.doFinal()
    }

    fun expand(pseudorandomKey: ByteArray, info: ByteArray, outputKeyMaterialLengthBytes: Int): ByteArray {
        val iterations = ceil(outputKeyMaterialLengthBytes.toDouble() / hmac.macLength.toDouble()).toInt()
        require(iterations <= 255) { "out length must be maximal 255 * hash-length; requested: $outputKeyMaterialLengthBytes bytes" }

        val T = ByteBuffer.allocate(outputKeyMaterialLengthBytes)
        var remainingBytes = outputKeyMaterialLengthBytes
        var Ti = ByteArray(0)
        for (iteration in 1..iterations) {
            hmac.init(SecretKeySpec(pseudorandomKey, sha256HmacAlgoName))
            hmac.update(Ti)
            hmac.update(info)
            hmac.update(iteration.toByte())
            Ti = hmac.doFinal()

            T.put(Ti, 0, min(Ti.size, remainingBytes))
            remainingBytes -= Ti.size
        }

        return T.array()
    }
}