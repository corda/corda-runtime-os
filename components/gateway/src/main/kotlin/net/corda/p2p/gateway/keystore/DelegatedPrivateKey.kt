package net.corda.p2p.gateway.keystore

import java.math.BigInteger
import java.security.PrivateKey
import java.security.interfaces.RSAKey

/**
 * Implements [PrivateKey] which doesn't hold the encoded value itself and it delegates a signing via an additional method.
 * The method body is set in the constructor and it should be preferably it's an implementation of [net.corda.v5.crypto.sdk.CryptoService].
 * For the signing delegation to be effective the [DelegatedSignatureProvider] provider needs to be registered
 * as the first provider in Java security.
 */
open class DelegatedPrivateKey(private val algorithm: String, private val format: String,
                               private val signOp: (String, ByteArray) -> ByteArray?) : PrivateKey {
    companion object {
        fun create(algorithm: String, format: String, signOp: (String, ByteArray) -> ByteArray?): DelegatedPrivateKey {
            return when (algorithm) {
                "RSA" -> DelegatedRSAPrivateKey(algorithm, format, signOp)
                else -> DelegatedPrivateKey(algorithm, format, signOp)
            }
        }
    }

    /** Corda additional method. */
    fun sign(sigAlgo: String, data: ByteArray): ByteArray? = signOp(sigAlgo, data)

    override fun getAlgorithm() = algorithm

    override fun getFormat() = format

    @Throws(UnsupportedOperationException::class)
    override fun getEncoded(): ByteArray = throw UnsupportedOperationException()
}

class DelegatedRSAPrivateKey(algorithm: String, format: String, signOp: (String, ByteArray) -> ByteArray?) :
    DelegatedPrivateKey(algorithm, format, signOp), RSAKey {

    companion object {
        private val dummyKeySize = BigInteger.valueOf(1L).shiftLeft(8191)
    }

    override fun getModulus(): BigInteger = dummyKeySize
}
