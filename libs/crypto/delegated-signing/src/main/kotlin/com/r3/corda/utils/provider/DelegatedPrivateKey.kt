package com.r3.corda.utils.provider

import java.math.BigInteger
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey

internal class DelegatedPrivateKey(
    private val algorithm: String,
    private val format: String,
    val alias: DelegatedSigningService.Alias,
) : PrivateKey, RSAPrivateKey {
    companion object {
        private val KEY_SIZE = BigInteger.valueOf(1L).shiftLeft(527)
        private val EXPONENT = BigInteger.valueOf(1L)
    }

    override fun getFormat() = format
    override fun getAlgorithm() = algorithm
    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }
    override fun getModulus(): BigInteger = KEY_SIZE
    override fun getPrivateExponent(): BigInteger = EXPONENT
}
