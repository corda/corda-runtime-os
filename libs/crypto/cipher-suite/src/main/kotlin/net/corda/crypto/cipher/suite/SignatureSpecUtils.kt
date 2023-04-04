@file:JvmName("SignatureSpecUtils")

package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.SignatureSpec
import java.security.spec.AlgorithmParameterSpec

/**
 * @return the contained [AlgorithmParameterSpec] of [CustomSignatureSpec.params] or [ParameterizedSignatureSpec.params]
 * or null otherwise.
 */
fun SignatureSpec.getParamsSafely(): AlgorithmParameterSpec? =
    when(this) {
        is CustomSignatureSpec -> params
        is ParameterizedSignatureSpec -> params
        else -> null
    }