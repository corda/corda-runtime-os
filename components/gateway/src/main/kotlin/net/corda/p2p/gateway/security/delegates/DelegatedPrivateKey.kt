package net.corda.p2p.gateway.security.delegates

import java.math.BigInteger
import java.security.interfaces.RSAPrivateKey

internal class DelegatedPrivateKey(
    private val format: String,
    private val algorithm: String,
    val aliasService: SigningService.Alias
) : RSAPrivateKey {
    companion object {
        private val KEY_SIZE = BigInteger.valueOf(1L).shiftLeft(527)
        private val EXPONENT = BigInteger.valueOf(1L)
    }

    override fun getAlgorithm(): String {
        return algorithm
    }

    override fun getFormat(): String {
        return format
    }

    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }

    override fun getModulus(): BigInteger {
        return KEY_SIZE
    }

    override fun getPrivateExponent(): BigInteger {
        return EXPONENT
    }
}
