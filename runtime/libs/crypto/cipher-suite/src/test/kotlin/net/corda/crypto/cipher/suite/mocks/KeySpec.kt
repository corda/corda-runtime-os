package net.corda.crypto.cipher.suite.mocks

import java.security.spec.AlgorithmParameterSpec

data class KeySpec(
    val name: String,
    val spec: AlgorithmParameterSpec? = null,
    val keyLength: Int? = null
)