package com.r3.corda.utils.provider

import java.math.BigInteger
import java.security.interfaces.RSAKey

class DelegatedRSAPrivateKey(algorithm: String, format: String, signOp: (String, ByteArray) -> ByteArray?) :
    DelegatedPrivateKey(algorithm, format, signOp), RSAKey {

    companion object {
        private val dummyKeySize = BigInteger.valueOf(1L).shiftLeft(8191)
    }

    override fun getModulus(): BigInteger {
        return dummyKeySize
    }
}
