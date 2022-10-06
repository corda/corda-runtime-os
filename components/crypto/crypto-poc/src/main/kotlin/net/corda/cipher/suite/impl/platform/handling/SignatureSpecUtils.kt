package net.corda.cipher.suite.impl.platform.handling

import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import java.security.spec.AlgorithmParameterSpec


fun SignatureSpec.getParamsSafely(): AlgorithmParameterSpec? =
    when(this) {
        is ParameterizedSignatureSpec -> params
        else -> null
    }
