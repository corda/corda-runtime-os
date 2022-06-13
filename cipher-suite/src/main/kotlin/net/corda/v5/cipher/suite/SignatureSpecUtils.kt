@file:JvmName("SignatureSpecUtils")

package net.corda.v5.cipher.suite

import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import java.security.spec.AlgorithmParameterSpec

fun SignatureSpec.getParamsSafely(): AlgorithmParameterSpec? =
    when(this) {
        is CustomSignatureSpec -> params
        is ParameterizedSignatureSpec -> params
        else -> null
    }