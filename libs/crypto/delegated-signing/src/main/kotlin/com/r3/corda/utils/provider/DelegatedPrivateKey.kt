package com.r3.corda.utils.provider

import java.math.BigInteger
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey

open class DelegatedPrivateKey(
    private val algorithm: String,
    private val format: String,
    private val signOp: (String, ByteArray) -> ByteArray?,
) : PrivateKey, RSAPrivateKey {
    companion object {
        private val KEY_SIZE = BigInteger.valueOf(1L).shiftLeft(527)
        private val EXPONENT = BigInteger.valueOf(1L)
    }

    override fun getFormat() = format
    fun sign(sigAlgo: String, data: ByteArray): ByteArray? = signOp(sigAlgo, data)
    override fun getAlgorithm() = algorithm
    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }
    override fun getModulus(): BigInteger = KEY_SIZE
    override fun getPrivateExponent(): BigInteger = EXPONENT
}
