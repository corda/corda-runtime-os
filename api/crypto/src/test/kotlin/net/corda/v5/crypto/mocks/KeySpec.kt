package net.corda.v5.crypto.mocks

import java.security.spec.AlgorithmParameterSpec

data class KeySpec(
    val name: String,
    val spec: AlgorithmParameterSpec? = null,
    val keyLength: Int? = null
)