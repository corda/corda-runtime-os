package com.r3.corda.utils.provider

import java.security.PrivateKey

open class DelegatedPrivateKey(
    private val algorithm: String,
    private val format: String,
    private val signOp: (String, ByteArray) -> ByteArray?,
) : PrivateKey {
    companion object {
        fun create(algorithm: String, format: String, signOp: (String, ByteArray) -> ByteArray?): DelegatedPrivateKey {
            return when (algorithm) {
                "RSA" -> DelegatedRSAPrivateKey(algorithm, format, signOp)
                else -> DelegatedPrivateKey(algorithm, format, signOp)
            }
        }
    }

    override fun getFormat() = format
    fun sign(sigAlgo: String, data: ByteArray): ByteArray? = signOp(sigAlgo, data)
    override fun getAlgorithm() = algorithm
    override fun getEncoded(): ByteArray {
        throw UnsupportedOperationException()
    }
}
